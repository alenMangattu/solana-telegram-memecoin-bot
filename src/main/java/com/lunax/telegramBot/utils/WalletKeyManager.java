package com.lunax.telegramBot.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.lunax.telegramBot.model.db.EncryptionKeyMetadata;
import com.lunax.telegramBot.model.repository.EncryptionKeyMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WalletKeyManager {
    
    private static final Logger log = LoggerFactory.getLogger(WalletKeyManager.class);
    
    @Value("${wallets.path}")
    private String walletsPath;
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;
    
    @Value("${encryption.key.rotation.days}")
    private int keyRotationDays;

    private final EncryptionKeyMetadataRepository keyMetadataRepository;

    public WalletKeyManager(EncryptionKeyMetadataRepository keyMetadataRepository) {
        this.keyMetadataRepository = keyMetadataRepository;
    }

    /**
     * Creates a new encryption key version for a wallet
     */
    @Transactional
    public void createNewKeyVersion(UUID walletId) throws Exception {
        // Get the current version number
        int nextVersion = keyMetadataRepository.findFirstByWalletIdOrderByVersionDesc(walletId)
            .map(meta -> meta.getVersion() + 1)
            .orElse(1);

        // Generate new encryption key
        SecretKey newKey = EncryptionUtils.generateEncryptionKey();
        
        // If there's an existing key, we need to re-encrypt the private key
        if (hasPrivateKey(walletId)) {
            String privateKey = getPrivateKey(walletId); // Get using old key
            
            // Save new key
            saveEncryptionKey(walletId, nextVersion, newKey);
            
            // Re-encrypt private key with new key
            String newEncryptedPrivateKey = EncryptionUtils.encrypt(privateKey, newKey);
            saveEncryptedPrivateKey(walletId, nextVersion, newEncryptedPrivateKey);
            
            // Update metadata
            deactivateOldKeys(walletId);
        } else {
            // Just save the new key if this is the first time
            saveEncryptionKey(walletId, nextVersion, newKey);
        }

        // Create new key metadata
        EncryptionKeyMetadata metadata = new EncryptionKeyMetadata();
        metadata.setWalletId(walletId);
        metadata.setVersion(nextVersion);
        metadata.setCreatedAt(LocalDateTime.now());
        metadata.setExpiresAt(LocalDateTime.now().plusDays(keyRotationDays));
        metadata.setActive(true);
        keyMetadataRepository.save(metadata);
    }

    /**
     * Scheduled task to rotate keys that are about to expire
     */
    @Scheduled(cron = "0 0 0 * * *") // Run daily at midnight
    @Transactional
    public void rotateExpiredKeys() {
        LocalDateTime now = LocalDateTime.now();
        List<EncryptionKeyMetadata> expiringKeys = keyMetadataRepository.findExpiredActiveKeys(now);
        
        for (EncryptionKeyMetadata metadata : expiringKeys) {
            try {
                createNewKeyVersion(metadata.getWalletId());
            } catch (Exception e) {
                // Log error but continue with other keys
                e.printStackTrace();
            }
        }
    }

    private void deactivateOldKeys(UUID walletId) {
        keyMetadataRepository.findByWalletIdAndActive(walletId, true)
            .ifPresent(metadata -> {
                metadata.setActive(false);
                keyMetadataRepository.save(metadata);
            });
    }

    private String getKeyFileName(UUID walletId, int version) {
        return String.format("%s.v%d.key", walletId, version);
    }

    private String getWalletFileName(UUID walletId, int version) {
        return String.format("%s.v%d.encrypted", walletId, version);
    }

    private void saveEncryptionKey(UUID walletId, int version, SecretKey key) throws Exception {
        Path keyPath = Paths.get(encryptionKeysPath, getKeyFileName(walletId, version));
        // Create parent directories if they don't exist
        Files.createDirectories(keyPath.getParent());
        Files.write(keyPath, EncryptionUtils.serializeKey(key).getBytes());
        Files.setPosixFilePermissions(keyPath, 
            PosixFilePermissions.fromString("rw-------"));
    }

    private void saveEncryptedPrivateKey(UUID walletId, int version, String encryptedKey) throws Exception {
        Path walletPath = Paths.get(walletsPath, getWalletFileName(walletId, version));
        // Create parent directories if they don't exist
        Files.createDirectories(walletPath.getParent());
        Files.write(walletPath, encryptedKey.getBytes());
        Files.setPosixFilePermissions(walletPath, 
            PosixFilePermissions.fromString("rw-------"));
    }

    /**
     * Retrieves the private key for a given wallet ID using the active key version
     */
    public String getPrivateKey(UUID walletId) throws Exception {
        System.out.println("Getting private key for wallet: " + walletId);
        
        // First find the active key version
        String walletFileName = String.format("%s.encrypted", walletId.toString());
        Path walletPath = Paths.get(walletsPath, walletFileName);
        
        System.out.println("Looking for wallet file: " + walletPath);
        
        if (!Files.exists(walletPath)) {
            throw new RuntimeException("Wallet file not found: " + walletPath);
        }

        // Read the encrypted private key
        String encryptedPrivateKey = new String(Files.readAllBytes(walletPath));
        System.out.println("Read encrypted private key length: " + encryptedPrivateKey.length());
        
        // Get the encryption key
        String keyFileName = String.format("%s.key", walletId.toString());
        Path keyPath = Paths.get(encryptionKeysPath, keyFileName);
        
        System.out.println("Looking for key file: " + keyPath);
        
        if (!Files.exists(keyPath)) {
            throw new RuntimeException("Encryption key file not found: " + keyPath);
        }

        SecretKey encryptionKey = EncryptionUtils.deserializeKey(
            new String(Files.readAllBytes(keyPath))
        );
        
        // Decrypt and return the private key
        String privateKey = EncryptionUtils.decrypt(encryptedPrivateKey, encryptionKey);
        System.out.println("Decrypted private key length: " + privateKey.length());
        
        return privateKey;
    }

    /**
     * Checks if a wallet's private key exists and is accessible
     */
    public boolean hasPrivateKey(UUID walletId) {
        return keyMetadataRepository.findByWalletIdAndActive(walletId, true)
            .map(metadata -> {
                Path encryptionKeyPath = Paths.get(encryptionKeysPath, 
                    getKeyFileName(walletId, metadata.getVersion()));
                Path walletPath = Paths.get(walletsPath, 
                    getWalletFileName(walletId, metadata.getVersion()));
                return Files.exists(encryptionKeyPath) && Files.exists(walletPath);
            })
            .orElse(false);
    }

    /**
     * Deletes all versions of a wallet's keys
     */
    @Transactional
    public void deleteWalletKeys(UUID walletId) throws Exception {
        List<EncryptionKeyMetadata> allVersions = 
            keyMetadataRepository.findByWalletIdOrderByVersionDesc(walletId);
        
        for (EncryptionKeyMetadata metadata : allVersions) {
            Path encryptionKeyPath = Paths.get(encryptionKeysPath, 
                getKeyFileName(walletId, metadata.getVersion()));
            Path walletPath = Paths.get(walletsPath, 
                getWalletFileName(walletId, metadata.getVersion()));
            
            Files.deleteIfExists(encryptionKeyPath);
            Files.deleteIfExists(walletPath);
            
            keyMetadataRepository.delete(metadata);
        }
    }

    /**
     * Stores a new wallet's private key
     */
    @Transactional
    public void storePrivateKey(UUID walletId, String privateKey) throws Exception {
        // Generate new encryption key
        SecretKey encryptionKey = EncryptionUtils.generateEncryptionKey();
        
        // Store encryption key
        String keyFileName = String.format("%s.key", walletId.toString());
        Path keyPath = Paths.get(encryptionKeysPath, keyFileName);
        Files.createDirectories(keyPath.getParent());
        Files.write(keyPath, EncryptionUtils.serializeKey(encryptionKey).getBytes());
        Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
        
        // Encrypt and store private key
        String encryptedPrivateKey = EncryptionUtils.encrypt(privateKey, encryptionKey);
        String walletFileName = String.format("%s.encrypted", walletId.toString());
        Path walletPath = Paths.get(walletsPath, walletFileName);
        Files.createDirectories(walletPath.getParent());
        Files.write(walletPath, encryptedPrivateKey.getBytes());
        Files.setPosixFilePermissions(walletPath, PosixFilePermissions.fromString("rw-------"));
    }
}
