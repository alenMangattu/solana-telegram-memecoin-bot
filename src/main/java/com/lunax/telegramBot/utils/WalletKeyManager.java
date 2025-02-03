package com.lunax.telegramBot.utils;

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

@Component
public class WalletKeyManager {
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;
   
    @Value("${wallets.path}")
    private String walletsPath;
    
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
        Files.write(keyPath, EncryptionUtils.serializeKey(key).getBytes());
        Files.setPosixFilePermissions(keyPath, 
            PosixFilePermissions.fromString("rw-------"));
    }

    private void saveEncryptedPrivateKey(UUID walletId, int version, String encryptedKey) throws Exception {
        Path walletPath = Paths.get(walletsPath, getWalletFileName(walletId, version));
        Files.write(walletPath, encryptedKey.getBytes());
        Files.setPosixFilePermissions(walletPath, 
            PosixFilePermissions.fromString("rw-------"));
    }

    /**
     * Retrieves the private key for a given wallet ID using the active key version
     */
    public String getPrivateKey(UUID walletId) throws Exception {
        EncryptionKeyMetadata metadata = keyMetadataRepository.findByWalletIdAndActive(walletId, true)
            .orElseThrow(() -> new Exception("No active encryption key found for wallet: " + walletId));

        // Read the encryption key
        Path encryptionKeyPath = Paths.get(encryptionKeysPath, 
            getKeyFileName(walletId, metadata.getVersion()));
        String serializedKey = new String(Files.readAllBytes(encryptionKeyPath));
        SecretKey encryptionKey = EncryptionUtils.deserializeKey(serializedKey);
        
        // Read the encrypted private key
        Path walletPath = Paths.get(walletsPath, 
            getWalletFileName(walletId, metadata.getVersion()));
        String encryptedPrivateKey = new String(Files.readAllBytes(walletPath));
        
        return EncryptionUtils.decrypt(encryptedPrivateKey, encryptionKey);
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
}
