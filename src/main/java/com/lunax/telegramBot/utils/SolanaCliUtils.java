package com.lunax.telegramBot.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Base58;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
        System.out.println("DEBUG: Entering createMemecoin. walletKeyPath: " + walletKeyPath 
                + ", name: " + name 
                + ", symbol: " + symbol 
                + ", decimals: " + decimals 
                + ", metadataUri: " + metadataUri);
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
        System.out.println("DEBUG: Create token ProcessBuilder initialized with command: " + createToken.command());
        
        System.out.println("Create token command: " + String.join(" ", createToken.command()));
        
        Process process = createToken.start();
        System.out.println("DEBUG: Create token process started.");
        String output = new String(process.getInputStream().readAllBytes());
        String error = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Create token process output: " + output);
        System.out.println("DEBUG: Create token process error: " + error);
        
        process.waitFor(100, TimeUnit.SECONDS);
        System.out.println("DEBUG: Create token process finished. Exit value: " + process.exitValue());
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Create token process failed with error: " + error);
            throw new RuntimeException("Failed to create token: " + error);
        }

        // Extract token address from output
        String tokenAddress = output.split("Creating token ")[1].split(" under")[0].trim();
        System.out.println("DEBUG: Token created with address: " + tokenAddress);
        
        // Wait a bit for the transaction to be confirmed
        System.out.println("DEBUG: Waited 2000ms for transaction confirmation");
        Thread.sleep(2000);
        
        // Initialize metadata with POSITIONAL arguments
        System.out.println("DEBUG: Preparing to initialize metadata for token: " + tokenAddress);
        System.out.println("Fixing the metadata initialization command...");
        ProcessBuilder initMetadata = new ProcessBuilder(
            "spl-token",
            "initialize-metadata",
            tokenAddress,  // TOKEN_MINT_ADDRESS
            name,          // TOKEN_NAME (positional)
            symbol,        // TOKEN_SYMBOL (positional)
            metadataUri,   // TOKEN_URI (positional)
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--mint-authority", walletKeyPath
        );
        System.out.println("DEBUG: Initialize metadata ProcessBuilder command: " + initMetadata.command());
        
        System.out.println("DEBUG: Initializing metadata for token: " + tokenAddress);
        System.out.println("Metadata command: " + String.join(" ", initMetadata.command()));
        
        process = initMetadata.start();
        System.out.println("DEBUG: Metadata initialization process started.");
        output = new String(process.getInputStream().readAllBytes());
        error = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Metadata process output: " + output);
        System.out.println("DEBUG: Metadata process error: " + error);
        
        process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Metadata process finished. Exit value: " + process.exitValue());

        System.out.println("Meta");
        System.out.println("Metadata output: " + output);
        System.out.println("Metadata error: " + error);

        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Metadata initialization failed with error: " + error);
            throw new RuntimeException("Failed to initialize metadata: " + error);
        }
        
        System.out.println("DEBUG: Exiting createMemecoin with token address: " + tokenAddress);
        return tokenAddress;
    }
    
    /**
     * Creates an associated token account for a wallet
     * @param walletKeyPath Path to the wallet keypair file
     * @param tokenAddress Token mint address
     * @return The created token account address
     */
    public String createAssociatedTokenAccount(String walletKeyPath, String tokenAddress) throws Exception {
        System.out.println("DEBUG: Entering createAssociatedTokenAccount with walletKeyPath: " + walletKeyPath 
                + " and tokenAddress: " + tokenAddress);
        ProcessBuilder createAccount = new ProcessBuilder(
            "spl-token",
            "create-account",
            tokenAddress,
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--owner", getWalletPublicKey(walletKeyPath),
            "--program-id", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"  // Add program-id parameter
        );
        System.out.println("DEBUG: Create account ProcessBuilder command: " + createAccount.command());
        
        Process process = createAccount.start();
        System.out.println("DEBUG: Create account process started.");
        String output = new String(process.getInputStream().readAllBytes());
        String error = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Create account process output: " + output);
        System.out.println("DEBUG: Create account process error: " + error);
        
        process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Create account process finished. Exit value: " + process.exitValue());
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Failed to create token account, error: " + error);
            throw new RuntimeException("Failed to create token account: " + error);
        }
        
        // Extract account address from output
        String accountAddress = output.split("Creating account ")[1].split("\n")[0].trim();
        System.out.println("DEBUG: Associated token account created: " + accountAddress);
        System.out.println("DEBUG: Exiting createAssociatedTokenAccount with accountAddress: " + accountAddress);
        return accountAddress;
    }

    /**
     * Mints tokens to an account
     * @param walletKeyPath Path to the wallet keypair file
     * @param tokenAddress Token mint address
     * @param amount Amount to mint
     * @return The token account address where tokens were minted
     */
    public String mintTokens(String walletKeyPath, String tokenAddress, double amount) throws Exception {
        System.out.println("DEBUG: Entering mintTokens with walletKeyPath: " + walletKeyPath 
                + ", tokenAddress: " + tokenAddress 
                + ", amount: " + amount);
        
        // First create the associated token account
        String tokenAccount = createAssociatedTokenAccount(walletKeyPath, tokenAddress);
        System.out.println("DEBUG: Associated token account obtained: " + tokenAccount);
        
        Thread.sleep(2000);

        ProcessBuilder mintTokens = new ProcessBuilder(
            "spl-token",
            "mint",
            tokenAddress,
            String.valueOf((long) amount),  // Convert double to long
            tokenAccount,  // This is the recipient token account
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--mint-authority", walletKeyPath,  // Add mint authority parameter
            "--program-id", "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
        );
        System.out.println("DEBUG: Mint tokens ProcessBuilder command: " + mintTokens.command());
        
        Process process = mintTokens.start();
        System.out.println("DEBUG: Mint tokens process started.");
        String output = new String(process.getInputStream().readAllBytes());
        String error = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Mint tokens process output: " + output);
        System.out.println("DEBUG: Mint tokens process error: " + error);
        
        process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Mint tokens process finished. Exit value: " + process.exitValue());
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Mint tokens process failed with error: " + error);
            throw new RuntimeException("Failed to mint tokens: " + error);
        }

        System.out.println("MINTEDDDD");
        System.out.println("DEBUG: Minting tokens successful. Tokens minted to account: " + tokenAccount);
        System.out.println("DEBUG: Exiting mintTokens with tokenAccount: " + tokenAccount);
        return tokenAccount;
    }
    
    /**
     * Revokes mint authority to prevent further token minting.
     * 
     * <p>
     * This command uses the provided wallet keypair file (which must match the current
     * mint authority stored on-chain) in both the fee-payer parameter and as the current signer.
     * By providing "null" as the authority address, the mint authority is effectively revoked.
     * </p>
     * 
     * @param walletKeyPath Path to the wallet keypair file (must be the current mint authority)
     * @param tokenAddress  Token mint address
     * @throws Exception if the CLI command fails
     */
    public void revokeMintAuthority(String walletKeyPath, String tokenAddress) throws Exception {
        System.out.println("DEBUG: Entering revokeMintAuthority with walletKeyPath: " + walletKeyPath 
                + " and tokenAddress: " + tokenAddress);
        
        // Add retry mechanism for getting token info
        String tokenInfo = getTokenInfo(tokenAddress, 3); // Try up to 3 times
        System.out.println("DEBUG: Token info retrieved: " + tokenInfo);
        System.out.println("Token info: " + tokenInfo);
        
        // Extract the current mint authority
        String currentMintAuthority = extractMintAuthority(tokenInfo);
        System.out.println("DEBUG: Extracted current mint authority: " + currentMintAuthority);
        
        // Get the public key from the wallet keypair file
        ProcessBuilder getAddress = new ProcessBuilder("solana-keygen", "pubkey", walletKeyPath);
        System.out.println("DEBUG: Getting wallet public key with command: " + getAddress.command());
        Process addressProcess = getAddress.start();
        String walletAddress = new String(addressProcess.getInputStream().readAllBytes()).trim();
        System.out.println("DEBUG: Wallet public key retrieved: " + walletAddress);
        
        if (currentMintAuthority == null) {
            System.out.println("DEBUG: Mint authority is already disabled.");
            throw new RuntimeException("Mint authority is already disabled");
        }
        
        if (!currentMintAuthority.equals(walletAddress)) {
            System.out.println("DEBUG: Wallet key does not match the current mint authority. Expected: " + currentMintAuthority + ", Got: " + walletAddress);
            throw new RuntimeException("Wallet key does not match the current mint authority. Expected: " +
                                         currentMintAuthority + ", Got: " + walletAddress);
        }

        System.out.println("DEBUG: Revoking mint authority for token: " + tokenAddress + " using keypair: " + walletKeyPath);
        System.out.println("Wallet key path: " + walletKeyPath);
        ProcessBuilder revokeAuth = new ProcessBuilder(
            "spl-token",
            "authorize",
            tokenAddress,
            "mint",
            "--disable",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath,
            "--owner", walletKeyPath  // Add owner parameter
        );
        System.out.println("DEBUG: Revoke mint authority ProcessBuilder command: " + revokeAuth.command());

        Process process = revokeAuth.start();
        System.out.println("DEBUG: Revoke mint authority process started.");
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Revoke mint authority process finished: finished " + finished + ", exit value " + process.exitValue());
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Revoke mint authority STDOUT: " + stdout);
        System.out.println("DEBUG: Revoke mint authority STDERR: " + stderr);
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Failed to revoke mint authority. Error: " + stderr);
            throw new RuntimeException("Failed to revoke mint authority: " + stderr);
        }
        System.out.println("DEBUG: Exiting revokeMintAuthority.");
    }
    
    /**
     * Revokes freeze authority to disable the ability to freeze token accounts.
     *
     * <p>
     * This command uses the provided wallet keypair file (which must match the current freeze
     * authority stored on-chain) in both the fee-payer parameter and as the current signer.
     * By providing "null" as the new freeze authority, the authority is revoked.
     * </p>
     * 
     * @param walletKeyPath Path to the wallet keypair file (must be the current freeze authority)
     * @param tokenAddress  Token mint address
     * @throws Exception if the CLI command fails
     */
    public void revokeFreezeAuthority(String walletKeyPath, String tokenAddress) throws Exception {
        System.out.println("DEBUG: Entering revokeFreezeAuthority with walletKeyPath: " + walletKeyPath 
                + " and tokenAddress: " + tokenAddress);
        
        // Add retry mechanism for getting token info
        String tokenInfo = getTokenInfo(tokenAddress, 3); // Try up to 3 times
        System.out.println("DEBUG: Token info retrieved: " + tokenInfo);
        System.out.println("Token info: " + tokenInfo);
        
        // Extract the current freeze authority
        String currentFreezeAuthority = extractFreezeAuthority(tokenInfo);
        System.out.println("DEBUG: Extracted current freeze authority: " + currentFreezeAuthority);
        
        // If freeze authority is already disabled, that's good! Just return
        if (currentFreezeAuthority == null) {
            System.out.println("DEBUG: Freeze authority is already disabled - this is what we want!");
            return;  // This is a success case, not an error!
        }
        
        // Get the public key from the wallet keypair file
        ProcessBuilder getAddress = new ProcessBuilder("solana-keygen", "pubkey", walletKeyPath);
        System.out.println("DEBUG: Getting wallet public key with command: " + getAddress.command());
        Process addressProcess = getAddress.start();
        String walletAddress = new String(addressProcess.getInputStream().readAllBytes()).trim();
        System.out.println("DEBUG: Wallet public key retrieved: " + walletAddress);
        
        if (!currentFreezeAuthority.equals(walletAddress)) {
            System.out.println("DEBUG: Wallet key does not match the current freeze authority. Expected: " + currentFreezeAuthority + ", Got: " + walletAddress);
            throw new RuntimeException("Wallet key does not match the current freeze authority. Expected: " +
                                         currentFreezeAuthority + ", Got: " + walletAddress);
        }

        System.out.println("DEBUG: Revoking freeze authority for token: " + tokenAddress + " using keypair: " + walletKeyPath);
        System.out.println("Wallet key path: " + walletKeyPath);
        ProcessBuilder revokeAuth = new ProcessBuilder(
            "spl-token",
            "authorize",
            tokenAddress,
            "freeze",
            "--disable",
            "--url", clusterUrl,
            "--fee-payer", walletKeyPath
        );
        System.out.println("DEBUG: Revoke freeze authority ProcessBuilder command: " + revokeAuth.command());

        Process process = revokeAuth.start();
        System.out.println("DEBUG: Revoke freeze authority process started.");
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Revoke freeze authority process finished: finished " + finished + ", exit value " + process.exitValue());
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Revoke freeze authority STDOUT: " + stdout);
        System.out.println("DEBUG: Revoke freeze authority STDERR: " + stderr);
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Failed to revoke freeze authority. Error: " + stderr);
            throw new RuntimeException("Failed to revoke freeze authority: " + stderr);
        }
        System.out.println("DEBUG: Exiting revokeFreezeAuthority.");
    }
    
    /**
     * Saves a wallet keypair to a temporary file
     * @param privateKey Base58 encoded private key
     * @return Path to the saved keypair file
     */
    public String saveWalletKeypair(String privateKey) throws Exception {
        System.out.println("DEBUG: Entering saveWalletKeypair with provided privateKey: " + privateKey);
        System.out.println("Original private key: " + privateKey);
        
        // Create temporary directory if it doesn't exist
        Path tempDir = Paths.get(solanaHomeDir, "temp");
        System.out.println("DEBUG: Temporary directory for keypair: " + tempDir);
        Files.createDirectories(tempDir);
        
        // Create temporary file for keypair
        String filename = UUID.randomUUID().toString() + ".json";
        Path keypairPath = tempDir.resolve(filename);
        System.out.println("DEBUG: Generated keypair filename: " + filename);
        
        // Convert Base58 to byte array (this is the secret key)
        byte[] secretKey = Base58.decode(privateKey);
        System.out.println("DEBUG: Decoded key bytes length: " + secretKey.length);
        
        if (secretKey.length != 64) {
            System.out.println("DEBUG: Invalid secret key length: " + secretKey.length + " (expected 64)");
            throw new RuntimeException("Invalid secret key length: " + secretKey.length + " (expected 64)");
        }
        
        // Convert bytes to unsigned values and create JSON array
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < secretKey.length; i++) {
            int unsignedByte = secretKey[i] & 0xFF;
            jsonArray.append(unsignedByte);
            if (i < secretKey.length - 1) {
                jsonArray.append(",");
            }
        }
        jsonArray.append("]");
        
        String jsonContent = jsonArray.toString();
        System.out.println("DEBUG: Constructed keypair JSON content: " + jsonContent);
        System.out.println("Writing keypair JSON: " + jsonContent);
        
        // Write keypair to file
        Files.write(keypairPath, jsonContent.getBytes());
        System.out.println("DEBUG: Keypair JSON written to file: " + keypairPath);
        Files.setPosixFilePermissions(keypairPath, PosixFilePermissions.fromString("rw-------"));
        System.out.println("DEBUG: Set file permissions for keypair: " + keypairPath);
        
        System.out.println("Created keypair file at: " + keypairPath);
        System.out.println("DEBUG: Keypair saved successfully at: " + keypairPath);
        System.out.println("DEBUG: Exiting saveWalletKeypair with file path: " + keypairPath);
        return keypairPath.toString();
    }
    
    /**
     * Deletes a temporary wallet keypair file
     * @param keypairPath Path to the keypair file
     */
    public void deleteWalletKeypair(String keypairPath) {
        System.out.println("DEBUG: Entering deleteWalletKeypair with keypairPath: " + keypairPath);
        try {
            boolean existsBefore = Files.exists(Paths.get(keypairPath));
            System.out.println("DEBUG: Keypair file exists before deletion: " + existsBefore);
            Files.deleteIfExists(Paths.get(keypairPath));
            boolean existsAfter = Files.exists(Paths.get(keypairPath));
            System.out.println("DEBUG: Keypair file exists after deletion: " + existsAfter);
        } catch (Exception e) {
            System.out.println("DEBUG: Failed to delete keypair file: " + e.getMessage());
        }
        System.out.println("DEBUG: Exiting deleteWalletKeypair.");
    }

    /**
     * Grinds a token address based on a given prefix
     * @param prefix The prefix to grind
     * @return The generated token address
     */
    private String grindTokenAddress(String prefix) throws Exception {
        System.out.println("DEBUG: Entering grindTokenAddress with prefix: " + prefix);
        ProcessBuilder grindAddress = new ProcessBuilder(
            "solana-keygen", 
            "grind", 
            "--starts-with", prefix + ":1"
        );
        System.out.println("DEBUG: Grind token address ProcessBuilder command: " + grindAddress.command());
        
        Process process = grindAddress.start();
        System.out.println("DEBUG: Grind token address process started.");
        String output = new String(process.getInputStream().readAllBytes());
        String error = new String(process.getErrorStream().readAllBytes());
        System.out.println("DEBUG: Grind token address process output: " + output);
        System.out.println("DEBUG: Grind token address process error: " + error);
        
        process.waitFor(30, TimeUnit.SECONDS);
        System.out.println("DEBUG: Grind token address process finished. Exit value: " + process.exitValue());
        
        if (process.exitValue() != 0) {
            System.out.println("DEBUG: Failed to grind token address. Error: " + error);
            throw new RuntimeException("Failed to grind token address: " + error);
        }
        
        String publicKey = output.split(" ")[0];
        System.out.println("DEBUG: Grinded token address (public key): " + publicKey);
        System.out.println("DEBUG: Exiting grindTokenAddress with publicKey: " + publicKey);
        return publicKey;
    }

    private String extractMintAuthority(String tokenInfo) {
        System.out.println("DEBUG: Entering extractMintAuthority with tokenInfo: " + tokenInfo);
        try {
            String[] lines = tokenInfo.split("\n");
            for (String line : lines) {
                // Add debug to see exact line content
                System.out.println("DEBUG: Checking line: '" + line + "'");
                
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("Mint authority:")) {
                    String authority = trimmedLine.substring("Mint authority:".length()).trim();
                    System.out.println("DEBUG: Found Mint authority line. Raw authority: '" + authority + "'");
                    
                    if (authority.equals("(not set)") || authority.equals("disabled")) {
                        System.out.println("DEBUG: Mint authority is disabled.");
                        return null;
                    }
                    
                    System.out.println("DEBUG: Exiting extractMintAuthority with authority: '" + authority + "'");
                    return authority;
                }
            }
            System.out.println("DEBUG: Could not find Mint authority in token info: " + tokenInfo);
            return null;
        } catch (Exception e) {
            System.out.println("DEBUG: Error parsing token info for mint authority: " + e.getMessage());
            e.printStackTrace(); // Add stack trace for better debugging
            return null;
        }
    }

    private String extractFreezeAuthority(String tokenInfo) {
        System.out.println("DEBUG: Entering extractFreezeAuthority with tokenInfo: " + tokenInfo);
        try {
            String[] lines = tokenInfo.split("\n");
            for (String line : lines) {
                // Add debug to see exact line content
                System.out.println("DEBUG: Checking line: '" + line + "'");
                
                String trimmedLine = line.trim();
                if (trimmedLine.startsWith("Freeze authority:")) {
                    String authority = trimmedLine.substring("Freeze authority:".length()).trim();
                    System.out.println("DEBUG: Found Freeze authority line. Raw authority: '" + authority + "'");
                    
                    if (authority.equals("(not set)") || authority.equals("disabled")) {
                        System.out.println("DEBUG: Freeze authority is disabled.");
                        return null;
                    }
                    
                    System.out.println("DEBUG: Exiting extractFreezeAuthority with authority: '" + authority + "'");
                    return authority;
                }
            }
            System.out.println("DEBUG: Could not find Freeze authority in token info: " + tokenInfo);
            return null;
        } catch (Exception e) {
            System.out.println("DEBUG: Error parsing token info for freeze authority: " + e.getMessage());
            e.printStackTrace(); // Add stack trace for better debugging
            return null;
        }
    }

    private String getTokenInfo(String tokenAddress, int maxRetries) throws Exception {
        System.out.println("DEBUG: Entering getTokenInfo for tokenAddress: " + tokenAddress + " with maxRetries: " + maxRetries);
        for (int i = 0; i < maxRetries; i++) {
            System.out.println("DEBUG: Attempt " + (i + 1) + " to retrieve token info.");
            ProcessBuilder checkAuth = new ProcessBuilder(
                "spl-token", "display", tokenAddress, "--url", clusterUrl
            );
            System.out.println("DEBUG: Get token info ProcessBuilder command: " + checkAuth.command());
            Process checkProcess = checkAuth.start();
            String tokenInfo = new String(checkProcess.getInputStream().readAllBytes());
            System.out.println("DEBUG: Attempt " + (i + 1) + " token info output: " + tokenInfo);
            
            if (!tokenInfo.trim().isEmpty()) {
                System.out.println("DEBUG: Token info successfully retrieved on attempt " + (i + 1) + ".");
                return tokenInfo;
            }
            
            System.out.println("DEBUG: Token info empty on attempt " + (i + 1) + ". Retrying after delay.");
            Thread.sleep(2000 * (i + 1));
        }
        System.out.println("DEBUG: Unable to get token info after " + maxRetries + " attempts for tokenAddress: " + tokenAddress);
        throw new RuntimeException("Unable to get token info after " + maxRetries + " attempts");
    }

    /**
     * Gets the public key for a given wallet keypair file.
     * @param walletKeyPath Path to the wallet keypair file
     * @return The public key (in Base58 string)
     */
    private String getWalletPublicKey(String walletKeyPath) throws Exception {
        System.out.println("DEBUG: Entering getWalletPublicKey with walletKeyPath: " + walletKeyPath);
        ProcessBuilder getPubKey = new ProcessBuilder("solana-keygen", "pubkey", walletKeyPath);
        System.out.println("DEBUG: Get wallet public key ProcessBuilder command: " + getPubKey.command());
        Process process = getPubKey.start();
        String walletPubKey = new String(process.getInputStream().readAllBytes()).trim();
        System.out.println("DEBUG: Retrieved wallet public key: " + walletPubKey);
        return walletPubKey;
    }
}
