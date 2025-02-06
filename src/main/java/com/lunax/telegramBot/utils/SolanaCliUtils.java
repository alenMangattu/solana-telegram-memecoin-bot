package com.lunax.telegramBot.utils;

import org.springframework.stereotype.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Base58;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;

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
     * @param metadataJson URI to the token metadata JSON
     * @return Token mint address
     */
    public String createMemecoin(String walletKeyPath, String name, String symbol, int decimals, String metadataJson) throws Exception {
        System.out.println("Creating memecoin with keypair: " + walletKeyPath);
        
        // First create the token
        ProcessBuilder createToken = new ProcessBuilder(
            "spl-token", 
            "create-token",
            "--enable-metadata",
            "--decimals", String.valueOf(decimals),
            "--program-id", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--owner", walletKeyPath  // Add owner parameter
        );
        
        System.out.println("Create token command: " + String.join(" ", createToken.command()));
        
        Process process = createToken.start();
        String output = new String(process.getInputStream().readAllBytes());
        String error = new String(process.getErrorStream().readAllBytes());
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to create token: " + error);
        }

        // Extract token address from output
        String tokenAddress = output.split("Creating token ")[1].split(" under")[0].trim();
        log.info("Token created with address: " + tokenAddress);
        
        // Wait a bit for the transaction to be confirmed
        Thread.sleep(2000);
        
        // Initialize metadata with name, symbol and URI
        ProcessBuilder initMetadata = new ProcessBuilder(
            "spl-token",
            "initialize-metadata",
            tokenAddress,
            "--name", name,          // Add --name flag
            "--symbol", symbol,      // Add --symbol flag
            "--uri", metadataJson,   // Add --uri flag
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--mint-authority", walletKeyPath
        );
       
        log.info("Initializing metadata for token: " + tokenAddress);
        System.out.println("Metadata command: " + String.join(" ", initMetadata.command()));
        
        process = initMetadata.start();
        output = new String(process.getInputStream().readAllBytes());
        error = new String(process.getErrorStream().readAllBytes());
        process.waitFor(30, TimeUnit.SECONDS);

        System.out.println("Metadata output: " + output);
        System.out.println("Metadata error: " + error);

        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to initialize metadata: " + error);
        }
        
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
        System.out.println("Original private key: " + privateKey);
        
        // Create temporary directory if it doesn't exist
        Path tempDir = Paths.get(solanaHomeDir, "temp");
        Files.createDirectories(tempDir);
        
        // Create temporary file for keypair
        String filename = UUID.randomUUID().toString() + ".json";
        Path keypairPath = tempDir.resolve(filename);
        
        // Convert Base58 to byte array (this is the secret key)
        byte[] secretKey = Base58.decode(privateKey);
        System.out.println("Decoded key bytes length: " + secretKey.length);
        
        if (secretKey.length != 64) {
            throw new RuntimeException("Invalid secret key length: " + secretKey.length + " (expected 64)");
        }
        
        // Convert bytes to unsigned values and create JSON array
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < secretKey.length; i++) {
            // Convert to unsigned byte (0-255)
            int unsignedByte = secretKey[i] & 0xFF;
            jsonArray.append(unsignedByte);
            if (i < secretKey.length - 1) {
                jsonArray.append(",");
            }
        }
        jsonArray.append("]");
        
        String jsonContent = jsonArray.toString();
        System.out.println("Writing keypair JSON: " + jsonContent);
        
        // Write keypair to file
        Files.write(keypairPath, jsonContent.getBytes());
        Files.setPosixFilePermissions(keypairPath, 
            PosixFilePermissions.fromString("rw-------"));
        
        System.out.println("Created keypair file at: " + keypairPath);
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

    /**
     * Grinds a token address based on a given prefix
     * @param prefix The prefix to grind
     * @return The generated token address
     */
    private String grindTokenAddress(String prefix) throws Exception {
        ProcessBuilder grindAddress = new ProcessBuilder(
            "solana-keygen", 
            "grind", 
            "--starts-with", prefix + ":1"
        );
        
        Process process = grindAddress.start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor(30, TimeUnit.SECONDS);
        
        if (process.exitValue() != 0) {
            throw new RuntimeException("Failed to grind token address: " + new String(process.getErrorStream().readAllBytes()));
        }
        
        // Extract the public key from the output
        return output.split(" ")[0]; // Adjust this based on the actual output format
    }
}
