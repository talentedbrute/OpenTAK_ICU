package io.opentakserver.opentakicu.utils;

import android.util.Log;

import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class PEMImporter {
    private static final String TAG = Constants.TAG_PREFIX + "PEMImporter";

    public static SSLSocketFactory createSSLFactory(File clientPEM, File serverPem, final String keystorePassword) throws
            IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException, CertificateException, InvalidKeySpecException {

        // Create an SSLContext for TLS client
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // Configure SSLContext with certificate and truststore
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        // Load client certificate and private key (if applicable)
        if (clientPEM != null) {
            if (!clientPEM.exists()) {
                throw new IOException("Client PEM file does not exist: " + clientPEM.getAbsolutePath());
            }

            Log.i(TAG, "Loading client certificate from: " + clientPEM.getAbsolutePath());

            List<X509Certificate> certs = new ArrayList<>();
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null, null);

            FileInputStream pemFileInputStream = new FileInputStream(clientPEM);
            PEMParser pemParser = new PEMParser(new PemReader(new InputStreamReader(pemFileInputStream)));
            Object pemObject;
            int certCount = 0;
            boolean privateKeyLoaded = false;

            while ((pemObject = pemParser.readPemObject()) != null) {
                if (pemObject instanceof PemObject) {
                    PemObject pem = (PemObject) pemObject;
                    Log.d(TAG, "Found PEM object of type: " + pem.getType());

                    if ("CERTIFICATE".equals(pem.getType())) {
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(pem.getContent()));
                        certs.add(cert);
                        keystore.setCertificateEntry("client_certificate_" + (certCount++), cert);
                        Log.i(TAG, "Added client certificate [" + certCount + "]: Subject=" + cert.getSubjectX500Principal().getName());
                    } else if ("PRIVATE KEY".equals(pem.getType()) || "RSA PRIVATE KEY".equals(pem.getType())) {
                        PrivateKey privateKey;
                        if ("RSA PRIVATE KEY".equals(pem.getType())) {
                            // PKCS#1 format - convert to PKCS#8
                            Log.d(TAG, "Converting RSA PRIVATE KEY (PKCS#1) to PKCS#8 format");
                            org.bouncycastle.asn1.pkcs.RSAPrivateKey rsaPrivKey =
                                    org.bouncycastle.asn1.pkcs.RSAPrivateKey.getInstance(pem.getContent());
                            org.bouncycastle.asn1.pkcs.PrivateKeyInfo privKeyInfo =
                                    new org.bouncycastle.asn1.pkcs.PrivateKeyInfo(
                                            new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
                                                    org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers.rsaEncryption,
                                                    org.bouncycastle.asn1.DERNull.INSTANCE),
                                            rsaPrivKey);
                            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privKeyInfo.getEncoded());
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            privateKey = keyFactory.generatePrivate(keySpec);
                        } else {
                            // PKCS#8 format - use directly
                            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pem.getContent());
                            privateKey = keyFactory.generatePrivate(keySpec);
                        }
                        keystore.setKeyEntry("client_private_key", privateKey, keystorePassword.toCharArray(), certs.toArray(new X509Certificate[0]));
                        privateKeyLoaded = true;
                        Log.i(TAG, "Loaded client private key (algorithm: " + privateKey.getAlgorithm() + ")");
                    }
                }
            }

            pemFileInputStream.close();

            if (certCount == 0) {
                throw new CertificateException("No client certificates found in client PEM file");
            }
            if (!privateKeyLoaded) {
                throw new CertificateException("No private key found in client PEM file");
            }
            Log.i(TAG, "Loaded " + certCount + " client certificate(s) and private key");

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keystorePassword.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        // Load the server PEM file as a truststore
        if (serverPem != null) {
            if (!serverPem.exists()) {
                throw new IOException("Server PEM file does not exist: " + serverPem.getAbsolutePath());
            }

            Log.i(TAG, "Loading server certificates from: " + serverPem.getAbsolutePath());

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);

            // Read all certificates from the PEM file (handles certificate chains)
            try (FileInputStream serverPemInputStream = new FileInputStream(serverPem)) {
                PEMParser serverPemParser = new PEMParser(new PemReader(new InputStreamReader(serverPemInputStream)));
                Object serverPemObject;
                int certIndex = 0;

                while ((serverPemObject = serverPemParser.readPemObject()) != null) {
                    if (serverPemObject instanceof PemObject) {
                        PemObject pem = (PemObject) serverPemObject;
                        if ("CERTIFICATE".equals(pem.getType())) {
                            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                            X509Certificate serverCert = (X509Certificate) certFactory.generateCertificate(
                                    new ByteArrayInputStream(pem.getContent()));
                            trustStore.setCertificateEntry("server_" + certIndex++, serverCert);
                            Log.i(TAG, "Added trusted CA certificate [" + certIndex + "]: Subject=" +
                                    serverCert.getSubjectX500Principal().getName());
                        }
                    }
                }

                if (certIndex == 0) {
                    throw new CertificateException("No CA certificates found in server PEM file");
                }
                Log.i(TAG, "Loaded " + certIndex + " CA certificate(s) into truststore");

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
        }

        // Configure SSLContext with the custom trust and key managers
        sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());

        return sslContext.getSocketFactory();
    }
}
