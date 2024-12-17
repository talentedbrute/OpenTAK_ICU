package io.opentakserver.opentakicu.utils;

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

import io.opentakserver.opentakicu.TcpClient;

public class PEMImporter {
    public static final String TAG = TcpClient.class.getSimpleName();
    public static SSLSocketFactory createSSLFactory(File clientPEM, File serverPem, String keystorePassword) throws
            IOException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException, KeyManagementException, CertificateException, InvalidKeySpecException {
        // Create an SSLContext for TLS client
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // Configure SSLContext with certificate and truststore
        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        // Load client certificate and private key (if applicable)
        if (clientPEM != null ) {
            // Password for the keystore and the alias
            // Load the PEM certificate
            List<X509Certificate> certs = new ArrayList<>();
            // Create a keystore
            KeyStore keystore = KeyStore.getInstance("JKS");
            keystore.load(null, null);

            FileInputStream pemFileInputStream = new FileInputStream(clientPEM);
            PEMParser pemParser = new PEMParser(new PemReader(new InputStreamReader(pemFileInputStream)));
            Object pemObject;
            int i = 0;
            while((pemObject = pemParser.readPemObject()) != null) {
                if(pemObject instanceof PemObject) {
                    PemObject pem = (PemObject)pemObject;
                    if("CERTIFICATE".equals(pem.getType())) {
                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                        X509Certificate cert = (X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(pem.getContent()));
                        certs.add(cert);
                        keystore.setCertificateEntry("client_certificate_" + (i++), cert);
                    } else if("PRIVATE KEY".equals(pem.getType())) {
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(((PemObject) pemObject).getContent());
                        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
                        keystore.setKeyEntry("client_private_key", privateKey, keystorePassword.toCharArray(), certs.toArray(new X509Certificate[0]));
                    }
                }
            }

            // Close the streams
            pemFileInputStream.close();

            // Create a key manager with the client keystore
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, keystorePassword.toCharArray());
            keyManagers = keyManagerFactory.getKeyManagers();
        }

        // Load the server PEM file as a truststore
        if(serverPem != null) {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, null);

            try (FileInputStream serverPemInputStream = new FileInputStream(serverPem)) {
                // Load the server certificate from PEM
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                X509Certificate serverCert = (X509Certificate) certFactory.generateCertificate(serverPemInputStream);

                // Add the server certificate to the truststore
                trustStore.setCertificateEntry("server", serverCert);

                // Create a trust manager with the truststore
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
                trustManagers = trustManagerFactory.getTrustManagers();
            }
        }


        // Configure SSLContext with the custom trust and key managers
        sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());

        // Now you can use sslContext to create SSL sockets or configure an HttpClient, for example.
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        return sslSocketFactory;
    }
}
