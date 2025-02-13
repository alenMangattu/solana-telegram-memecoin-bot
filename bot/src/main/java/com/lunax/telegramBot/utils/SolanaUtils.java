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
import com.lunax.telegramBot.service.MemecoinService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Collections;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
<<<<<<< HEAD
import lombok.Data;
=======
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))

@Slf4j
@Component
public class SolanaUtils {
    private final RpcClient client;
<<<<<<< HEAD
    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;
=======
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private SolanaCliUtils solanaCliUtils;
    
    @Autowired
    private MemecoinService memecoinService;
    
    @Autowired
<<<<<<< HEAD
    private UserService userService;
    
    @Autowired
    public SolanaUtils(
        RpcClient rpcClient,
        @Value("${solana.api.base-url:http://localhost:3000}") String apiBaseUrl,
        RestTemplate restTemplate,
        ObjectMapper objectMapper
    ) {
        this.client = rpcClient;
        this.apiBaseUrl = apiBaseUrl;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
=======
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    public SolanaUtils(RpcClient rpcClient) {
        this.client = rpcClient;
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
    }
   
    @Value("${solana.cluster.url}")
    private String clusterUrl;

<<<<<<< HEAD
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
=======
    //private final RestTemplate restTemplate = new RestTemplate();
    
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))

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
<<<<<<< HEAD
            log.info("Getting balance for address: {}", address);
            ResponseEntity<BalanceResponse> response = makeRequest(
                "/api/wallet/balance/" + address,
                HttpMethod.GET,
                null,
                BalanceResponse.class
            );
            return response.getBody().getBalance();
        } catch (Exception e) {
            log.error("Failed to get balance for address {}: {}", address, e.getMessage());
            throw new RuntimeException("Failed to get balance", e);
        }
    }

    @Data
    private static class BalanceResponse {
        private double balance;
    }

    /**
     * Creates a new memecoin token on Solana.
     * 
     * @param walletPrivateKey The wallet private key
=======
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
     * Creates a new memecoin token on Solana.
     * 
     * @param walletId The wallet ID to use for token creation
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
     * @param name The name of the token
     * @param symbol The token symbol
     * @param decimals Number of decimal places for the token
     * @param supply The total token supply
<<<<<<< HEAD
     * @param metadataUri The token metadata URI
=======
     * @param metadataJson The token metadata JSON
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
     * @param revokeMintAuthority Flag to revoke the mint authority upon creation
     * @param revokeFreezeAuthority Flag to revoke the freeze authority upon creation
     * @param update The Telegram update object
     * @return The token mint address
     */
