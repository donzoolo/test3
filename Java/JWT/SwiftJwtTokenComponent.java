package com.example.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

/**
 * Component responsible for loading the P12 certificate, extracting the private key
 * and X5C header, and performing the JWT signing operation.
 * These security primitives are loaded once on startup.
 */
@Component
public class SwiftJwtTokenComponent {

    private static final Logger logger = LoggerFactory.getLogger(SwiftJwtTokenComponent.class);

    // Configuration values injected directly
    @Value("${swift.p12File}")
    private String p12File;
    @Value("${swift.p12Password}")
    private String p12Password;
    @Value("${swift.consumerKey}")
    private String consumerKey;
    @Value("${swift.scope}")
    private String scope;
    
    // Security primitives cached after initialization
    private PrivateKey privateKey;
    private X509Certificate leafCert;
    private String x5cHeader;
    private String subjectDn;

    // Must be injected in the Service, but we define the logic here
    public String getTokenEndpoint(String env) {
        return "production".equals(env) ? 
            "https://api.swift.com/oauth2/v1/token" : 
            "https://sandbox.swift.com/oauth2/v1/token";
    }

    @PostConstruct
    public void initSecurityPrimitives() throws Exception {
        logger.info("Initializing Swift security primitives...");
        
        KeyStore keyStore = loadKeyStore();
        extractCertificateAndKey(keyStore);
        
        this.x5cHeader = extractX5c(this.leafCert);
        this.subjectDn = extractSubject(this.leafCert);
        
        logger.info("JWT signing setup complete. Subject DN: {}", subjectDn);
    }
    
    /**
     * Builds and signs the JWT assertion required for the OAuth token request.
     */
    public String buildSignedJwt(String tokenEndpoint) throws Exception {
        long now = Instant.now().getEpochSecond();
        
        // 1. Header (contains x5c extracted during init)
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"x5c\":[\"" + x5cHeader + "\"]}";
        
        // 2. Payload (uses consumerKey and subjectDn extracted during init)
        String payload = String.format(
            "{\"iss\":\"%s\",\"sub\":\"%s\",\"aud\":\"%s\",\"jti\":\"%s\",\"iat\":%d,\"exp\":%d}",
            consumerKey, subjectDn, tokenEndpoint,
            UUID.randomUUID().toString(), now, now + 300
        );

        String headerB64 = base64Url(header);
        String payloadB64 = base64Url(payload);
        String signingInput = headerB64 + "." + payloadB64;

        // 3. Signature
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = base64Url(new String(Base64.getEncoder().encode(sig.sign())));

        return signingInput + "." + signatureB64;
    }
    
    public String getConsumerKey() { return consumerKey; }
    public String getP12Password() { return p12Password; }
    public String getScope() { return scope; }
    public X509Certificate getLeafCert() { return leafCert; }
    
    // --- Private Setup Methods ---
    
    private KeyStore loadKeyStore() throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(Paths.get(p12File))) {
            keyStore.load(is, p12Password.toCharArray());
        } catch (IOException e) {
             logger.error("Error loading P12 file: {}. Check file path and existence.", p12File, e);
             throw e;
        }
        return keyStore;
    }
    
    private void extractCertificateAndKey(KeyStore keyStore) throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        for (String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias)) {
                this.leafCert = (X509Certificate) keyStore.getCertificate(alias);
                this.privateKey = (PrivateKey) keyStore.getKey(alias, p12Password.toCharArray());
                return;
            }
        }
        logger.error("No private key entry found in .p12 file: {}", p12File);
        throw new RuntimeException("No private key entry found in .p12 file: " + p12File);
    }
    
    private String extractX5c(X509Certificate cert) throws CertificateEncodingException {
        String pem = pemEncode(cert);
        return pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replaceAll("\\s", "");
    }

    private String extractSubject(X509Certificate cert) {
        String subject = cert.getSubjectX500Principal()
            .getName(javax.naming.ldap.Rdn.escapeValue());
        return subject.replaceAll("\\s*=\\s*", "=");
    }
    
    // --- Helper Methods ---
    
    private String base64Url(String input) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private String pemEncode(X509Certificate cert) throws CertificateEncodingException {
        return "-----BEGIN CERTIFICATE-----\n" +
            Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(cert.getEncoded()) +
            "\n-----END CERTIFICATE-----\n";
    }
}