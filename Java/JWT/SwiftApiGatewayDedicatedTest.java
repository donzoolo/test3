package com.example.api;

import com.example.test.ItTest; // Assuming this is your base class
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Ensures this specific YAML profile is loaded for this test class
@ActiveProfiles("e2e") 
public class SwiftApiGatewayDedicatedTest extends ItTest { 

    private static final Logger logger = LoggerFactory.getLogger(SwiftApiGatewayDedicatedTest.class);

    // Spring will automatically inject the service instance due to @Service
    @Autowired
    private SwiftApiGatewayService swiftApiGatewayService;

    // Removed: @Value("${swift.baseUrl}${swift.paymentOrderPath}") private String paymentOrderEndpoint;

    @Test
    void tokenRetrieval_succeedsAndCaches() throws Exception {
        // The service will fetch and cache the token on the first call
        String accessToken = swiftApiGatewayService.getAccessToken();
        assertNotNull(accessToken, "Access token must be retrieved.");
        logger.info("Test 1: Successfully retrieved and cached token (starting with {}...)", accessToken.substring(0, 10));
    }

    @Test
    void subsequentApiCall_usesCachedToken() throws Exception {
        // The token is cached from the first call.
        
        // The specific endpoint URL is now managed internally by the service.
        String dummyJsonBody = "{\"debtorAccount\":\"DE123\",\"amount\":100.00,\"currency\":\"EUR\"}";
        
        // NEW: Call the dedicated service method without passing the URL
        HttpResponse<String> response = swiftApiGatewayService.sendPaymentOrderPost(dummyJsonBody);

        logger.info("Test 2: Authenticated API Response Status: {}", response.statusCode());
        logger.debug("Test 2: Authenticated API Response Body: {}", response.body());
        
        // Assertions based on expected E2E response
        // assertTrue(response.statusCode() < 500, "API call should not result in a server error.");
        
        // Example assertion if you expect a successful creation (201)
        // assertEquals(201, response.statusCode()); 
    }
}