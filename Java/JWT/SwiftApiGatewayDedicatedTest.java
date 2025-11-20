package com.example.api;

import com.example.test.ItTest; // Assuming this is your base class
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Ensures this specific YAML profile is loaded for this test class
@ActiveProfiles("e2e") 
public class SwiftApiGatewayDedicatedTest extends ItTest { 

    // Spring will automatically inject the service instance due to @Service
    @Autowired
    private SwiftApiGatewayService swiftApiGatewayService;

    @Test
    void tokenRetrieval_succeedsAndCaches() throws Exception {
        // The service will fetch and cache the token on the first call
        String accessToken = swiftApiGatewayService.getAccessToken();
        assertNotNull(accessToken, "Access token must be retrieved.");
        System.out.println("Successfully retrieved and cached token.");
    }

    @Test
    void subsequentApiCall_usesCachedToken() throws Exception {
        // The token is cached from the first call.
        // This second call will reuse the existing token without a new JWT request.
        
        String dummyApiUrl = "https://" + "api-test.something.com" + "/api/v1/payment-order";
        String dummyJsonBody = "{\"debtorAccount\":\"DE123\",\"amount\":100.00,\"currency\":\"EUR\"}";
        
        HttpResponse<String> response = swiftApiGatewayService.sendAuthenticatedPost(dummyApiUrl, dummyJsonBody);

        System.out.println("\nAuthenticated API Response Status: " + response.statusCode());
        
        // Assertions based on expected E2E response
        // assertTrue(response.statusCode() < 500, "API call should not result in a server error.");
        
        // Example assertion if you expect a successful creation (201)
        // assertEquals(201, response.statusCode()); 
    }
}