<<<<<<< HEAD
    public String createMemecoin(
        String walletPrivateKey,
        String name,
        String symbol,
        int decimals,
        double supply,
        String metadataUri,
        boolean revokeMintAuthority,
        boolean revokeFreezeAuthority,
        Update update
    ) {
        try {
            log.info("Creating memecoin: {} ({}) with supply {}", name, symbol, supply);

            // Create request object
            CreateMemecoinRequest request = new CreateMemecoinRequest();
            request.setName(name);
            request.setSymbol(symbol);
            request.setDecimals(decimals);
            request.setSupply(supply);
            request.setMetadataUri(metadataUri);
            request.setRevokeMintAuthority(revokeMintAuthority);
            request.setRevokeFreezeAuthority(revokeFreezeAuthority);
            request.setWalletPrivateKey(walletPrivateKey);

            // Make API request
            ResponseEntity<CreateMemecoinResponse> response = makeRequest(
                "/api/memecoin/create",
                HttpMethod.POST,
                request,
                CreateMemecoinResponse.class
            );

            String tokenAddress = response.getBody().getTokenAddress();

            // Create Memecoin entity - use the public key from the private key
            Account account = new Account(Base58.decode(walletPrivateKey));
            String publicKey = account.getPublicKey().toString();
            
            Memecoin newMemecoin = new Memecoin();
            newMemecoin.setCreatorWalletId(UUID.fromString(publicKey));
            newMemecoin.setName(name);
            newMemecoin.setSymbol(symbol);
            newMemecoin.setTotalSupply(supply);
            newMemecoin.setTokenAddress(tokenAddress);
            
            // Save to database
            memecoinService.addMemecoin(newMemecoin);

            return tokenAddress;
        } catch (Exception e) {
            log.error("Failed to create memecoin {}: {}", name, e.getMessage());
            throw new RuntimeException("Failed to create memecoin: " + e.getMessage());
=======
    public String createMemecoin(UUID walletId, String name, String symbol, int decimals, double supply, 
                                 String metadataJson, boolean revokeMintAuthority, boolean revokeFreezeAuthority, 
                                 Update update) {
        MessageUtils.sendMessage(update, "🎬 Starting memecoin creation...");
        
        try {
            Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
            
            System.out.println("Found wallet with address: " + wallet.getWalletAddress());
            
            String privateKey = walletKeyManager.getPrivateKey(walletId);
            System.out.println("Retrieved private key length: " + privateKey.length());
            
            String keypairPath = solanaCliUtils.saveWalletKeypair(privateKey);
            System.out.println("Saved keypair to: " + keypairPath);
            
            try {
                System.out.println("Invoking createMemecoin on SolanaCliUtils");
                MessageUtils.sendMessage(update, "🎬 Creating a solana keypair...");
                
                String tokenAddress = solanaCliUtils.createMemecoin(
                    keypairPath,
                    name,
                    symbol,
                    decimals,
                    metadataJson
                );

                MessageUtils.sendMessage(update, "Minting " + supply + " tokens to the token address...");
                String tokenAccount = solanaCliUtils.mintTokens(keypairPath, tokenAddress, supply);
                MessageUtils.sendMessage(update, "Created token account: " + tokenAccount);
                
                // Revoke mint authority if requested
                if (revokeMintAuthority) {
                    solanaCliUtils.revokeMintAuthority(keypairPath, tokenAddress);
                    MessageUtils.sendMessage(update, "✅ Revoked mint authority for token: " + tokenAddress);
                }
                
                // Revoke freeze authority if requested
                if (revokeFreezeAuthority) {
                    try {
                        solanaCliUtils.revokeFreezeAuthority(keypairPath, tokenAddress);
                        MessageUtils.sendMessage(update, "✅ Revoked freeze authority for token: " + tokenAddress);
                    } catch (RuntimeException e) {
                        if (e.getMessage().contains("already disabled")) {
                            MessageUtils.sendMessage(update, "ℹ️ Freeze authority was already disabled - this is good!");
                        } else {
                            throw e;
                        }
                    }
                }
                
                // Extract image URL and description from metadata
                String imageUrl = getImageUrl(metadataJson);
                String description = getDescription(metadataJson);
                
                // Create the memecoin object
                Memecoin newMemecoin = new Memecoin();
                newMemecoin.setCreatorWalletId(walletId);
                newMemecoin.setName(name);
                newMemecoin.setSymbol(symbol);
                newMemecoin.setTotalSupply(supply);
                newMemecoin.setImageUrl(imageUrl);
                newMemecoin.setDescription(description);
                newMemecoin.setTokenAddress(tokenAddress);
                
                memecoinService.addMemecoin(newMemecoin);
                MessageUtils.sendMessage(update, "✅ Successfully created memecoin: " + name);

                return tokenAddress;
            } finally {
                solanaCliUtils.deleteWalletKeypair(keypairPath);
            }
        } catch (Exception e) {
            log.error("Failed to create memecoin", e);
            MessageUtils.sendMessage(update, "❌ Error creating memecoin: " + e.getMessage());
            throw new RuntimeException("Failed to create memecoin: " + e.getMessage(), e);
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
        }
    }

    /**
     * Helper method to get Account from address string
     * @param address Base58 encoded address
     * @return Account object or null if invalid
     */
<<<<<<< HEAD
    @SuppressWarnings("unused")
=======
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
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

<<<<<<< HEAD
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
=======
    public boolean addLiquidity(Long telegramId, double tokenAmount, double solAmount) {
        try {
            // Retrieve the user's wallet
            User user = userService.findByTelegramId(telegramId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Retrieve the Wallet object using the currentWallet UUID
            Wallet currentWallet = user.getWallets().stream()
                    .filter(wallet -> wallet.getWalletId().equals(user.getCurrentWallet()))
                    .findFirst()
                    .orElse(null);

            if (currentWallet == null) {
                throw new RuntimeException("Current wallet not found for user.");
            }

            String walletAddress = currentWallet.getWalletAddress(); // Now this is safe

            // Construct the command to add liquidity
            ProcessBuilder addLiquidityCommand = new ProcessBuilder(
                "spl-token",
                "add-liquidity", // This is a hypothetical command; replace with the actual command if different
                "--token", walletAddress, // Token address
                "--amount", String.valueOf(tokenAmount), // Token amount
                "--sol", String.valueOf(solAmount), // SOL amount
                "--url", clusterUrl, // Cluster URL
                "--fee-payer", walletAddress // Fee payer
            );

            Process process = addLiquidityCommand.start();
            process.waitFor(30, TimeUnit.SECONDS);

            if (process.exitValue() != 0) {
                String error = new String(process.getErrorStream().readAllBytes());
                throw new RuntimeException("Failed to add liquidity: " + error);
            }

            return true; // Indicate success
        } catch (Exception e) {
            log.error("Error adding liquidity: {}", e.getMessage());
            return false; // Indicate failure
        }
    }

    private String resolveTokenAddress(String tokenIdentifier) throws Exception {
        // If it's already an address, validate and return it
        if (tokenIdentifier.matches("^[1-9A-HJ-NP-Za-km-z]{32,44}$")) {
            return tokenIdentifier;
        }
        
        // Otherwise, look up the address by symbol
        return solanaCliUtils.resolveTokenSymbol(tokenIdentifier);
    }
  
    private JsonNode getTokenMetadata(String tokenAddress) {
        try {
            return solanaCliUtils.getTokenMetadata(tokenAddress);
        } catch (Exception e) {
            log.error("Error getting token metadata: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode getUserTokenHoldings(String tokenAddress, String userWalletAddress) {
        try {
            return solanaCliUtils.getUserTokenAccount(tokenAddress, userWalletAddress);
        } catch (Exception e) {
            log.error("Error getting user holdings: {}", e.getMessage());
            return null;
        }
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
    }
}
