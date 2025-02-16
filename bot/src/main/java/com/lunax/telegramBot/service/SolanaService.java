package com.lunax.telegramBot.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.utils.EncryptionUtils;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SolanaService {
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private WalletRepository walletRepository;
    
    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private UserService userService;

    @Value("${encryption.key.rotation.days}")
    private int keyRotationDays;

    @Value("${wallets.path}")
    private String walletsPath;
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;

    @Transactional
    public Wallet createNewWallet(User user, String encryptionKeysPath, String walletsPath) {
        try {
            log.info("Starting wallet creation for user: {}", user.getTelegramId());
            
            // Create the new wallet without manually setting the walletId.
            Map<String, String> walletInfo = solanaUtils.createNewWallet(null);
            log.info("Created Solana wallet with public key: {}", walletInfo.get("publicKey"));
            
            Wallet wallet = new Wallet();
            wallet.setUser(user);
            wallet.setWalletAddress(walletInfo.get("publicKey")); 
            wallet.setWalletLabel("Solana Wallet");

            // Save & flush to ensure the wallet is persisted and its generated ID is available.
            wallet = walletRepository.saveAndFlush(wallet);
            UUID walletId = wallet.getWalletId();
            log.info("Saved wallet entity with generated ID: {} and address: {} to database", 
                    walletId, walletInfo.get("publicKey"));
            
            // Now store the wallet's private key along with its encryption key metadata.
            // The storePrivateKey method will check if an active record exists a create one if needed.
            walletKeyManager.storePrivateKey(walletId, walletInfo.get("privateKey"));
            log.info("Successfully stored wallet keys and created encryption metadata for wallet ID: {}", walletId);
            
            return wallet;
        } catch (Exception e) {
            log.error("Failed to create wallet for user: {}", user.getTelegramId(), e);
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    private void storeWalletKeys(Wallet wallet, String privateKey, SecretKey encryptionKey, 
                                String encryptionKeysPath, String walletsPath) throws Exception {
        try {
            log.info("Storing keys for wallet ID: {}", wallet.getWalletId());
            // Store encryption key
            String encryptionKeyFileName = wallet.getWalletId().toString() + ".key";
            Path encryptionKeyPath = Paths.get(encryptionKeysPath, encryptionKeyFileName);
            Files.createDirectories(encryptionKeyPath.getParent());
            Files.write(encryptionKeyPath, EncryptionUtils.serializeKey(encryptionKey).getBytes());
            Files.setPosixFilePermissions(encryptionKeyPath, 
                PosixFilePermissions.fromString("rw-------"));
            log.info("Stored encryption key at: {}", encryptionKeyPath);

            // Store encrypted private key
            String encryptedPrivateKey = EncryptionUtils.encrypt(privateKey, encryptionKey);
            String walletFileName = wallet.getWalletId().toString() + ".encrypted";
            Path walletPath = Paths.get(walletsPath, walletFileName);
            Files.createDirectories(walletPath.getParent());
            Files.write(walletPath, encryptedPrivateKey.getBytes());
            Files.setPosixFilePermissions(walletPath, 
                PosixFilePermissions.fromString("rw-------"));
            log.info("Stored encrypted private key at: {}", walletPath);

            // Create initial key version
            walletKeyManager.createNewKeyVersion(wallet.getWalletId());
            log.info("Created new key version for wallet ID: {}", wallet.getWalletId());
        } catch (Exception e) {
            log.error("Failed to store wallet keys for wallet ID: {}", wallet.getWalletId(), e);
            // Clean up any files that might have been created
            try {
                Files.deleteIfExists(Paths.get(encryptionKeysPath, wallet.getWalletId().toString() + ".key"));
                Files.deleteIfExists(Paths.get(walletsPath, wallet.getWalletId().toString() + ".encrypted"));
            } catch (Exception cleanupError) {
                log.error("Failed to cleanup after error for wallet ID: {}", wallet.getWalletId(), cleanupError);
            }
            throw e;
        }
    }

    @Transactional
    public void createNewWallet(Long telegramId) {
        try {
            log.info("Creating new wallet for telegram ID: {}", telegramId);
            // Get the user first
            User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Create the actual Solana wallet using existing method
            Wallet newWallet = createNewWallet(user, encryptionKeysPath, walletsPath);
            
            log.info("Successfully created wallet with ID: {} for user: {}", 
                newWallet.getWalletId(), telegramId);
            
        } catch (Exception e) {
            log.error("Failed to create wallet for telegram ID: {}", telegramId, e);
            throw new RuntimeException("Failed to create wallet", e);
        }
    }
}
