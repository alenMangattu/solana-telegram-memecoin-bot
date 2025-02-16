package com.lunax.telegramBot.utils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

            ResponseEntity<T> response = restTemplate.exchange(
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
}
