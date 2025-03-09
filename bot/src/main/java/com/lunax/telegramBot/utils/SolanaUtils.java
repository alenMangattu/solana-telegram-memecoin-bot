package com.lunax.telegramBot.utils;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SolanaUtils {
    private final RpcClient client;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    @Autowired
    private SolanaCliUtils solanaCliUtils;
    
    @Value("${helius.api.key}")
    private  String heliusApiKey;
    
    @Value("${birdeye.api.key}")
    private String birdEyeApiKey;
    
    @Autowired
    public SolanaUtils(
        RpcClient rpcClient,
        @Value("${solana.api.base-url:https://api.mainnet-beta.solana.com}") String apiBaseUrl,
        ObjectMapper objectMapper
    ) {
        this.client = rpcClient;
        this.apiBaseUrl = apiBaseUrl;
        this.objectMapper = objectMapper;
        
        // Configure RestTemplate with longer timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);  // 60 seconds connection timeout
        factory.setReadTimeout(60000);     // 60 seconds read timeout
    }
   
    @Value("${solana.cluster.url}")
    private String clusterUrl;

    // Add a new property for the TypeScript API URL
    @Value("${typescript.api.base-url:http://localhost:3000}")
    private String typescriptApiBaseUrl;

    /**
     * Generic method to make HTTP requests to the TypeScript API
     */
    private <T> ResponseEntity<T> makeRequest(
        String endpoint,
        HttpMethod method,
        Object body,
        ParameterizedTypeReference<T> responseType
    ) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> requestEntity = body != null
                ? new HttpEntity<>(body, headers)
                : new HttpEntity<>(headers);

            log.info("Making {} request to {}", method, endpoint);
            if (body != null) {
                log.debug("Request body: {}", objectMapper.writeValueAsString(body));
            }

            // Set a timeout for the request
            RestTemplate restTemplateWithTimeout = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();

            // Use typescriptApiBaseUrl instead of apiBaseUrl for TypeScript API requests
            ResponseEntity<T> response = restTemplateWithTimeout.exchange(
                typescriptApiBaseUrl + endpoint,
                method,
                requestEntity,
                responseType
            );

            log.info("Response status: {}", response.getStatusCode());
            log.debug("Response body: {}", objectMapper.writeValueAsString(response.getBody()));

            return response;
        } catch (Exception e) {
            log.error("Error making request to {}: {}", endpoint, e.getMessage(), e);
            throw new RuntimeException("API request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new Solana wallet and returns its public key and private key
     * @return Map containing the public key (as Base58 string) and private key (as Base58 string)
     */
    public Map<String, String> createNewWallet(UUID walletId) throws Exception {
        Account account = new Account();
        Map<String, String> walletInfo = new HashMap<>();
        
        // Get the public key in Base58 format
        walletInfo.put("publicKey", account.getPublicKey().toString());
        
        // Get the private key and encode it in Base58
        byte[] privateKey = account.getSecretKey();
        if (privateKey.length != 64) {
            throw new RuntimeException("Invalid private key length: " + privateKey.length);
        }
        
        String encodedPrivateKeyBase58 = Base58.encode(privateKey);
        log.debug("Private key generated");
        
        // Only store if walletId is provided
        if (walletId != null) {
            walletKeyManager.storePrivateKey(walletId, encodedPrivateKeyBase58);
        }
        
        walletInfo.put("privateKey", encodedPrivateKeyBase58);
        return walletInfo;
    }
    
    /**
     * Gets the balance of a Solana wallet address in SOL
     * @param address The wallet address in Base58 format
     * @return The balance in SOL (as a double)
     */
    public double getBalance(String address) {
        try {
            log.debug("Getting balance for address: {}", address);
            PublicKey publicKey = new PublicKey(address);
            
            try {
                long lamports = client.getApi().getBalance(publicKey);
                System.out.println(lamports);
                double solBalance = lamports / 1000000000.0;
                System.out.println(solBalance);
                return solBalance;
            } catch (Exception rpcError) {
                log.error("RPC Error: {}", rpcError.getMessage(), rpcError);
                throw new RuntimeException("RPC call failed", rpcError);
            }
        } catch (Exception e) {
            log.error("General Error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get balance for address: " + address, e);
        }
    }

    /**
     * Helper method to get Account from address string
     * @param address Base58 encoded address
     * @return Account object or null if invalid
     */
    @SuppressWarnings("unused")
    private Account getAccountFromAddress(String address) {
        try {
            byte[] privateKey = Base58.decode(address);
            return new Account(privateKey);
        } catch (Exception e) {
            return null;
        }
    }

    public String getImageUrl(String metadataJson) {
        try {
            // First try to parse as JSON
            try {
                JsonNode metadataNode = objectMapper.readTree(metadataJson);
                return metadataNode.get("image").asText();
            } catch (Exception e) {
                // If JSON parsing fails, check if it's a direct URL
                if (metadataJson.startsWith("http")) {
                    return metadataJson;
                }
                log.error("Error parsing metadata JSON for image URL: {}", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("Error processing image URL: {}", e.getMessage());
            return null;
        }
    }

    public String getDescription(String metadataJson) {
        try {
            // First try to parse as JSON
            try {
                JsonNode metadataNode = objectMapper.readTree(metadataJson);
                return metadataNode.get("description").asText();
            } catch (Exception e) {
                // If JSON parsing fails, use the URL as description
                if (metadataJson.startsWith("http")) {
                    return "Metadata URL: " + metadataJson;
                }
                log.error("Error parsing metadata JSON for description: {}", e.getMessage());
                return null;
            }
        } catch (Exception e) {
            log.error("Error processing description: {}", e.getMessage());
            return null;
        }
    }

    @Data
    private static class CreateMemecoinRequest {
        private String name;
        private String symbol;
        private int decimals;
        private double supply;
        private String metadataUri;
        private boolean revokeMintAuthority;
        private boolean revokeFreezeAuthority;
        private String walletPrivateKey;
        private Long chatId;
    }

    @Data
    private static class CreateMemecoinResponse {
        private String tokenAddress;
    }

    @Data
    private static class AddLiquidityRequest {
        private String tokenMint;
        private double tokenAmount;
        private double solAmount;
        private String walletPrivateKey;
    }

    @Data
    private static class AddLiquidityResponse {
        private String signature;
    }

    public String addLiquidity(
        String tokenMint,
        double tokenAmount,
        double solAmount,
        String walletPrivateKey
    ) {
        try {
            log.info("Adding liquidity for token: {}", tokenMint);
            
            AddLiquidityRequest request = new AddLiquidityRequest();
            request.setTokenMint(tokenMint);
            request.setTokenAmount(tokenAmount);
            request.setSolAmount(solAmount);
            request.setWalletPrivateKey(walletPrivateKey);

            ResponseEntity<AddLiquidityResponse> response = makeRequest(
                "/api/liquidity/add",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<AddLiquidityResponse>() {}
            );

            return response.getBody().getSignature();
        } catch (Exception e) {
            log.error("Failed to add liquidity for token {}: {}", tokenMint, e.getMessage());
            throw new RuntimeException("Failed to add liquidity", e);
        }
    }

    @SuppressWarnings("unused")
    private String resolveTokenAddress(String tokenIdentifier) {
        // Implementation
        return null; // Placeholder return, actual implementation needed
    }
  
    @SuppressWarnings("unused")
    private JsonNode getTokenMetadata(String tokenAddress) {
        // Implementation
        return null; // Placeholder return, actual implementation needed
    }

    @SuppressWarnings("unused")
    private JsonNode getUserTokenHoldings(String tokenAddress, String userWalletAddress) {
        // Implementation
        return null; // Placeholder return, actual implementation needed
    }

    @Data
    private static class CreateMarketRequest {
        private String tokenMint;
        private String walletPrivateKey;
        private int lotSize;
        private double tickSize;
    }

    @Data
    private static class CreateMarketResponse {
        private String marketId;
    }

    @Data
    private static class CreateAmmPoolRequest {
        private String fromToken;    // SOL token address
        private String toToken;      // Your token address
        private double fromAmount;   // SOL amount
        private double toAmount;     // Your token amount
        private String walletPrivateKey;
        private int decimals;
    }

    @Data
    private static class CreateAmmPoolResponse {
        private String poolId;
    }

    public String createMarket(String tokenMint, String walletPrivateKey) {
        try {
            log.info("Creating market for token: {}", tokenMint);
            
            CreateMarketRequest request = new CreateMarketRequest();
            request.setTokenMint(tokenMint);
            request.setWalletPrivateKey(walletPrivateKey);
            request.setLotSize(1);
            request.setTickSize(0.01);

            ResponseEntity<CreateMarketResponse> response = makeRequest(
                "/api/market/create",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<CreateMarketResponse>() {}
            );

            return response.getBody().getMarketId();
        } catch (Exception e) {
            log.error("Failed to create market for token {}: {}", tokenMint, e.getMessage());
            throw new RuntimeException("Failed to create market", e);
        }
    }

    public String createAmmPool(
        String fromToken,
        String toToken,
        double fromAmount,
        double toAmount,
        String walletPrivateKey,
        int decimals
    ) {
        try {
            log.info("Creating AMM pool for token: {}", toToken);
            
            CreateAmmPoolRequest request = new CreateAmmPoolRequest();
            request.setFromToken(fromToken);
            request.setToToken(toToken);
            request.setFromAmount(fromAmount);
            request.setToAmount(toAmount);
            request.setWalletPrivateKey(walletPrivateKey);
            request.setDecimals(decimals);

            ResponseEntity<CreateAmmPoolResponse> response = makeRequest(
                "/api/pool/create",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<CreateAmmPoolResponse>() {}
            );

            return response.getBody().getPoolId();
        } catch (Exception e) {
            log.error("Failed to create AMM pool for token {}: {}", toToken, e.getMessage());
            throw new RuntimeException("Failed to create AMM pool", e);
        }
    }

    @Data
    private static class BurnLiquidityRequest {
        private String poolId;
        private double burnPercentage;
        private String walletPrivateKey;
    }

    @Data
    private static class BurnLiquidityResponse {
        private String signature;
    }

    public String burnLiquidity(
        String poolId,
        double burnPercentage,
        String walletPrivateKey
    ) {
        try {
            log.info("Burning {}% of liquidity from pool: {}", burnPercentage, poolId);
            
            if (burnPercentage <= 0 || burnPercentage > 100) {
                throw new IllegalArgumentException("Burn percentage must be between 0 and 100");
            }
            
            BurnLiquidityRequest request = new BurnLiquidityRequest();
            request.setPoolId(poolId);
            request.setBurnPercentage(burnPercentage);
            request.setWalletPrivateKey(walletPrivateKey);

            ResponseEntity<BurnLiquidityResponse> response = makeRequest(
                "/api/liquidity/burn",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<BurnLiquidityResponse>() {}
            );

            return response.getBody().getSignature();
        } catch (Exception e) {
            log.error("Failed to burn liquidity from pool {}: {}", poolId, e.getMessage());
            throw new RuntimeException("Failed to burn liquidity", e);
        }
    }

    @Data
    private static class RemoveLiquidityRequest {
        private String poolId;
        private double removePercentage;
        private String walletPrivateKey;
    }

    @Data
    private static class RemoveLiquidityResponse {
        private String signature;
    }

    public String removeLiquidity(
        String poolId,
        double removePercentage,
        String walletPrivateKey
    ) {
        try {
            log.info("Removing {}% of liquidity from pool: {}", removePercentage, poolId);
            
            if (removePercentage <= 0 || removePercentage > 100) {
                throw new IllegalArgumentException("Remove percentage must be between 0 and 100");
            }
            
            RemoveLiquidityRequest request = new RemoveLiquidityRequest();
            request.setPoolId(poolId);
            request.setRemovePercentage(removePercentage);
            request.setWalletPrivateKey(walletPrivateKey);

            ResponseEntity<RemoveLiquidityResponse> response = makeRequest(
                "/api/liquidity/remove",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<RemoveLiquidityResponse>() {}
            );

            return response.getBody().getSignature();
        } catch (Exception e) {
            log.error("Failed to remove liquidity from pool {}: {}", poolId, e.getMessage());
            throw new RuntimeException("Failed to remove liquidity", e);
        }
    }

    /**
     * Fetches details for a specific memecoin using SolanaCliUtils.
     * @param tokenAddress The token address of the memecoin.
     * @return A map containing the memecoin details.
     */
    public Map<String, Object> getMemecoinDetails(String tokenAddress) {
        Map<String, Object> details = new HashMap<>();

        try {
            // Validate token address
            if (tokenAddress == null || tokenAddress.isEmpty()) {
                details.put("error", "Invalid token address.");
                return details;
            }

            // Get token metadata
            JsonNode metadata = solanaCliUtils.getTokenMetadata(tokenAddress);
            
            // Safely access fields with null checks
            details.put("currentPrice", metadata.has("price") ? metadata.get("price").asDouble() : 0.0);
            details.put("marketCap", metadata.has("marketCap") ? metadata.get("marketCap").asDouble() : 0.0);
            details.put("liquidity", metadata.has("liquidity") ? metadata.get("liquidity").asDouble() : 0.0);
            details.put("age", metadata.has("age") ? metadata.get("age").asText() : "N/A");
            details.put("volume", metadata.has("volume") ? metadata.get("volume").asDouble() : 0.0);
            details.put("makers", metadata.has("makers") ? metadata.get("makers").asText() : "N/A");
            details.put("30MinPriceDiff", metadata.has("30MinPriceDiff") ? metadata.get("30MinPriceDiff").asDouble() : 0.0);
            details.put("1HPriceDiff", metadata.has("1HPriceDiff") ? metadata.get("1HPriceDiff").asDouble() : 0.0);
            details.put("6HPriceDiff", metadata.has("6HPriceDiff") ? metadata.get("6HPriceDiff").asDouble() : 0.0);

        } catch (Exception e) {
            // Handle exceptions appropriately
            e.printStackTrace();
            details.put("error", "Failed to fetch memecoin details: " + e.getMessage());
        }

        return details;
    }

    /**
     * Fetches all token accounts for a given wallet address.
     * @param walletAddress The wallet address to check.
     * @return A list of token addresses.
     */
    public List<String> getTokenAccounts(String walletAddress) {
        try {
            // Fetch token accounts using SolanaCliUtils
            JsonNode tokenAccountsResponse = solanaCliUtils.getWalletTokenAccounts(walletAddress);
            List<String> tokenAddresses = new ArrayList<>();

            // Check if the response has the "accounts" field and is an array
            JsonNode tokenAccounts = tokenAccountsResponse.get("accounts");
            if (tokenAccounts != null && tokenAccounts.isArray()) {
                for (JsonNode account : tokenAccounts) {
                    JsonNode addressNode = account.get("address");
                    if (addressNode != null) {
                        // Add the address to the list
                        tokenAddresses.add(addressNode.asText()); // Get the address
                    } else {
                        log.warn("Address is null for account: {}", account);
                    }
                }
            } else {
                log.warn("Expected tokenAccounts to be an array, but got: {}", tokenAccountsResponse);
            }

            return tokenAddresses;
        } catch (Exception e) {
            log.error("Failed to fetch token accounts for wallet {}: {}", walletAddress, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches comprehensive token information by combining Helius API data with Raydium liquidity pool data.
     * @param tokenAddress The token address to fetch information for.
     * @return A map containing token information including price, liquidity, volume and other metrics.
     */
    public Map<String, Object> getTokenInformation(String tokenAddress) {
        Map<String, Object> tokenInfo = new HashMap<>();

        try {
            // Step 1: Get basic token information from Helius API
            String heliusApiUrl = "https://mainnet.helius-rpc.com/?api-key=" + heliusApiKey;
            
            // Create the payload for the Helius request
            Map<String, Object> payload = new HashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("id", 1);
            payload.put("method", "getAsset");
            Map<String, String> params = new HashMap<>();
            params.put("id", tokenAddress);
            payload.put("params", params);

            // Make the request to Helius API
            ResponseEntity<JsonNode> heliusResponse = new RestTemplate().postForEntity(
                heliusApiUrl,
                new HttpEntity<>(payload, createHeaders()),
                JsonNode.class
            );

            log.info("Helius API response for token {}: {}", tokenAddress, heliusResponse.getBody());

            if (heliusResponse.getStatusCode().is2xxSuccessful() && heliusResponse.getBody() != null) {
                JsonNode result = heliusResponse.getBody().get("result");
                if (result != null) {
                    // Extract relevant fields from Helius
                    if (result.has("content") && result.get("content").has("metadata")) {
                        tokenInfo.put("name", result.get("content").get("metadata").get("name").asText());
                        tokenInfo.put("symbol", result.get("content").get("metadata").get("symbol").asText());
                    }
                    
                    if (result.has("token_info")) {
                        JsonNode tokenInfoNode = result.get("token_info");
                        tokenInfo.put("decimals", tokenInfoNode.get("decimals").asInt());
                        tokenInfo.put("totalSupply", tokenInfoNode.get("supply").asLong());
                        
                        if (tokenInfoNode.has("price_info") && tokenInfoNode.get("price_info").has("price_per_token")) {
                            tokenInfo.put("priceUSD", tokenInfoNode.get("price_info").get("price_per_token").asDouble());
                        }
                    }
                } else {
                    log.error("No result found for token in Helius: {}", tokenAddress);
                }
            }
            
            // Step 2: Get pool information from Raydium API
            String raydiumPoolsUrl = String.format(
                "https://api-v3.raydium.io/pools/info/mint?mint1=%s&poolType=all&poolSortField=default&sortType=desc&pageSize=100&page=1",
                tokenAddress
            );
            
            // Make request to get pools for this token
            ResponseEntity<JsonNode> poolsResponse = new RestTemplate().getForEntity(
                raydiumPoolsUrl,
                JsonNode.class
            );
            
            log.info("Raydium pools response for token {}: {}", tokenAddress, poolsResponse.getBody());
            
            if (poolsResponse.getStatusCode().is2xxSuccessful() && poolsResponse.getBody() != null) {
                JsonNode data = poolsResponse.getBody().get("data");
                if (data != null && data.has("data") && data.get("data").isArray() && data.get("data").size() > 0) {
                    // Get the first pool (highest liquidity typically)
                    JsonNode firstPool = data.get("data").get(0);
                    String poolId = firstPool.get("id").asText();
                    
                    // Store basic pool info
                    tokenInfo.put("poolId", poolId);
                    tokenInfo.put("price", firstPool.get("price").asDouble());
                    tokenInfo.put("tvl", firstPool.get("tvl").asDouble());
                    tokenInfo.put("feeRate", firstPool.get("feeRate").asDouble());
                    
                    // Add day/week/month metrics if present
                    if (firstPool.has("day")) {
                        JsonNode dayData = firstPool.get("day");
                        tokenInfo.put("24hVolume", dayData.get("volume").asDouble());
                        tokenInfo.put("24hVolumeQuote", dayData.get("volumeQuote").asDouble());
                        tokenInfo.put("24hFee", dayData.get("volumeFee").asDouble());
                        tokenInfo.put("24hApr", dayData.get("apr").asDouble());
                        tokenInfo.put("24hPriceMin", dayData.get("priceMin").asDouble());
                        tokenInfo.put("24hPriceMax", dayData.get("priceMax").asDouble());
                    }
                    
                    if (firstPool.has("week")) {
                        JsonNode weekData = firstPool.get("week");
                        tokenInfo.put("7dVolume", weekData.get("volume").asDouble());
                        tokenInfo.put("7dApr", weekData.get("apr").asDouble());
                    }
                    
                    if (firstPool.has("month")) {
                        JsonNode monthData = firstPool.get("month");
                        tokenInfo.put("30dVolume", monthData.get("volume").asDouble());
                        tokenInfo.put("30dApr", monthData.get("apr").asDouble());
                    }
                    
                    // Get mint information
                    if (firstPool.has("mintA") && firstPool.has("mintB")) {
                        JsonNode mintA = firstPool.get("mintA");
                        JsonNode mintB = firstPool.get("mintB");
                        
                        // Determine which mint is the token we're looking for
                        if (mintB.get("address").asText().equals(tokenAddress)) {
                            tokenInfo.put("pairedWithSymbol", mintA.get("symbol").asText());
                            tokenInfo.put("pairedWithAddress", mintA.get("address").asText());
                        } else {
                            tokenInfo.put("pairedWithSymbol", mintB.get("symbol").asText());
                            tokenInfo.put("pairedWithAddress", mintB.get("address").asText());
                        }
                    }
                    
                    // Get LP mint info
                    if (firstPool.has("lpMint")) {
                        tokenInfo.put("lpMintAddress", firstPool.get("lpMint").get("address").asText());
                        tokenInfo.put("lpPrice", firstPool.get("lpPrice").asDouble());
                        tokenInfo.put("lpAmount", firstPool.get("lpAmount").asDouble());
                        tokenInfo.put("burnPercent", firstPool.get("burnPercent").asDouble());
                    }
                    
                    // Step 3: Get detailed pool information
                    String poolDetailUrl = String.format(
                        "https://api-v3.raydium.io/pools/info/ids?ids=%s",
                        poolId
                    );
                    
                    ResponseEntity<JsonNode> poolDetailResponse = new RestTemplate().getForEntity(
                        poolDetailUrl,
                        JsonNode.class
                    );
                    
                    log.info("Raydium pool detail response: {}", poolDetailResponse.getBody());
                    
                    if (poolDetailResponse.getStatusCode().is2xxSuccessful() && 
                        poolDetailResponse.getBody() != null && 
                        poolDetailResponse.getBody().has("data") && 
                        poolDetailResponse.getBody().get("data").isArray() && 
                        poolDetailResponse.getBody().get("data").size() > 0) {
                        
                        JsonNode poolDetail = poolDetailResponse.getBody().get("data").get(0);
                        
                        // Add any additional details not already captured above
                        tokenInfo.put("mintAmountA", poolDetail.get("mintAmountA").asDouble());
                        tokenInfo.put("mintAmountB", poolDetail.get("mintAmountB").asDouble());
                    }
                } else {
                    log.warn("No pools found for token: {}", tokenAddress);
                }
            } else {
                log.error("Failed to get pool information for token: {}", tokenAddress);
            }
            
        } catch (RestClientException e) {
            log.error("Error making request to API: {}", e.getMessage());
            tokenInfo.put("error", "Failed to fetch token information: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error processing token information: {}", e.getMessage());
            tokenInfo.put("error", "Error processing token information: " + e.getMessage());
        }

        return tokenInfo;
    }

    // Helper method to create headers for the request
    private  HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers; // No need to set the API key in headers
    }

    // Add a new method to get historical data
    public JsonNode getHistoricalData(String tokenAddress, String timeFrequency, String timeBefore) {
        String birdEyeApiUrl = "https://public-api.birdeye.so/defi/history_price";
        
        // Calculate the time range based on the timeBefore parameter
        long currentTime = System.currentTimeMillis() / 1000; // Current time in seconds
        long timeFrom = calculateTimeFrom(timeBefore); // Convert timeBefore to a timestamp

        // Construct the full URL with query parameters
        String url = String.format("%s?address=%s&address_type=token&type=%s&time_from=%d&time_to=%d",
                birdEyeApiUrl, tokenAddress, timeFrequency, timeFrom, currentTime);

        // Set up headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-KEY", birdEyeApiKey);
        headers.set("X-CHAIN", "solana");

        // Log the request parameters and headers
        log.info("Request URL: {}", url);
        log.info("Request Headers: {}", headers);

        try {
            ResponseEntity<JsonNode> response = new RestTemplate().exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully retrieved historical data for token: {}", tokenAddress);
                log.info("Historical data: {}", response.getBody());
                return response.getBody();
            } else {
                log.error("Failed to retrieve historical data: {}", response.getStatusCode());
                throw new RuntimeException("Failed to retrieve historical data");
            }
        } catch (RestClientException e) {
            log.error("Error making request to BirdEye API: {}", e.getMessage());
            throw new RuntimeException("Error fetching historical data: " + e.getMessage(), e);
        }
    }

    // Method to send historical data to the chart endpoint
    public InputStreamResource sendHistoricalDataToChart(String tokenAddress, JsonNode historicalData, String timeFrequency, String tokenTicker) {
        String chartUrl = "http://localhost:7000/chart";
        
        // Construct the payload
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        // Extract items from historicalData and format them
        if (historicalData != null && historicalData.has("data")) {
            JsonNode itemsNode = historicalData.get("data").get("items");
            for (JsonNode item : itemsNode) {
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("address", tokenAddress);
                itemData.put("unixTime", item.get("unixTime").asLong());
                itemData.put("value", item.get("value").asDouble());
                items.add(itemData);
            }
        }

        // Construct the data object
        data.put("items", items);
        
        // Ensure token_ticker is never null
        if (tokenTicker == null || tokenTicker.isEmpty()) {
            // If no token ticker is provided, use a meaningful default or the token address
            String shortAddress = tokenAddress.length() > 6 ? 
                tokenAddress.substring(0, 4) + "..." + tokenAddress.substring(tokenAddress.length() - 4) : 
                tokenAddress;
            tokenTicker = "Token-" + shortAddress;
        }
        
        // Remove the $ sign from the token ticker if it exists
        tokenTicker = tokenTicker.replace("$", "");
        
        // Set top-level fields
        payload.put("token_ticker", tokenTicker); // Now guaranteed to be non-null
        payload.put("ema_slow_period", 69); 
        payload.put("ema_fast_period", 12);
        payload.put("rsi_period", 14);
        payload.put("frequency", timeFrequency);
        payload.put("success", true);
        
        payload.put("data", data); // Add the data object to the payload

        log.info("Sending chart request with payload: token_ticker={}, frequency={}", tokenTicker, timeFrequency);

        // Create RestTemplate with proper message converters for handling binary data
        RestTemplate restTemplate = new RestTemplate();
        
        try {
            // Use ResponseEntity<byte[]> to get the binary data directly
            ResponseEntity<byte[]> response = restTemplate.exchange(
                chartUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload, createHeaders()),
                byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully received image data from chart endpoint. Size: {} bytes", response.getBody().length);
                
                // Convert the byte array to an InputStreamResource
                return new InputStreamResource(new ByteArrayInputStream(response.getBody()));
            } else {
                log.error("Failed to retrieve image from chart endpoint: {}", response.getStatusCode());
                return null; // Return null if there was an error
            }
        } catch (RestClientException e) {
            log.error("Error sending data to chart endpoint: {}", e.getMessage());
            return null; // Return null if there was an error
        }
    }

    // Helper method to calculate the timestamp based on the timeBefore parameter
    private long calculateTimeFrom(String timeBefore) {
        long seconds = 0;
        switch (timeBefore.charAt(timeBefore.length() - 1)) {
            case 'D': // Days
                seconds = Integer.parseInt(timeBefore.substring(0, timeBefore.length() - 1)) * 24 * 60 * 60;
                break;
            case 'M': // Months
                seconds = Integer.parseInt(timeBefore.substring(0, timeBefore.length() - 1)) * 30L * 24 * 60 * 60;
                break;
            case 'Y': // Years
                seconds = Integer.parseInt(timeBefore.substring(0, timeBefore.length() - 1)) * 365L * 24 * 60 * 60;
                break;
            default:
                throw new IllegalArgumentException("Unsupported timeBefore value: " + timeBefore);
        }
        return (System.currentTimeMillis() / 1000) - seconds; // Return the timestamp for the calculated time
    }

    /**
     * Fetches the top token holders for a given token address using the native Solana RPC method.
     * Results are cached for 2 minutes to reduce API calls.
     * @param tokenAddress The mint address of the token
     * @param limit The number of top holders to fetch (max 20 as per Solana RPC limitation)
     * @return List of maps containing holder information (address, amount, percentage)
     */
    @Cacheable(value = "topTokenHolders", key = "#tokenAddress + '-' + #limit", unless = "#result.isEmpty()")
    public List<Map<String, Object>> getTopTokenHolders(String tokenAddress, int limit) {
        List<Map<String, Object>> holdersList = new ArrayList<>();
        
        String heliusUrl = "https://mainnet.helius-rpc.com/?api-key=" + heliusApiKey;
        try {
            // Validate input
            if (tokenAddress == null || tokenAddress.isEmpty()) {
                log.error("Invalid token address provided");
                return holdersList;
            }
            
            log.info("Fetching top {} token holders for token: {} (not from cache)", Math.min(limit, 20), tokenAddress);
            
            // Ensure limit doesn't exceed the maximum of 20 returned by the API
            int effectiveLimit = Math.min(limit, 20);
            
            // Prepare the RPC request payload
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("jsonrpc", "2.0");
            requestBody.put("id", 1);
            requestBody.put("method", "getTokenLargestAccounts");
            requestBody.put("params", Collections.singletonList(tokenAddress));
            
            // Configure headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Make the RPC request to the Solana API
            ResponseEntity<JsonNode> response = new RestTemplate().postForEntity(
                heliusUrl,
                new HttpEntity<>(requestBody, headers),
                JsonNode.class
            );
            
            // Process the response
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode result = response.getBody().get("result");
                
                if (result != null && result.has("value") && result.get("value").isArray()) {
                    JsonNode accounts = result.get("value");
                    
                    // Calculate total supply from the accounts using BigDecimal for precision
                    BigDecimal totalSupply = BigDecimal.ZERO;
                    for (JsonNode account : accounts) {
                        String amount = account.get("amount").asText();
                        totalSupply = totalSupply.add(new BigDecimal(amount));
                    }
                    
                    // Process each account
                    int count = 0;
                    for (JsonNode account : accounts) {
                        if (count >= effectiveLimit) break;
                        
                        String address = account.get("address").asText();
                        String amountStr = account.get("amount").asText();
                        BigDecimal amountBD = new BigDecimal(amountStr);
                        int decimals = account.get("decimals").asInt();
                        
                        // Parse the amount as a Long (converting from string)
                        Long amountLong = Long.parseLong(amountStr);
                        
                        // Calculate percentage of total supply using BigDecimal
                        BigDecimal percentage = totalSupply.compareTo(BigDecimal.ZERO) > 0 
                            ? amountBD.multiply(BigDecimal.valueOf(100)).divide(totalSupply, 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                        
                        // Get UI amount (formatted with proper decimals)
                        String uiAmountString = account.has("uiAmountString") 
                            ? account.get("uiAmountString").asText() 
                            : formatAmount(amountBD, decimals);
                        
                        // Create holder info map with proper numeric types
                        Map<String, Object> holderInfo = new HashMap<>();
                        holderInfo.put("address", address);
                        holderInfo.put("amount", amountLong); // Store as Long as expected by consumer code
                        holderInfo.put("uiAmount", uiAmountString);
                        holderInfo.put("decimals", decimals);
                        holderInfo.put("percentage", percentage.doubleValue());
                        
                        holdersList.add(holderInfo);
                        count++;
                    }
                    
                    log.info("Successfully fetched {} token holders", holdersList.size());
                } else {
                    log.error("Invalid response format from Solana RPC");
                }
            } else {
                log.error("Error response from Solana RPC: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching top token holders: {}", e.getMessage(), e);
        }
        
        return holdersList;
    }

    /**
     * Helper method to format token amounts with proper decimal places
     */
    private String formatAmount(BigDecimal rawAmount, int decimals) {
        if (decimals == 0) return rawAmount.toString();
        
        BigDecimal divisor = BigDecimal.TEN.pow(decimals);
        BigDecimal formattedAmount = rawAmount.divide(divisor, decimals, RoundingMode.HALF_UP);
        
        // Remove trailing zeros after decimal point
        String result = formattedAmount.stripTrailingZeros().toPlainString();
        return result;
    }

    /**
     * Gets the current price of SOL in USD
     * @return The current SOL price in USD
     */
    public double getSolanaPrice() {
        // Implement your logic to fetch SOL price
        // This could be from an API, cache, or other source
        
        // For now, return a reasonable default value
        return 150.0; // Example value - replace with actual implementation
    }

    /**
     * Enum for transaction priority levels
     */
    public enum TransactionPriority {
        MIN, // Minimum priority fee
        MID, // Medium priority fee
        MAX  // Maximum priority fee
    }

    @Data
    private static class SwapRequest {
        private String tokenMint;      // The token address to buy
        private double solAmount;      // Amount of SOL to spend
        private double slippage;       // Slippage tolerance percentage 
        private String walletPrivateKey; // Private key of the wallet making the purchase
        private String priority;       // Transaction priority level (affects fees and speed)
    }

    @Data
    private static class LimitOrderRequest {
        private String tokenMint;      // The token address to buy
        private double solAmount;      // Amount of SOL to spend
        private double limitPrice;     // The target price at which to execute the order
        private double slippage;       // Slippage tolerance percentage
        private String walletPrivateKey; // Private key of the wallet making the purchase
    }

    @Data
    public static class SwapResponse {
        private boolean success;      // Whether the swap was successful
        private String signature;     // Transaction signature
        private String error;         // Error message if any
        private double amount;        // Amount of tokens received
        private double price;         // Execution price
    }

    /**
     * Buys a token with SOL with specified priority
     * @param tokenMint The token address to buy
     * @param solAmount Amount of SOL to spend
     * @param slippage Slippage tolerance percentage
     * @param walletPrivateKey Private key of the wallet making the purchase
     * @param priority Transaction priority level (affects fees and speed)
     * @return SwapResponse containing the transaction details
     */
    public SwapResponse buyToken(
        String tokenMint,
        double solAmount,
        double slippage, 
        String walletPrivateKey,
        TransactionPriority priority
    ) {
        try {
            log.info("Buying token: {} with priority: {}", tokenMint, priority);
            
            SwapRequest request = new SwapRequest();
            request.setTokenMint(tokenMint);
            request.setSolAmount(solAmount);
            request.setSlippage(slippage);
            request.setWalletPrivateKey(walletPrivateKey);
            request.setPriority(priority.toString());

            ResponseEntity<SwapResponse> response = makeRequest(
                "/api/swap/execute",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<SwapResponse>() {}
            );

            log.debug("Swap response: {}", response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to buy token {}: {}", tokenMint, e.getMessage());
            throw new RuntimeException("Failed to execute swap", e);
        }
    }

    /**
     * Creates a limit order to buy a token when it reaches a specific price
     * @param tokenMint The token address to buy
     * @param solAmount Amount of SOL to spend
     * @param limitPrice The target price at which to execute the order
     * @param slippage Slippage tolerance percentage
     * @param walletPrivateKey Private key of the wallet making the purchase
     * @return Order ID for tracking the limit order
     */
    public String createLimitOrder(
        String tokenMint,
        double solAmount,
        double limitPrice,
        double slippage,
        String walletPrivateKey
    ) {
        try {
            log.info("Creating limit order for token: {} at price: {}", tokenMint, limitPrice);
            
            LimitOrderRequest request = new LimitOrderRequest();
            request.setTokenMint(tokenMint);
            request.setSolAmount(solAmount);
            request.setLimitPrice(limitPrice);
            request.setSlippage(slippage);
            request.setWalletPrivateKey(walletPrivateKey);

            ResponseEntity<Map<String, Object>> response = makeRequest(
                "/api/limit/create",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            log.debug("Limit order response: {}", response.getBody());
            return (String) response.getBody().get("orderId");
        } catch (Exception e) {
            log.error("Failed to create limit order for token {}: {}", tokenMint, e.getMessage());
            throw new RuntimeException("Failed to create limit order", e);
        }
    }

    /**
     * Gets the status of a limit order
     * @param orderId The ID of the limit order
     * @return Order status information
     */
    public Map<String, Object> getLimitOrderStatus(String orderId) {
        try {
            log.info("Getting limit order status for: {}", orderId);
            
            ResponseEntity<Map<String, Object>> response = makeRequest(
                "/api/limit/status?orderId=" + orderId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to get limit order status for {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to get limit order status", e);
        }
    }

    /**
     * Cancels a limit order
     * @param orderId The ID of the limit order
     * @param walletPrivateKey Private key of the wallet that created the order
     * @return True if the order was successfully canceled
     */
    public boolean cancelLimitOrder(String orderId, String walletPrivateKey) {
        try {
            log.info("Canceling limit order: {}", orderId);
            
            Map<String, String> request = new HashMap<>();
            request.put("orderId", orderId);
            request.put("walletPrivateKey", walletPrivateKey);

            ResponseEntity<Map<String, Object>> response = makeRequest(
                "/api/limit/cancel",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );

            Map<String, Object> responseBody = response.getBody();
            return (boolean) responseBody.get("success");
        } catch (Exception e) {
            log.error("Failed to cancel limit order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Failed to cancel limit order", e);
        }
    }

    /**
     * Sends blockchain instructions to the Solana network
     * @param instructions The array of transaction instructions to send
     * @param keypair The keypair for signing the transaction
     * @param options Additional options for sending the transaction
     * @return An array of transaction signatures
     */
    public String[] sendInstructions(
        Object[] instructions,
        Object keypair,
        Map<String, Object> options
    ) {
        try {
            log.info("Sending {} instructions to Solana network", instructions.length);
            
            // In a real implementation, this would interact with the Solana blockchain
            // For now, we're just passing the request to our TypeScript API
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("instructions", instructions);
            requestBody.put("keypair", keypair);
            requestBody.put("options", options);
            
            ResponseEntity<Map<String, Object>> response = makeRequest(
                "/api/transaction/send",
                HttpMethod.POST,
                requestBody,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("signatures")) {
                List<String> signatures = (List<String>) responseBody.get("signatures");
                return signatures.toArray(new String[0]);
            }
            
            // If we reached here, something went wrong but no exception was thrown
            throw new RuntimeException("Failed to get transaction signatures from response");
        } catch (Exception e) {
            log.error("Failed to send instructions to Solana network: {}", e.getMessage());
            throw new RuntimeException("Failed to send instructions", e);
        }
    }

    /**
     * Gets the current price of a token in USD
     * @param tokenMint The token mint address
     * @return The current token price in USD
     */
    public double getTokenPrice(String tokenMint) {
        try {
            log.info("Getting price for token: {}", tokenMint);
            
            ResponseEntity<Map<String, Object>> response = makeRequest(
                "/api/token/price/" + tokenMint,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("price")) {
                return Double.parseDouble(responseBody.get("price").toString());
            }
            
            return 0.0;
        } catch (Exception e) {
            log.error("Failed to get price for token {}: {}", tokenMint, e.getMessage());
            return 0.0;
        }
    }
}