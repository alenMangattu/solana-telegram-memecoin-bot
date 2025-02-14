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

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
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
     * Creates a new encryption key version for a wallet.
     * 
     * By setting propagation to MANDATORY we ensure this method 
     * runs within an already present (outer) transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
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
            
            // Update metadata: deactivate previous active key
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
        log.info("Retrieving private key for wallet: {}", walletId);
        
        EncryptionKeyMetadata metadata = keyMetadataRepository.findByWalletIdAndActive(walletId, true)
            .orElseThrow(() -> new Exception("No active encryption key found for wallet: " + walletId));
        
        log.info("Active encryption key version: {}", metadata.getVersion());

        // Try versioned files first
        String walletFileName = getWalletFileName(walletId, metadata.getVersion());
        String keyFileName = getKeyFileName(walletId, metadata.getVersion());
        Path walletPath = Paths.get(walletsPath, walletFileName);
        Path keyPath = Paths.get(encryptionKeysPath, keyFileName);
        
        // If version 1 and versioned files don't exist, try legacy unversioned files
        if (metadata.getVersion() == 1 && (!Files.exists(walletPath) || !Files.exists(keyPath))) {
            log.info("Trying legacy unversioned files for wallet: {}", walletId);
            walletFileName = String.format("%s.encrypted", walletId.toString());
            keyFileName = String.format("%s.key", walletId.toString());
            walletPath = Paths.get(walletsPath, walletFileName);
            keyPath = Paths.get(encryptionKeysPath, keyFileName);
        }

        if (!Files.exists(walletPath)) {
            throw new RuntimeException("Wallet file not found: " + walletPath);
        }
        if (!Files.exists(keyPath)) {
            throw new RuntimeException("Encryption key file not found: " + keyPath);
        }

        String encryptedPrivateKey = new String(Files.readAllBytes(walletPath));
        SecretKey encryptionKey = EncryptionUtils.deserializeKey(new String(Files.readAllBytes(keyPath)));
        
        log.info("Using key file: {} for wallet file: {}", keyPath, walletPath);
        return EncryptionUtils.decrypt(encryptedPrivateKey, encryptionKey);
    }

    /**
     * Checks if a wallet's private key exists and is accessible
     */
    public boolean hasPrivateKey(UUID walletId) {
        Optional<EncryptionKeyMetadata> metadataOpt = keyMetadataRepository.findByWalletIdAndActive(walletId, true);
        if (metadataOpt.isPresent()) {
            EncryptionKeyMetadata metadata = metadataOpt.get();
            Path encryptionKeyPath = Paths.get(encryptionKeysPath, getKeyFileName(walletId, metadata.getVersion()));
            Path walletPath = Paths.get(walletsPath, getWalletFileName(walletId, metadata.getVersion()));
            return Files.exists(encryptionKeyPath) && Files.exists(walletPath);
        }
        return false;
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
    @Transactional(propagation = Propagation.MANDATORY)
    public void storePrivateKey(UUID walletId, String privateKey) throws Exception {
        log.info("Storing private key for wallet: {}", walletId);
        
        // Generate new encryption key
        SecretKey encryptionKey = EncryptionUtils.generateEncryptionKey();
        log.info("Generated new encryption key for wallet: {}", walletId);
        
        // Use version 1 for the initial storage
        int version = 1;
        log.debug("Using version {} for storing private key", version);
        
        // Store encryption key with versioned file name
        String keyFileName = getKeyFileName(walletId, version);
        Path keyPath = Paths.get(encryptionKeysPath, keyFileName);
        log.debug("Key file path: {}", keyPath);
        
        Files.createDirectories(keyPath.getParent());
        Files.write(keyPath, EncryptionUtils.serializeKey(encryptionKey).getBytes());
        Files.setPosixFilePermissions(keyPath, PosixFilePermissions.fromString("rw-------"));
        log.info("Stored encryption key at: {}", keyPath);
        
        // Encrypt and store private key using the versioned file name
        String encryptedPrivateKey = EncryptionUtils.encrypt(privateKey, encryptionKey);
        String walletFileName = getWalletFileName(walletId, version);
        Path walletPath = Paths.get(walletsPath, walletFileName);
        log.debug("Wallet file path: {}", walletPath);
        
        Files.createDirectories(walletPath.getParent());
        Files.write(walletPath, encryptedPrivateKey.getBytes());
        Files.setPosixFilePermissions(walletPath, PosixFilePermissions.fromString("rw-------"));
        log.info("Stored encrypted private key at: {}", walletPath);
        
        log.info("Successfully stored private key and encryption key for wallet ID: {}", walletId);

        // Check if an active encryption key metadata record exists
        if (!keyMetadataRepository.findByWalletIdAndActive(walletId, true).isPresent()) {
            // No active record found—check if there is an existing (inactive) record
            Optional<EncryptionKeyMetadata> latestMetaOpt =
                keyMetadataRepository.findFirstByWalletIdOrderByVersionDesc(walletId);
            if (latestMetaOpt.isPresent()) {
                EncryptionKeyMetadata latestMeta = latestMetaOpt.get();
                // Reactivate the existing record
                latestMeta.setActive(true);
                keyMetadataRepository.saveAndFlush(latestMeta);
                log.info("Reactivated previously inactive encryption key metadata record for wallet: {}", walletId);
            } else {
                // Create a new active record if none exists at all
                EncryptionKeyMetadata metadata = new EncryptionKeyMetadata();
                metadata.setWalletId(walletId);
                metadata.setVersion(version);
                metadata.setCreatedAt(LocalDateTime.now());
                metadata.setExpiresAt(LocalDateTime.now().plusDays(keyRotationDays));
                metadata.setActive(true);
                keyMetadataRepository.saveAndFlush(metadata);
                log.info("Created new encryption key metadata record for wallet: {}", walletId);
            }
        } else {
            log.info("Active encryption key metadata record already exists for wallet: {}", walletId);
        }
    }
}
