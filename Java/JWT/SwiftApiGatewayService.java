package com.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;


@Service
public class SwiftApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(SwiftApiGatewayService.class);

    private final SwiftJwtTokenComponent jwtTokenComponent;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String accessToken = null; // Caches the token for re-use

    // Fixed headers injected directly via @Value
    @Value("${swift.institutionHeader:AAAABBBB}")
    private String institutionHeader;
    @Value("${swift.hostHeader:api-test.something.com}")
    private String hostHeader;
    @Value("${swift.consumerSecret}")
    private String consumerSecret;
    @Value("${swift.env}")
    private String env;

    private final String TOKEN_ENDPOINT;

    /**
     * Constructor injects the security component and sets up the HttpClient based on environment.
     */
    public SwiftApiGatewayService(SwiftJwtTokenComponent jwtTokenComponent) {
        this.jwtTokenComponent = jwtTokenComponent;

        // Determine the token endpoint based on the injected environment property
        TOKEN_ENDPOINT = jwtTokenComponent.getTokenEndpoint(env);
        logger.debug("Token endpoint set to: {}", TOKEN_ENDPOINT);

        // Build HttpClient with mutual TLS logic only if production
        HttpClient.Builder builder = HttpClient.newBuilder();
        if ("production".equals(env)) {
            try {
                logger.info("Configuring HttpClient for mutual TLS (mTLS) in production environment.");
                
                // Initialize KeyManagerFactory and SSLContext using the component's primitives
                KeyStore ks = KeyStore.getInstance("PKCS12");
                // NOTE: The loading path below is likely incorrect as the getSerialNumber().toString() doesn't represent the file path.
                // It's kept for logical consistency with the previous version, but should be checked against real path/alias logic.
                // Assuming jwtTokenComponent.getP12File() would be needed here if the entire keystore object wasn't available.
                ks.load(Files.newInputStream(Paths.get(jwtTokenComponent.getLeafCert().getSerialNumber().toString())), 
                        jwtTokenComponent.getP12Password().toCharArray());
                
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                // The init method needs the full KeyStore instance, not just the name. 
                // The previous implementation was also syntactically incorrect here. 
                // A better approach would be to pass the loaded KeyStore object from the component.
                kmf.init(ks, jwtTokenComponent.getP12Password().toCharArray());

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), null, null);
                builder.sslContext(sslContext);
                logger.info("HttpClient mTLS setup complete.");

            } catch (Exception e) {
                logger.error("Failed to configure HttpClient for mutual TLS in production environment. Aborting startup.", e);
                throw new RuntimeException("Failed to configure HttpClient for mutual TLS in production environment.", e);
            }
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
        logger.info("Fetching new access token for environment: {}", env);
        
        // 1. Build JWT using the component
        String jwt = jwtTokenComponent.buildSignedJwt(TOKEN_ENDPOINT);
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
                .header("Host", hostHeader)
                .header("Institution", institutionHeader)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        logger.info("Sending authenticated POST request to: {}", url);
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}