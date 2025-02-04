package com.lunax.telegramBot.utils;

import org.springframework.stereotype.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SolanaCliUtils {
    
    @Value("${solana.home.dir:${user.home}/.config/solana}")
    private String solanaHomeDir;

    @Value("${solana.cluster.url}")
    private String clusterUrl;
    
    /**
     * Creates a new memecoin using the Solana CLI with Token-2022 program
     * @param walletKeyPath Path to the wallet keypair file
     * @param name Token name
     * @param symbol Token symbol
     * @param decimals Number of decimal places for the token
     * @param metadataUri URI to the token metadata JSON
     * @return Token mint address
     */
    public String createMemecoin(String walletKeyPath, String name, String symbol, int decimals, String metadataUri) throws Exception {
        // Create token using Token-2022 program
        ProcessBuilder createToken = new ProcessBuilder(
            "spl-token", 
            "create-token",
            "--enable-metadata",
            "--decimals", String.valueOf(decimals),
            "--program-id", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        Process process = createToken.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to create token: " + new String(process.getErrorStream().readAllBytes()));
        }
        
        // Extract token address from output
        String tokenAddress = output.split("Creating token ")[1].split("\n")[0].trim();
        
        // Initialize metadata
        ProcessBuilder initMetadata = new ProcessBuilder(
            "spl-token",
            "initialize-metadata",
            tokenAddress,
            name,
            symbol,
            metadataUri,
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        process = initMetadata.start();
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to initialize metadata: " + new String(process.getErrorStream().readAllBytes()));
        }
        
        // Create token account
        ProcessBuilder createAccount = new ProcessBuilder(
            "spl-token",
            "create-account",
            tokenAddress,
            "-p", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        process = createAccount.start();
        String accountOutput = new String(process.getInputStream().readAllBytes());
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to create token account: " + new String(process.getErrorStream().readAllBytes()));
        }
        
        String tokenAccount = accountOutput.split("Creating account ")[1].split("\n")[0].trim();
        
        return tokenAddress;
    }
    
    /**
     * Mints tokens to an account
     * @param walletKeyPath Path to the wallet keypair file
     * @param tokenAddress Token mint address
     * @param tokenAccount Token account address
     * @param amount Amount to mint
     */
    public void mintTokens(String walletKeyPath, String tokenAddress, String tokenAccount, long amount) throws Exception {
        ProcessBuilder mintTokens = new ProcessBuilder(
            "spl-token",
            "mint",
            tokenAddress,
            String.valueOf(amount),
            tokenAccount,
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        Process process = mintTokens.start();
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to mint tokens: " + new String(process.getErrorStream().readAllBytes()));
        }
    }
    
    /**
     * Revokes mint authority to prevent further token minting
     * @param walletKeyPath Path to the wallet keypair file
     * @param tokenAddress Token mint address
     */
    public void revokeMintAuthority(String walletKeyPath, String tokenAddress) throws Exception {
        ProcessBuilder revokeAuth = new ProcessBuilder(
            "spl-token",
            "authorize",
            tokenAddress,
            "--disable",
            "mint",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        Process process = revokeAuth.start();
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to revoke mint authority: " + new String(process.getErrorStream().readAllBytes()));
        }
    }
    
    /**
     * Saves a wallet keypair to a temporary file
     * @param privateKey Base58 encoded private key
     * @return Path to the saved keypair file
     */
    public String saveWalletKeypair(String privateKey) throws Exception {
        // Create temporary directory if it doesn't exist
        Path tempDir = Paths.get(solanaHomeDir, "temp");
        Files.createDirectories(tempDir);
        
        // Create temporary file for keypair
        String filename = UUID.randomUUID().toString() + ".json";
        Path keypairPath = tempDir.resolve(filename);
        
        // Write private key to file
        Files.write(keypairPath, privateKey.getBytes());
        
        return keypairPath.toString();
    }
    
    /**
     * Deletes a temporary wallet keypair file
     * @param keypairPath Path to the keypair file
     */
    public void deleteWalletKeypair(String keypairPath) {
        try {
            Files.deleteIfExists(Paths.get(keypairPath));
        } catch (Exception e) {
            log.error("Failed to delete keypair file: {}", e.getMessage());
        }
    }
}
