package com.lunax.telegramBot.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.bitcoinj.core.Base58;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import com.mmorrell.metaplex.manager.MetaplexManager;
import com.mmorrell.metaplex.model.Metadata;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.RpcClient;
import org.p2p.solanaj.core.Transaction;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Component
public class SolanaCliUtils {
    
    @Value("${solana.home.dir:${user.home}/.config/solana}")
    private String solanaHomeDir;

    @Value("${solana.cluster.url}")
    private String clusterUrl;
    
    @Value("${token.list.url:https://cdn.jsdelivr.net/gh/solana-labs/token-list@main/src/tokens/solana.tokenlist.json}")
    private String tokenListUrl;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private JsonNode tokenList;
    private long lastTokenListUpdate = 0;
    private static final long TOKEN_LIST_CACHE_DURATION = 3600000; // 1 hour in milliseconds

    private static final String TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final String METADATA_PROGRAM_ID = "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s";

    private final RpcClient rpcClient;
    private final MetaplexManager metaplexManager;

    private static final String[] BACKUP_RPC_URLS = {
        "https://api.devnet.solana.com",
        "https://devnet.solana.com",
    };
    private static final int MAX_RETRIES = 3;

    public SolanaCliUtils(@Value("${solana.cluster.url}") String clusterUrl) {
        this.rpcClient = new RpcClient(clusterUrl);
        this.metaplexManager = new MetaplexManager(rpcClient);
    }

    /**
     * Creates a new memecoin using the Solana CLI with the standard Token program and Metaplex metadata
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
        System.out.println("DEBUG: Creating memecoin with keypair: " + walletKeyPath);
        
        // Get the wallet's public key first
        String walletPublicKey = getWalletPublicKey(walletKeyPath);
        System.out.println("DEBUG: Retrieved wallet public key: " + walletPublicKey);
        
        // Create the token using the standard Token program
        String tokenAddress = createTokenWithRetry(walletKeyPath, walletPublicKey, decimals);
        System.out.println("DEBUG: Token created with address: " + tokenAddress);
        
        // Wait for transaction confirmation
        Thread.sleep(2000);
        
        // Create metadata using Metaplex library
        try {
            PublicKey mintKey = new PublicKey(tokenAddress);
            System.out.println("DEBUG: Mint key created: " + mintKey);
            
            Metadata metadata = Metadata.builder()
                .updateAuthority(new PublicKey(walletPublicKey))
                .tokenMint(mintKey)
                .name(name)
                .symbol(symbol)
                .uri(metadataUri)
                .build();
            
            System.out.println("DEBUG: Metadata object created: " + metadata);
            
            // Get metadata PDA
            PublicKey.ProgramDerivedAddress metadataPda = PublicKey.findProgramAddress(
                Arrays.asList(
                    "metadata".getBytes(StandardCharsets.UTF_8),
                    new PublicKey(METADATA_PROGRAM_ID).toByteArray(),
                    mintKey.toByteArray()
                ),
                new PublicKey(METADATA_PROGRAM_ID)
            );
            
            System.out.println("DEBUG: Metadata PDA: " + metadataPda.getAddress());
            
            // Create metadata transaction
            Transaction transaction = new Transaction();
            transaction.addInstruction(
                MetadataInstruction.createMetadataAccount(
                    metadataPda.getAddress(),
                    mintKey,
                    new PublicKey(walletPublicKey),
                    new PublicKey(walletPublicKey),
                    new PublicKey(walletPublicKey),
                    metadata,
                    true
                )
            );
            
            // No need to verify metadata since CLI command handles it
            return tokenAddress;
            
        } catch (Exception e) {
            System.out.println("DEBUG: Exception occurred: " + e.getMessage());
            throw new RuntimeException("Failed to create metadata: " + e.getMessage(), e);
        }
    }

    private String createTokenWithRetry(String walletKeyPath, String walletPublicKey, int decimals) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            for (String rpcUrl : BACKUP_RPC_URLS) {
                try {
                    System.out.println("DEBUG: Attempting to create token using RPC URL: " + rpcUrl + " (Attempt " + (attempt + 1) + ")");
                    
                    ProcessBuilder createToken = new ProcessBuilder(
                        "spl-token", 
                        "create-token",
                        "--decimals", String.valueOf(decimals),
                        "--url", rpcUrl,
                        "--fee-payer", walletKeyPath,
                        "--mint-authority", walletPublicKey
                    );
                    
                    Process process = createToken.start();
                    String output = new String(process.getInputStream().readAllBytes());
                    String error = new String(process.getErrorStream().readAllBytes());
                    
                    if (!process.waitFor(30, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        continue; // Try next RPC URL
                    }
                    
                    if (process.exitValue() == 0) {
                        String tokenAddress = output.split("Creating token ")[1].split(" under")[0].trim();
                        System.out.println("DEBUG: Successfully created token with address: " + tokenAddress);
                        return tokenAddress;
                    }
                    
                    if (error.contains("Node is unhealthy")) {
                        System.out.println("DEBUG: Node " + rpcUrl + " is unhealthy, trying next...");
                        continue;
                    }
                    
                    throw new RuntimeException("Failed to create token: " + error);
                } catch (Exception e) {
                    lastException = e;
                    System.out.println("DEBUG: Error with RPC URL " + rpcUrl + ": " + e.getMessage());
                }
            }
            
            // Wait before retrying all RPCs
            if (attempt < MAX_RETRIES - 1) {
                Thread.sleep(2000);
            }
        }
        
        throw new RuntimeException("Failed to create token after all retries", lastException);
    }

    /**
     * Helper method to create metadata JSON for Metaplex
     */
    // private String createMetadataJson(String name, String symbol, String uri) {
    //     try {
    //         ObjectNode metadata = objectMapper.createObjectNode();
    //         metadata.put("name", name);
    //         metadata.put("symbol", symbol);
    //         metadata.put("uri", uri);
    //         metadata.put("sellerFeeBasisPoints", 0);
    //         metadata.put("creators", objectMapper.createArrayNode()); // Changed null to an empty array
    //         metadata.put("collection", objectMapper.createObjectNode()); // Changed null to an empty object
    //         metadata.put("uses", objectMapper.createObjectNode()); // Changed null to an empty object
            
