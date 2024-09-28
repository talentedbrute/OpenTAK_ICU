package io.opentakserver.opentakicu.utils;

import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptor;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcePEMEncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemGenerationException;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Map;

public class CSRGenerator {
    private static final String TAG = "OTICU_CSRGen";
    static {
        Security.addProvider(new BouncyCastleProvider()); // Add BouncyCastle as a provider
    }

//    public static void main(String[] args) {
//        try {
//            // Generate key pair (private/public)
//            KeyPair keyPair = generateKeyPair();
//
//            // Generate CSR
//            String csr = generateCSR(keyPair, "CN=Test User, O=My Company, L=My City, C=US");
//            System.out.println("Generated CSR:\n" + csr);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    // Generate KeyPair
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA", "BC");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

//    public static String encryptPrivateKey(KeyPair keyPair, final String password) {
//        try {
//            PEMEncryptor pemEncryptor = new JcePEMEncryptorBuilder("AES-256-CBC")
//                    .build(password.toCharArray());
//
//            StringWriter privateKeyWriter = new StringWriter();
//            JcaPEMWriter pemWriter = new JcaPEMWriter(privateKeyWriter);
//            PKCS8Generator pkcs8Generator = new JcaPKCS8Generator(keyPair.getPrivate(), (OutputEncryptor) pemEncryptor);
//            pemWriter.writeObject(pkcs8Generator);
//            pemWriter.flush();
//            pemWriter.close();
//
//            return privateKeyWriter.toString();
//
//        } catch(PemGenerationException ex) {
//            StringWriter sw = new StringWriter();
//            ex.printStackTrace(new PrintWriter(sw));
//            Log.e(TAG, "PemGenerationException: " + ex.getMessage() + " - " + sw);
//        } catch (IOException ex) {
//            StringWriter sw = new StringWriter();
//            ex.printStackTrace(new PrintWriter(sw));
//            Log.e(TAG, "IOException: " + ex.getMessage() + " - " + sw);
//        }
//    }
//
    // Generate CSR
    public static String generateCSR(KeyPair keyPair, Map<String,String> namedEntries) throws Exception {
        StringBuilder sb = new StringBuilder();
        int k = 0;
        for(Map.Entry<String,String> entry : namedEntries.entrySet()) {
            sb.append(entry.getKey()).append("=");

            if(entry.getValue().contains(",")) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else {
                sb.append(entry.getValue());
            }

            if(++k < namedEntries.size()) {
                sb.append(",");
            }
        }
        Log.d(TAG, "Subject: " + sb);
        X500Name subject = new X500Name(sb.toString());

        JcaPKCS10CertificationRequestBuilder p10Builder =
                new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic());

        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
        ContentSigner signer = csBuilder.build(keyPair.getPrivate());

        PKCS10CertificationRequest csr = p10Builder.build(signer);
        StringWriter sw = new StringWriter();
        PemWriter pemWriter = new PemWriter(sw);
        try {
            PemObject pemObject = new PemObject("CERTIFICATE REQUEST", csr.getEncoded());
            pemWriter.writeObject(pemObject);
        } finally {
            pemWriter.close();
        }

        return sw.toString();
//                .replace("-----BEGIN CERTIFICATE REQUEST-----","")
//                .replace("-----END CERTIFICATE REQUEST-----", "");
//        return new String(org.bouncycastle.util.encoders.Base64.encode(csr.getEncoded()));
    }
}
