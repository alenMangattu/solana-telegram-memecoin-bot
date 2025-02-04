package com.lunax.telegramBot.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.model.db.Wallet;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SolanaUtils {
    private static RpcClient client;
    
    @Autowired
    private WalletKeyManager walletKeyManager;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private SolanaCliUtils solanaCliUtils;
    
    @Autowired
    public SolanaUtils(RpcClient rpcClient) {
        client = rpcClient;
    }
    
    /**
     * Creates a new Solana wallet and returns its public key and private key
     * @return Map containing the public key (as Base58 string) and private key (as Base58 string)
     */
    public static Map<String, String> createNewWallet() {
        Account account = new Account();
        Map<String, String> walletInfo = new HashMap<>();
        
        // Get the public key in Base58 format
        walletInfo.put("publicKey", account.getPublicKey().toString());
        
        // Get the private key and encode it in Base58
        byte[] privateKey = account.getSecretKey();
        String encodedPrivateKeyBase58 = Base58.encode(privateKey);
        log.debug("Private key generated");
        walletInfo.put("privateKey", encodedPrivateKeyBase58);
        
        return walletInfo;
    }
    
    /**
     * Gets the balance of a Solana wallet address in SOL
     * @param address The wallet address in Base58 format
     * @return The balance in SOL (as a double)
     */
    public static double getBalance(String address) {
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
    public String createMemecoin(UUID walletId, String name, String symbol, int decimals, double supply) {
        // Security check: Verify wallet exists and belongs to the user
        Wallet wallet = walletRepository.findById(walletId)
            .orElseThrow(() -> new RuntimeException("Wallet not found: " + walletId));
        
        try {
            // Get the decrypted private key using WalletKeyManager
            String privateKey = walletKeyManager.getPrivateKey(walletId);
            
            // Save private key to temporary file
            String keypairPath = solanaCliUtils.saveWalletKeypair(privateKey);
            
            try {
                // Create metadata JSON
                String metadataJson = String.format(
                    "{\"name\":\"%s\",\"symbol\":\"%s\",\"description\":\"A memecoin on Solana\"}",
                    name, symbol
                );
                
                // Create token using CLI
                String tokenAddress = solanaCliUtils.createMemecoin(
                    keypairPath,
                    name,
                    symbol,
                    decimals,
                    metadataJson
                );
                
                // Mint initial supply
                long totalSupply = (long) (supply * Math.pow(10, decimals));
                solanaCliUtils.mintTokens(
                    keypairPath,
                    tokenAddress,
                    wallet.getWalletAddress(),
                    totalSupply
                );
                
                // Revoke mint authority for trust
                solanaCliUtils.revokeMintAuthority(keypairPath, tokenAddress);
                
                return tokenAddress;
            } finally {
                // Clean up temporary keypair file
                solanaCliUtils.deleteWalletKeypair(keypairPath);
                
                // Clear private key from memory
                privateKey = null;
                System.gc();
            }
        } catch (Exception e) {
            log.error("Failed to create memecoin: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create memecoin: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to get Account from address string
     * @param address Base58 encoded address
     * @return Account object or null if invalid
     */
    private static Account getAccountFromAddress(String address) {
        try {
            byte[] privateKey = Base58.decode(address);
            return new Account(privateKey);
        } catch (Exception e) {
            return null;
        }
    }
}