    //         return metadata.toString();
    //     } catch (Exception e) {
    //         throw new RuntimeException("Failed to create metadata JSON: " + e.getMessage());
    //     }
    // }
    
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
            "--program-id",  TOKEN_PROGRAM_ID// Add program-id parameter
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
            "--mint-authority", walletKeyPath  // Add mint authority parameter
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

    /**
     * Resolves a token symbol to its address using the Solana token list
     * @param symbol The token symbol to resolve
     * @return The token's address
     */
    public String resolveTokenSymbol(String symbol) throws Exception {
        // Update token list if needed
        updateTokenListIfNeeded();
        
        // Search for token by symbol
        if (tokenList != null && tokenList.has("tokens")) {
            for (JsonNode token : tokenList.get("tokens")) {
                if (token.get("symbol").asText().equalsIgnoreCase(symbol)) {
                    return token.get("address").asText();
                }
            }
        }
       
        throw new RuntimeException("Token symbol not found: " + symbol);
    }

    private void updateTokenListIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (tokenList == null || (currentTime - lastTokenListUpdate) > TOKEN_LIST_CACHE_DURATION) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(tokenListUrl, String.class);
                tokenList = objectMapper.readTree(response.getBody());
                lastTokenListUpdate = currentTime;
                log.info("Token list updated successfully");
            } catch (Exception e) {
                log.error("Error updating token list: {}", e.getMessage());
                // Keep using old list if update fails
            }
        }
    }

    /**
     * Gets detailed metadata for a token including off-chain data
     */
    public JsonNode getTokenMetadata(String tokenAddress) throws Exception {
        // Get on-chain metadata
        JsonNode onChainData = getOnChainTokenData(tokenAddress);
        
        // Try to get additional metadata from token list
        updateTokenListIfNeeded();
        JsonNode tokenListData = findTokenInList(tokenAddress);
        
        // Combine the data
        ObjectNode fullMetadata = objectMapper.createObjectNode();
        if (onChainData != null) {
            fullMetadata.setAll((ObjectNode) onChainData);
        }
        if (tokenListData != null) {
            // Add additional fields from token list without overwriting on-chain data
            tokenListData.fields().forEachRemaining(entry -> {
                if (!fullMetadata.has(entry.getKey())) {
                    fullMetadata.set(entry.getKey(), entry.getValue());
                }
            });
        } else {
            log.warn("Token list data is null for token address: {}", tokenAddress);
        }
        
        return fullMetadata;
    }

    private JsonNode getOnChainTokenData(String tokenAddress) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "spl-token",
            "display",
            tokenAddress,
            "--output", "json",
            "--url", clusterUrl
        );

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        
        if (process.waitFor() != 0) {
            throw new RuntimeException("Failed to get token metadata: " + 
                new String(process.getErrorStream().readAllBytes()));
        }

        return objectMapper.readTree(output);
    }

    private JsonNode findTokenInList(String tokenAddress) {
        if (tokenList != null && tokenList.has("tokens")) {
            for (JsonNode token : tokenList.get("tokens")) {
                if (token.get("address").asText().equals(tokenAddress)) {
                    return token;
                }
            }
        }
        return null;
    }

    /**
     * Get token account information for a specific user and token
     * @param tokenMint The token mint address
     * @param walletAddress The wallet address
     * @return JsonNode containing token account information or null if not found
     */
    public JsonNode getUserTokenAccount(String tokenMint, String walletAddress) {
        try {
            log.info("Fetching token account info for token: {} and user: {}", tokenMint, walletAddress);
            
            // First, get all token accounts for this wallet
            String command = String.format("spl-token accounts --owner %s --output json", walletAddress);
            
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Parse the JSON output
                String jsonString = output.toString().trim();
                JsonNode accountsNode = objectMapper.readTree(jsonString);
                
                // Find the account for the specific mint
                if (accountsNode.has("accounts") && accountsNode.get("accounts").isArray()) {
                    for (JsonNode account : accountsNode.get("accounts")) {
                        if (account.has("mint") && account.get("mint").asText().equals(tokenMint)) {
                            return account;
                        }
                    }
                }
                
                // If we didn't find a specific account for this mint, return the whole list
                // so the caller can use it for other purposes
                return accountsNode;
            } else {
                log.error("Error getting user token account. Exit code: {}, Output: {}", exitCode, output);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get user token account: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get all token accounts for a wallet
     * @param walletAddress The wallet address
     * @return JsonNode containing all token accounts information
     */
    public JsonNode getAllTokenAccounts(String walletAddress) {
        try {
            // Get all token accounts for this wallet
            String command = String.format("spl-token accounts --owner %s --output json", walletAddress);
            
            ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                // Parse the JSON output
                String jsonString = output.toString().trim();
                return objectMapper.readTree(jsonString);
            } else {
                log.error("Error getting all token accounts. Exit code: {}, Output: {}", exitCode, output);
                return objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            log.error("Failed to get all token accounts: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    @Scheduled(fixedRate = 30000) // 30000 milliseconds = 30 seconds
    @CacheEvict(value = "userTokenAccounts", allEntries = true)
    public void evictUserTokenAccountCache() {
        log.debug("Evicting user token account cache");
    }

    /**
     * Gets all token addresses for a wallet
     * @param walletAddress The wallet address to check
     * @return JsonNode containing all token addresses
     */
    public JsonNode getWalletTokenAccounts(String walletAddress) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(
            "spl-token",
            "accounts",
            "--owner", walletAddress,
            "--output", "json",
            "--url", clusterUrl
        );

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());

        if (process.waitFor() != 0) {
            throw new RuntimeException("Failed to get wallet token accounts: " + 
                new String(process.getErrorStream().readAllBytes()));
        }

        JsonNode accountsNode = objectMapper.readTree(output);
        ArrayNode tokenAddresses = objectMapper.createArrayNode();

        if (accountsNode != null && accountsNode.isArray()) {
            for (JsonNode account : accountsNode) {
                if (account.has("address")) {
                    tokenAddresses.add(account.get("address").asText());
                }
            }
        }

        return tokenAddresses;
    }

    /**
     * Gets the token balance for a wallet's token account
     *
     * @param walletAddress The wallet address to check
     * @param tokenAddress The token address to check balance for
     * @return The token balance as a string
     * @throws Exception If there is an error retrieving the balance
     */
    public String getTokenBalance(String walletAddress, String tokenAddress) throws Exception {
        // Get the user token account details first
        JsonNode accountInfo = getUserTokenAccount(tokenAddress, walletAddress);
        
        if (accountInfo != null && accountInfo.has("tokenAmount")) {
            JsonNode tokenAmount = accountInfo.get("tokenAmount");
            if (tokenAmount.has("uiAmountString")) {
                // This is the formatted amount with proper decimal places
                return tokenAmount.get("uiAmountString").asText();
            } else if (tokenAmount.has("amount") && tokenAmount.has("decimals")) {
                // Calculate the balance manually if uiAmountString is not available
                String amountStr = tokenAmount.get("amount").asText();
                int decimals = tokenAmount.get("decimals").asInt();
                
                // Convert to double and format with proper decimals
                double rawAmount = Double.parseDouble(amountStr);
                double amount = rawAmount / Math.pow(10, decimals);
                return String.valueOf(amount);
            }
        }
        
        // Fallback - return zero if we can't get the balance
        log.warn("Unable to extract token balance from account info for token: {}", tokenAddress);
        return "0";
    }
}