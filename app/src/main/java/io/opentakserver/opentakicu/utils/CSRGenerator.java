package io.opentakserver.opentakicu.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;

import javax.security.auth.x500.X500Principal;

public class CSRGenerator {
    private static final String TAG = "OTICU_CSRGen";

    // Generate KeyPair
    public static KeyPair generateKeyPair() throws Exception {
        Log.d(TAG, "Setup for key pair generation");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return  keyPairGenerator.generateKeyPair();
    }

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
    }
}
