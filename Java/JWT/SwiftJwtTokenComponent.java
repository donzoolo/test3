package com.example.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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

    // Removed the duplicated constant. Now it's referenced from SwiftApiGatewayService.
    
    // Configuration values injected directly
    @Value("${swift.p12File}")
    private String p12File;
    @Value("${swift.p12Password}")
    private String p12Password;
    @Value("${swift.consumerKey}")
    private String consumerKey;
    @Value("${swift.scope}")
    private String scope;
    
    // Inject only the base URL
    @Value("${swift.baseUrl}")
    private String baseUrl; 
    
    // Security primitives cached after initialization
    private PrivateKey privateKey;
    private X509Certificate leafCert;
    private String x5cHeader;
    private String subjectDn;

    // Canonical Audience URL, constructed during initialization
    private String canonicalAudienceUrl;


    @PostConstruct
    public void initSecurityPrimitives() throws Exception {
        logger.info("Initializing Swift security primitives...");
        
        KeyStore keyStore = loadKeyStore();
        extractCertificateAndKey(keyStore);
        
        this.x5cHeader = extractX5c(this.leafCert);
        this.subjectDn = extractSubject(this.leafCert);
        
        // Construct the full token URL using the injected base URL and the constant from the Service
        String tokenUrl = this.baseUrl + SwiftApiGatewayService.TOKEN_PATH;
        
        // FIX: Derive the Audience (aud) claim directly from tokenUrl by removing the scheme.
        URI uri = new URI(tokenUrl);
        // Concatenate host and path, which is the required scheme-less format for 'aud'
        this.canonicalAudienceUrl = uri.getHost() + uri.getPath(); 
        
        logger.info("JWT signing setup complete. Subject DN: {}", subjectDn);
        logger.info("Canonical Audience (aud) set to: {}", canonicalAudienceUrl);
    }
    
    /**
     * Builds and signs the JWT assertion required for the OAuth token request.
     * The Audience (aud) claim is generated internally using the canonical URL.
     * * @param tokenEndpoint The full URL of the token endpoint (used by the service for debug logging)
     */
    public String buildSignedJwt(String tokenEndpoint) throws Exception { 
        long now = Instant.now().getEpochSecond();
        
        // 1. Header (contains x5c extracted during init)
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"x5c\":[\"" + x5cHeader + "\"]}";
        
        // 2. Payload (uses consumerKey, subjectDn, and the canonical Audience URL)
        String payload = String.format(
            "{\"iss\":\"%s\",\"sub\":\"%s\",\"aud\":\"%s\",\"jti\":\"%s\",\"iat\":%d,\"exp\":%d}",
            consumerKey, subjectDn, this.canonicalAudienceUrl, // Uses the canonical DNS name URL without scheme
            UUID.randomUUID().toString(), now, now + 300
        );

        String headerB64 = base64Url(header);
        String payloadB64 = base64Url(payload);
        String signingInput = headerB64 + "." + payloadB64;

        // 3. Signature
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        
        // Directly apply Base64Url encoding to the raw signature bytes.
        String signatureB64 = Base64.getUrlEncoder().withoutPadding()
                                    .encodeToString(sig.sign());

        return signingInput + "." + signatureB64;
    }
    
    public String getConsumerKey() { return consumerKey; }
    public String getP12Password() { return p12Password; }
    public String getP12File() { return p12File; } 
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
        // Calling getName() without arguments returns the DN string, which is then cleaned by the regex.
        String subject = cert.getSubjectX500Principal().getName();
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