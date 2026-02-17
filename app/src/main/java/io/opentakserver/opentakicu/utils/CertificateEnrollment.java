package io.opentakserver.opentakicu.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.provider.Settings;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class CertificateEnrollment {
    private static final String TAG = Constants.TAG_PREFIX + "CertEnroll";

    private final Context context;
    private final String hostname;
    private final String username;
    private final String password;

    public CertificateEnrollment(Context context, String hostname, String username, String password) {
        this.context = context;
        this.hostname = hostname;
        this.username = username;
        this.password = password;
    }

    public boolean enroll(String clientPemPath, String serverPemPath) {
        try {
            setupTrustAllCerts();

            String baseUrl = "https://" + hostname + ":8446";
            String credentials = username + ":" + password;
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    credentials.getBytes(StandardCharsets.UTF_8));

            // Step 1: Get TLS config
            String configUrl = baseUrl + "/Marti/api/tls/config";
            Log.i(TAG, "Fetching TLS config from: " + configUrl);
            Log.d(TAG, "Enrolling with username: " + username);

            String configXml = httpGet(configUrl, authHeader);
            if (configXml == null) {
                return false;
            }
            Log.d(TAG, "TLS config XML: " + configXml);

            // Parse XML to extract O and OU
            String orgName = "";
            String orgUnit = "";
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(configXml.getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            NodeList nameEntries = doc.getElementsByTagNameNS("http://bbn.com/marti/xml/config", "nameEntry");
            if (nameEntries.getLength() == 0) {
                nameEntries = doc.getElementsByTagName("nameEntry");
            }

            for (int i = 0; i < nameEntries.getLength(); i++) {
                Element entry = (Element) nameEntries.item(i);
                String name = entry.getAttribute("name");
                String value = entry.getAttribute("value");
                if ("O".equals(name)) {
                    orgName = value;
                    Log.d(TAG, "Organization: " + orgName);
                } else if ("OU".equals(name)) {
                    orgUnit = value;
                    Log.d(TAG, "Organizational Unit: " + orgUnit);
                }
            }

            // Generate RSA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Build CSR subject using X500NameBuilder (handles commas in values)
            X500NameBuilder nameBuilder = new X500NameBuilder(BCStyle.INSTANCE);
            nameBuilder.addRDN(BCStyle.CN, username);
            if (!orgName.isEmpty()) {
                nameBuilder.addRDN(BCStyle.O, orgName);
            }
            if (!orgUnit.isEmpty()) {
                nameBuilder.addRDN(BCStyle.OU, orgUnit);
            }
            X500Name subject = nameBuilder.build();

            // Create CSR
            PKCS10CertificationRequestBuilder csrBuilder =
                    new PKCS10CertificationRequestBuilder(subject,
                            SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            PKCS10CertificationRequest csr = csrBuilder.build(signer);

            // Convert CSR to PEM string
            StringWriter csrWriter = new StringWriter();
            try (JcaPEMWriter pemWriter = new JcaPEMWriter(csrWriter)) {
                pemWriter.writeObject(csr);
            }
            String csrPem = csrWriter.toString();
            Log.d(TAG, "Generated CSR");

            // Step 2: Sign the CSR
            String clientUid = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String version;
            try {
                PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                version = packageInfo.versionName;
            } catch (Exception e) {
                version = "0.0.0";
            }

            String signUrl = baseUrl + "/Marti/api/tls/signClient/v2"
                    + "?clientUid=" + clientUid + "&version=" + version;
            Log.i(TAG, "Requesting certificate signing from: " + signUrl);

            String signResponseBody = httpPost(signUrl, authHeader, csrPem);
            if (signResponseBody == null) {
                return false;
            }

            JSONObject jsonResponse = new JSONObject(signResponseBody);
            String signedCert = jsonResponse.optString("signedCert", "");
            String ca0 = jsonResponse.optString("ca0", "");
            String ca1 = jsonResponse.optString("ca1", "");

            if (signedCert.isEmpty() || ca0.isEmpty()) {
                Log.e(TAG, "Missing certificate data in response");
                return false;
            }

            // Write client.pem (signed cert + private key)
            File clientPemDir = new File(clientPemPath).getParentFile();
            if (clientPemDir != null && !clientPemDir.exists()) {
                clientPemDir.mkdirs();
            }

            try (FileWriter clientWriter = new FileWriter(clientPemPath)) {
                clientWriter.write(wrapCert(signedCert));
                StringWriter keyWriter = new StringWriter();
                try (JcaPEMWriter pemWriter = new JcaPEMWriter(keyWriter)) {
                    pemWriter.writeObject(keyPair.getPrivate());
                }
                clientWriter.write(keyWriter.toString());
            }
            Log.i(TAG, "Wrote client certificate to: " + clientPemPath);

            // Write server.pem (CA certs)
            File serverPemDir = new File(serverPemPath).getParentFile();
            if (serverPemDir != null && !serverPemDir.exists()) {
                serverPemDir.mkdirs();
            }

            try (FileWriter serverWriter = new FileWriter(serverPemPath)) {
                serverWriter.write(wrapCert(ca0));
                if (!ca1.isEmpty()) {
                    serverWriter.write(wrapCert(ca1));
                }
            }
            Log.i(TAG, "Wrote server certificates to: " + serverPemPath);

            Log.i(TAG, "Certificate enrollment successful");
            return true;

        } catch (Exception ex) {
            Log.e(TAG, "Certificate enrollment failed", ex);
            return false;
        }
    }

    private String httpGet(String urlStr, String authHeader) throws IOException {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "GET " + urlStr + " -> " + responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorBody = readStream(conn.getErrorStream() != null ?
                    new BufferedReader(new InputStreamReader(conn.getErrorStream())) : null);
            Log.e(TAG, "GET failed, status: " + responseCode + ", body: " + errorBody);
            conn.disconnect();
            return null;
        }

        String body = readStream(new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)));
        conn.disconnect();
        return body;
    }

    private String httpPost(String urlStr, String authHeader, String postBody) throws IOException {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", authHeader);
        conn.setRequestProperty("Content-Type", "text/plain");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(postBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        Log.d(TAG, "POST " + urlStr + " -> " + responseCode);

        if (responseCode != HttpURLConnection.HTTP_OK) {
            String errorBody = readStream(conn.getErrorStream() != null ?
                    new BufferedReader(new InputStreamReader(conn.getErrorStream())) : null);
            Log.e(TAG, "POST failed, status: " + responseCode + ", body: " + errorBody);
            conn.disconnect();
            return null;
        }

        String body = readStream(new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)));
        conn.disconnect();
        return body;
    }

    private String readStream(BufferedReader reader) throws IOException {
        if (reader == null) return "";
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString().trim();
    }

    private void setupTrustAllCerts() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    }

    private static String wrapCert(String data) {
        return "-----BEGIN CERTIFICATE-----\n" + data + "\n-----END CERTIFICATE-----\n";
    }
}
