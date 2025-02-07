package com.lunax.telegramBot.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.service.MemecoinService;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SolanaUtils {
    private final RpcClient client;
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private SolanaCliUtils solanaCliUtils;
    
    @Autowired
    private MemecoinService memecoinService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    public SolanaUtils(RpcClient rpcClient) {
        this.client = rpcClient;
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
     * Creates a new memecoin token on Solana
     * @param walletId The wallet ID to use for token creation
     * @param name The name of the token
     * @param symbol The token symbol
     * @param decimals Number of decimal places for the token
     * @param supply The total supply of tokens
     * @return The token mint address
     */
    public String createMemecoin(UUID walletId, String name, String symbol, int decimals, double supply, String metadataJson, Update update) {
        MessageUtils.sendMessage(update, "🎬 Starting memecoin creation...");
        
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        System.out.println("Found wallet with address: " + wallet.getWalletAddress());
        
        try {
            String privateKey = walletKeyManager.getPrivateKey(walletId);
            System.out.println("Retrieved private key length: " + privateKey.length());
            
            String keypairPath = solanaCliUtils.saveWalletKeypair(privateKey);
            System.out.println("Saved keypair to: " + keypairPath);
            
            try {
                System.out.println("Invoking createMemecoin on SolanaCliUtils");
                MessageUtils.sendMessage(update, "🎬 Creating a solana keypair...");
                // MessageUtils.sendFileMessageInlineKeyboard(update, privateKey, keypairPath, null);
                String tokenAddress = solanaCliUtils.createMemecoin(
                    keypairPath,
                    name,
                    symbol,
                    decimals,
                    metadataJson
                );
                
                System.out.println("Created token with address: " + tokenAddress);
                
                // long totalSupply = (long) (supply * Math.pow(10, decimals));
                // solanaCliUtils.mintTokens(
                //     keypairPath,
                //     tokenAddress,
                //     wallet.getWalletAddress(),
                //     totalSupply
                // );
                
                // solanaCliUtils.revokeMintAuthority(keypairPath, tokenAddress);

                // Extract image URL and description from metadata
                String imageUrl = getImageUrl(metadataJson);
                String description = getDescription(metadataJson);
                
                // Create the memecoin object
                Memecoin newMemecoin = new Memecoin();
                newMemecoin.setCreatorWalletId(walletId);
                newMemecoin.setName(name);
                newMemecoin.setSymbol(symbol);
                newMemecoin.setTotalSupply(supply);
                newMemecoin.setImageUrl(imageUrl); // Set the image URL
                newMemecoin.setDescription(description); // Set the description
                newMemecoin.setTokenAddress(tokenAddress); // Set the token address

                memecoinService.addMemecoin(newMemecoin);

                return tokenAddress;
            } finally {
                solanaCliUtils.deleteWalletKeypair(keypairPath);
            }
        } catch (Exception e) {
            log.error("Failed to create memecoin", e);
            throw new RuntimeException("Failed to create memecoin: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to get Account from address string
     * @param address Base58 encoded address
     * @return Account object or null if invalid
     */
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
            JsonNode metadataNode = objectMapper.readTree(metadataJson);
            return metadataNode.get("image").asText();
        } catch (Exception e) {
            log.error("Error parsing metadata JSON for image URL: {}", e.getMessage());
            return null;
        }
    }

    public String getDescription(String metadataJson) {
        try {
            JsonNode metadataNode = objectMapper.readTree(metadataJson);
            return metadataNode.get("description").asText();
        } catch (Exception e) {
            log.error("Error parsing metadata JSON for description: {}", e.getMessage());
            return null;
        }
    }
}
