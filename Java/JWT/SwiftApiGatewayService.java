package com.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;


@Service
public class SwiftApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(SwiftApiGatewayService.class);

    private final SwiftJwtTokenComponent jwtTokenComponent;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String accessToken = null; // Caches the token for re-use

    // Configuration values injected directly via @Value
    // institutionHeader is now hardcoded as requested
    private final String institutionHeader = "AABBCCDD";
    
    @Value("${swift.consumerSecret}")
    private String consumerSecret;
    // Removed @Value("${swift.env}") private String env;
    @Value("${swift.tokenEndpoint}")
    private String TOKEN_ENDPOINT;

    /**
     * Constructor injects the security component and sets up the HttpClient for non-production use.
     * This setup uses a relaxed SSL context to bypass certificate hostname validation issues (like using an IP address).
     */
    public SwiftApiGatewayService(SwiftJwtTokenComponent jwtTokenComponent) {
        this.jwtTokenComponent = jwtTokenComponent;

        logger.debug("Token endpoint set to: {}", TOKEN_ENDPOINT);

        // Build HttpClient with relaxed SSL rules (the only configuration needed for non-prod)
        HttpClient.Builder builder = HttpClient.newBuilder();
        
        logger.warn("Configuring HttpClient with INSECURE SSL trust manager. This configuration MUST NOT BE USED in production.");
        
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            // Use a TrustManager that accepts all server certificates
            TrustManager[] trustAllCerts = new TrustManager[]{new InsecureTrustManager()};
            sslContext.init(null, trustAllCerts, null);
            builder.sslContext(sslContext);

            // NOTE: Since the tokenEndpoint now uses a DNS name, the Java HttpClient automatically
            // sets the correct 'Host' header and, given the hosts file fix, the connection should proceed.
            // Remember to start the JVM with: -Djdk.internal.httpclient.disableHostnameVerification=true
            
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            logger.error("Failed to initialize relaxed SSL context.", e);
            throw new RuntimeException("Failed to initialize relaxed SSL context.", e);
        }
        
        this.client = builder.build();
    }

    /**
     * Retrieves the access token, using the cached one if available (thread-safe check).
     */
    public String getAccessToken() throws Exception {
        if (accessToken == null) {
            synchronized (this) {
                if (accessToken == null) {
                    accessToken = fetchNewAccessToken();
                }
            }
        }
        return accessToken;
    }

    /**
     * Executes the JWT Bearer Token request to get a new access token.
     */
    private String fetchNewAccessToken() throws Exception {
        logger.info("Fetching new access token.");
        
        // 1. Build JWT using the component. 
        // The component now generates the 'aud' claim using the canonical host derived from TOKEN_ENDPOINT.
        String jwt = jwtTokenComponent.buildSignedJwt();
        logger.debug("JWT assertion built (length: {})", jwt.length());
        
        // 2. Basic Auth (Consumer Key:Consumer Secret)
        String basicAuth = Base64.getEncoder().encodeToString(
            (jwtTokenComponent.getConsumerKey() + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        
        // 3. Request body
        String body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer"
                    + "&assertion=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8)
                    + "&scope=" + URLEncoder.encode(jwtTokenComponent.getScope(), StandardCharsets.UTF_8);

        // 4. Send request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_ENDPOINT))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                // Removed X-Forwarded-Host: The Host header is now automatically set by HttpClient
                // based on the DNS name in TOKEN_ENDPOINT.
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("Token request failed with status {}. Response body: {}", response.statusCode(), response.body());
            throw new RuntimeException("Token request failed: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String token = root.get("access_token").asText();
        
        logger.info("Access token retrieved successfully. Expires in: {}s", root.get("expires_in").asText());
        return token;
    }
    
    /**
     * Sends a POST request to a given URL using the cached access token.
     */
    public HttpResponse<String> sendAuthenticatedPost(String url, String jsonBody) throws Exception {
        String token = getAccessToken(); // Fetches token if it's null

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                // Removed manual Host header. HttpClient automatically sets it.
                .header("Institution", institutionHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        logger.info("Sending authenticated POST request to: {}", url);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Trust Manager that does no validation. Only for non-production use.
     */
    private static class InsecureTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}