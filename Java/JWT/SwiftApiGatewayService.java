package com.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


@Service
public class SwiftApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(SwiftApiGatewayService.class);

    // Hardcoded API Paths - TOKEN_PATH is now public static for access by the component
    public static final String TOKEN_PATH = "/oauth2/v1/token";
    private static final String PAYMENT_ORDER_PATH = "/api/v1/payment-order";

    private final SwiftJwtTokenComponent jwtTokenComponent;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String accessToken = null; // Caches the token for re-use

    // Hardcoded mandatory header
    private final String institutionHeader = "AABBCCDD";
    
    @Value("${swift.consumerSecret}")
    private String consumerSecret;
    
    // Inject only the base URL
    @Value("${swift.baseUrl}")
    private String baseUrl;

    private final String tokenUrl;
    private final String paymentOrderUrl;


    /**
     * Constructor injects the security component and sets up the HttpClient.
     * * IMPORTANT: This version assumes the test environment's SSL certificate is 
     * trusted by the default Java trust store. If you encounter SSL handshake errors, 
     * you must revert to the previous code with the InsecureTrustManager.
     */
    public SwiftApiGatewayService(SwiftJwtTokenComponent jwtTokenComponent) {
        this.jwtTokenComponent = jwtTokenComponent;
        
        // Construct the full URLs using the injected base URL and hardcoded paths
        this.tokenUrl = baseUrl + TOKEN_PATH;
        this.paymentOrderUrl = baseUrl + PAYMENT_ORDER_PATH;

        logger.debug("Token URL set to: {}", tokenUrl);
        logger.debug("Payment Order URL set to: {}", paymentOrderUrl);

        // Build standard HttpClient.
        this.client = HttpClient.newBuilder().build();
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
        // The component now constructs the 'aud' claim internally using the hardcoded path.
        String jwt = jwtTokenComponent.buildSignedJwt(tokenUrl); // Passing token URL for consistency
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
                .uri(URI.create(tokenUrl))
                .header("Authorization", "Basic " + basicAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
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
     * Sends a POST request to the payment order endpoint using the cached access token.
     */
    public HttpResponse<String> sendPaymentOrderPost(String jsonBody) throws Exception {
        return sendAuthenticatedRequest(this.paymentOrderUrl, jsonBody);
    }

    /**
     * Generic internal method to send a POST request with the cached access token.
     */
    private HttpResponse<String> sendAuthenticatedRequest(String url, String jsonBody) throws Exception {
        String token = getAccessToken(); // Fetches token if it's null

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("Institution", institutionHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        logger.info("Sending authenticated POST request to: {}", url);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}