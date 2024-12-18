package io.opentakserver.opentakicu;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.Log;

import com.ctc.wstx.stax.WstxInputFactory;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.opentakserver.opentakicu.contants.Preferences;
import io.opentakserver.opentakicu.cot.Contact;
import io.opentakserver.opentakicu.cot.Detail;
import io.opentakserver.opentakicu.cot.Point;
import io.opentakserver.opentakicu.cot.Status;
import io.opentakserver.opentakicu.cot.Takv;
import io.opentakserver.opentakicu.cot.auth;
import io.opentakserver.opentakicu.cot.Cot;
import io.opentakserver.opentakicu.cot.event;
import io.opentakserver.opentakicu.cot.uid;
import io.opentakserver.opentakicu.utils.CSRGenerator;
import io.opentakserver.opentakicu.utils.PEMImporter;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class TcpClient extends Thread implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String TAG = TcpClient.class.getSimpleName();
    public static final String TAK_SERVER_CONNECTED = "tak_server_connected";
    public static final String TAK_SERVER_DISCONNECTED = "tak_server_disconnected";

    private SharedPreferences prefs;

    public String serverAddress;
    public int port;
    private boolean atak_auth = false;
    private String atak_username;
    private String atak_password;
    private boolean atak_ssl = false;
    private boolean atak_ssl_self_signed = true;
    private String atak_trust_store;
    private String atak_trust_store_password;
    private String atak_client_cert;
    private String atak_client_cert_password;
    private String uid;
    private String path;

    private Socket socket;
    private Context context;
    private Intent batteryStatus;

    private String mServerMessage;
    private OnMessageReceived mMessageListener;
    public boolean mRun = false;
    private OutputStream mBufferOut;
    private BufferedReader mBufferIn;

    /**
     * Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TcpClient(Context context, String serverAddress, int port, OnMessageReceived listener) {
        this.context = context;
        this.serverAddress = serverAddress;
        this.port = port;
        mMessageListener = listener;

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, ifilter);

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        getSettings();
    }

    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public void sendMessage(final String message) {
        try {
            if (mBufferOut != null) {
                mBufferOut.write(message.getBytes(Charset.defaultCharset()));
                mBufferOut.flush();
            }
        } catch(SocketException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "SocketException " + ex.getMessage() + ": " + sw);
        } catch (IOException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "IOException " + ex.getMessage() + ": " + sw);
        }
    }

    /**
     * Close the connection and release the members
     */
    public void stopClient() {
        mRun = false;

        if (mBufferOut != null) {
            try {
                mBufferOut.flush();
            } catch(IOException ex) {
                //DO nothing
            }

            try {
                mBufferOut.close();
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close buffer", e);
            }
        }

        prefs.unregisterOnSharedPreferenceChangeListener(this);
        mMessageListener = null;
        mBufferIn = null;
        mBufferOut = null;
        mServerMessage = null;
        socket = null;
        Log.d(TAG, "Sending Broadcast " + TAK_SERVER_DISCONNECTED);
        context.sendBroadcast(new Intent(TAK_SERVER_DISCONNECTED).setPackage(context.getPackageName()));
    }

    public void run() {
        mRun = true;

        try {
            Log.d(TAG, "Connecting...");
            getSettings();

            if (atak_ssl) {
                SSLSocketFactory factory = null;
                if (atak_ssl_self_signed) {
                    Log.d(TAG, "Connecting via SSL to a server with a self signed cert");
                    KeyStore trusted = KeyStore.getInstance("PKCS12");
                    FileInputStream trust_store = new FileInputStream(atak_trust_store);

                    KeyStore client_cert_keystore = KeyStore.getInstance("PKCS12");
                    FileInputStream client_cert = new FileInputStream(atak_client_cert);

                    trusted.load(trust_store, atak_trust_store_password.toCharArray());
                    trust_store.close();

                    client_cert_keystore.load(client_cert, atak_client_cert_password.toCharArray());
                    client_cert.close();

                    TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    trustManagerFactory.init(trusted);

                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(client_cert_keystore, atak_trust_store_password.toCharArray());
                    SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
                    sslContext.init(kmf.getKeyManagers(),trustManagerFactory.getTrustManagers(),null);

                    factory = sslContext.getSocketFactory();
                    socket = factory.createSocket(serverAddress, port);
                    socket.setSoTimeout(1000);
                } else {
                    factory = certificateEnrollment();
                }

                if(factory == null) {
                    Log.e(TAG, "SSL Socket Factory is NULL");
                    throw new Exception("SSL Socket Factory is NULL");
                }

                Log.d(TAG, "Connecting to " + serverAddress + ":" + port);
                socket = factory.createSocket(serverAddress, port);
                socket.setSoTimeout(1000);

            } else {
                Log.d(TAG, "Connecting via TCP");
                socket = new Socket(serverAddress, port);
                socket.setSoTimeout(1000);
                Log.d(TAG, "Connected via TCP");
            }

            // Janky way to try to prevent a race condition where we send the initial CoT before the socket is connected
            Log.d(TAG, "Sleeping");
            Thread.sleep(1000);
            Log.d(TAG, "awake");

            mBufferOut = socket.getOutputStream();
            mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            context.sendBroadcast(new Intent(TAK_SERVER_CONNECTED).setPackage(context.getPackageName()));

            XmlFactory xmlFactory = XmlFactory.builder()
                    .xmlInputFactory(new WstxInputFactory())
                    .xmlOutputFactory(new WstxOutputFactory())
                    .build();

            XmlMapper xmlMapper = XmlMapper.builder(xmlFactory).build();
            xmlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

            if (atak_auth) {
                Cot cot = new Cot(atak_username, atak_password, uid);
                auth atakAuth = new auth(cot);
                sendMessage(xmlMapper.writeValueAsString(atakAuth));
            }

            // FTS requires this CoT to be sent before any others
            event event = new event();
            event.setUid(uid);

            Point point = new Point(9999999, 9999999, 9999999);
            event.setPoint(point);

            Contact contact = new Contact(path);

            Takv takv = new Takv(context);

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level * 100 / (float)scale;

            Detail detail = new Detail(contact, null, null, null, takv, new uid(path), null, new Status(batteryPct));
            event.setDetail(detail);

            sendMessage(xmlMapper.writeValueAsString(event));

            try {
                //in this while the client listens for the messages sent by the server
                while (mRun) {
                    try {
                        mServerMessage = mBufferIn.readLine();
                        if (mServerMessage != null && mMessageListener != null) {
                            mMessageListener.messageReceived(mServerMessage);
                        }
                    } catch (SocketException e) {
                        Log.e(TAG, "Socket closed");
                        stopClient();
                    } catch (SocketTimeoutException e) {}
                }
                stopClient();
            } catch (Exception e) {
                Log.e(TAG, "Error", e);
                stopClient();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error", e);
            stopClient();
        }
    }

    public void setmRun(boolean mRun) {
        if (!mRun)
            Log.d(TAG, "Stopping thread");
        this.mRun = mRun;
    }

    private void getSettings() {
        serverAddress = prefs.getString(Preferences.ATAK_SERVER_ADDRESS, Preferences.ATAK_SERVER_ADDRESS_DEFAULT);
        port = Integer.parseInt(prefs.getString(Preferences.ATAK_SERVER_PORT, Preferences.ATAK_SERVER_PORT_DEFAULT));
        atak_auth = prefs.getBoolean(Preferences.ATAK_SERVER_AUTHENTICATION, Preferences.ATAK_SERVER_AUTHENTICATION_DEFAULT);
        atak_username = prefs.getString(Preferences.ATAK_SERVER_USERNAME, Preferences.ATAK_SERVER_USERNAME_DEFAULT);
        atak_password = prefs.getString(Preferences.ATAK_SERVER_PASSWORD, Preferences.ATAK_SERVER_PASSWORD_DEFAULT);
        atak_ssl = prefs.getBoolean(Preferences.ATAK_SERVER_SSL, Preferences.ATAK_SERVER_SSL_DEFAULT);
        atak_ssl_self_signed = prefs.getBoolean(Preferences.ATAK_SERVER_SELF_SIGNED_CERT, Preferences.ATAK_SERVER_SELF_SIGNED_CERT_DEFAULT);
        atak_trust_store = prefs.getString(Preferences.ATAK_SERVER_SSL_TRUST_STORE, Preferences.ATAK_SERVER_SSL_TRUST_STORE_DEFAULT);
        atak_trust_store_password = prefs.getString(Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD, Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD_DEFAULT);
        atak_client_cert = prefs.getString(Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE, Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_DEFAULT);
        atak_client_cert_password = prefs.getString(Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_PASSWORD, Preferences.ATAK_SERVER_SSL_CLIENT_CERTIFICATE_PASSWORD_DEFAULT);
        uid = prefs.getString(Preferences.UID, Preferences.UID_DEFAULT);
        path = prefs.getString(Preferences.STREAM_PATH, Preferences.STREAM_PATH_DEFAULT);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        getSettings();
    }

    //Declare the interface. The method messageReceived(String message) will must be implemented in the Activity
    //class at on AsyncTask doInBackground
    public interface OnMessageReceived {
        public void messageReceived(String message);
    }

    /**
     * getCertificate - In order to communicate with a TAK server that is using a well-known certificate
     * i.e. LetsEncrypt,
     *
     * 1. is to obtain the TLS configuration information from the server with a simple HTTP GET
     * 2. is to generate the CSR using this information to the server
     * 3. send the CSR to the server
     */

    private SSLSocketFactory certificateEnrollment() throws Exception {
        OkHttpClient okHttpClient = new OkHttpClient();

        StringBuilder sb = new StringBuilder("https://");
        sb.append(serverAddress).append(":8446/Marti/api/tls/config");

        Request request = new Request.Builder()
                .url(sb.toString())
                .get()
                .addHeader("Authorization", Credentials.basic(atak_username, atak_password))
                .build();


        try {
            Response response = okHttpClient.newCall(request).execute();
            if(response.isSuccessful()) {
                String xml = response.body().string();
                Map<String, String> namedEntries = parseTLSConfig(xml);

                // Generate the CSR
                KeyPair keyPair = CSRGenerator.generateKeyPair();
                String csr = CSRGenerator.generateCSR(keyPair, namedEntries);
                Log.d(TAG, "CSR: " + csr);
                List<String> certFiles = getCertificateFromServer(csr, keyPair.getPrivate());
                if(certFiles == null) {
                    throw new Exception("Unable to obtain certs from server");
                }
                return PEMImporter.createSSLFactory(new File(certFiles.get(0)), new File(certFiles.get(1)), "atakatak");
            } else {
                Log.e(TAG, "Unable to obtain TLS Configurationg");
                return null;
            }
        } catch(IOException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "IOException - Failed to get TLS configuration: " + ex.getMessage() + " - " + sw);
            throw ex;
        } catch (Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Unknown exception - Failed to get TLS configuration: " + ex.getMessage() + " - " + sw);
            throw ex;
        }
    }

    private Map<String, String> parseTLSConfig(final String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
            doc.getDocumentElement().normalize();

            Map<String, String> namedEntries = new HashMap<>();
            namedEntries.put("CN", atak_username);
            String namespaceURI = "http://bbn.com/marti/xml/config";

            NodeList namedEntriesXML = doc.getElementsByTagName("nameEntry");

            for(int k=0; k<namedEntriesXML.getLength(); k++) {
                Element namedEntry = (Element) namedEntriesXML.item(k);
                namedEntries.put(namedEntry.getAttribute("name"), namedEntry.getAttribute("value"));
            }

            return namedEntries;
        } catch(Exception ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Caught unknown exception parsing TLS Config: " + ex.getMessage() + " - " + sw);
            return null;
        }
    }

    private List<String>  getCertificateFromServer(final String csr, final PrivateKey privateKey) throws IOException, JSONException, PackageManager.NameNotFoundException {
        try {
            OkHttpClient okHttpClient = new OkHttpClient();

            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);

            StringBuilder sb = new StringBuilder("https://");
            sb.append(serverAddress).append(":8446/Marti/api/tls/signClient/v2?clientUid=")
                    .append(Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID))
                    .append("&version=")
                    .append(packageInfo.versionName);

            Log.d(TAG, "Submitting CSR to: " + sb);
            RequestBody requestBody = RequestBody.create(csr.getBytes());

            Request request = new Request.Builder()
                    .url(sb.toString())
                    .post(requestBody)
                    .addHeader("Authorization", Credentials.basic(atak_username, atak_password))
                    .addHeader("Content-Type", "text/plain")
                    .build();

            Response response = okHttpClient.newCall(request).execute();
            if(response.isSuccessful()) {
                Log.d(TAG, "Successfully obtained client cert");
                JSONObject jsonObject = new JSONObject(response.body().string());

                String signedCert = jsonObject.get("signedCert").toString();
                String ca0 = jsonObject.get("ca0").toString();
                String ca1 = jsonObject.get("ca1").toString();

                List<String> certFiles = new ArrayList<>();
                certFiles.add(writeCertToInternalStorage("client.pem", wrapCert(signedCert) + privateKeyToPEM(privateKey)));
                certFiles.add(writeCertToInternalStorage("server.pem", wrapCert(ca0) + wrapCert(ca1)));
                return certFiles;
            } else {
                Log.d(TAG, "Unable to obtain client cert");
                return null;
            }
        } catch(IOException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Caught IOException: " + ex.getMessage() + " - " + sw);
            throw ex;
        } catch(JSONException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Caught JSONException: " + ex.getMessage() + " - " + sw);
            throw ex;
        } catch (PackageManager.NameNotFoundException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Caught NameNotFoundException: " + ex.getMessage() + " - " + sw);
            throw ex;
        }
    }

    private String writeCertToInternalStorage(String filename, String data) throws IOException{
        File dir = new File(context.getFilesDir(), "certs");
        if(!dir.exists()) {
            dir.mkdir();
        }

        try {
            File outputFile = new File(dir, filename);
            FileWriter writer = new FileWriter(outputFile);
            writer.append(data);
            Log.d(TAG, "Writing Cert To File: " + data);
            writer.flush();
            writer.close();
            return outputFile.getAbsolutePath();
        } catch(IOException ex) {
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            Log.e(TAG, "Caught IOException: " + ex.getMessage() + " - " + sw);
            throw ex;
        }
    }

    private String wrapCert(final String cert) {
        StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
        sb.append(cert).append("\n-----END CERTIFICATE-----\n");
        return sb.toString();
    }

    private String privateKeyToPEM(PrivateKey privateKey) {
        // 1. Get the private key's encoded form
        byte[] keyBytes = privateKey.getEncoded();

        // 2. Convert to Base64 for PEM format
        String base64Key = Base64.getEncoder().encodeToString(keyBytes);

        // 3. Add PEM format headers and footers
        StringBuilder pemKey = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        pemKey.append(formatInChunks(base64Key, 64))
                .append("\n-----END PRIVATE KEY-----");

        return pemKey.toString();
    }
    private String formatInChunks(String base64Key, int chunkSize) {
        StringBuilder formattedKey = new StringBuilder();
        int index = 0;
        while (index < base64Key.length()) {
            formattedKey.append(base64Key, index, Math.min(index + chunkSize, base64Key.length()));
            formattedKey.append("\n");
            index += chunkSize;
        }
        return formattedKey.toString().trim();
    }
}
