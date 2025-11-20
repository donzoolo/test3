@Service
public class SwiftApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(SwiftApiGatewayService.class);

    private final SwiftJwtTokenComponent jwtTokenComponent;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String accessToken = null; // Caches the token for re-use

    // Configuration values injected directly via @Value
    @Value("${swift.institutionHeader:AAAABBBB}")
    private String institutionHeader;
    @Value("${swift.hostHeader:api-test.something.com}")
    private String hostHeader;
    @Value("${swift.consumerSecret}")
    private String consumerSecret;
    @Value("${swift.env}")
    private String env;
    @Value("${swift.tokenEndpoint}") // New value injected from YAML
    private String TOKEN_ENDPOINT;

    /**
     * Constructor injects the security component and sets up the HttpClient based on environment.
     */
    public SwiftApiGatewayService(SwiftJwtTokenComponent jwtTokenComponent) {
        this.jwtTokenComponent = jwtTokenComponent;

        // TOKEN_ENDPOINT is now injected by Spring via @Value
        logger.debug("Token endpoint set to: {}", TOKEN_ENDPOINT);

        // Build HttpClient with mutual TLS logic only if production
        HttpClient.Builder builder = HttpClient.newBuilder();
        if ("production".equals(env)) {
            try {
                logger.info("Configuring HttpClient for mutual TLS (mTLS) in production environment.");
                
                // Initialize KeyManagerFactory and SSLContext using the component's primitives
                KeyStore ks = KeyStore.getInstance("PKCS12");
                // NOTE: Using p12File from the component for the actual file path.
                // The previous usage of getSerialNumber().toString() was incorrect for file pathing.
                ks.load(Files.newInputStream(Paths.get(jwtTokenComponent.getP12File())), 
                        jwtTokenComponent.getP12Password().toCharArray());
                
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
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
        
        // 1. Build JWT using the component and pass the injected TOKEN_ENDPOINT as the audience (aud)
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