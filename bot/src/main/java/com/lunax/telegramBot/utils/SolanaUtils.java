package com.lunax.telegramBot.utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.service.MemecoinDbService;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Collections;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import java.time.Duration;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Slf4j
@Component
public class SolanaUtils {
    private final RpcClient client;
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private SolanaCliUtils solanaCliUtils;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private MemecoinDbService memecoinDbService;
    
    @Autowired
    public SolanaUtils(
        RpcClient rpcClient,
        @Value("${solana.api.base-url:http://localhost:3000}") String apiBaseUrl,
        ObjectMapper objectMapper
    ) {
        this.client = rpcClient;
        this.apiBaseUrl = apiBaseUrl;
        this.objectMapper = objectMapper;
        
        // Configure RestTemplate with longer timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(60000);  // 60 seconds connection timeout
        factory.setReadTimeout(60000);     // 60 seconds read timeout
        
        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(60))
            .setReadTimeout(Duration.ofSeconds(60))
            .build();
    }
   
    @Value("${solana.cluster.url}")
    private String clusterUrl;

    /**
     * Generic method to make HTTP requests to the TypeScript API
     */
    private <T> ResponseEntity<T> makeRequest(
        String endpoint,
        HttpMethod method,
        Object body,
        Class<T> responseType
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
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(60000)  // 60 seconds connection timeout
                .setSocketTimeout(60000)    // 60 seconds read timeout
                .build();
            RestTemplate restTemplateWithTimeout = new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();

            ResponseEntity<T> response = restTemplateWithTimeout.exchange(
                apiBaseUrl + endpoint,
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
                AddLiquidityResponse.class
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
                CreateMarketResponse.class
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
                CreateAmmPoolResponse.class
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
                BurnLiquidityResponse.class
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
                RemoveLiquidityResponse.class
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
}
