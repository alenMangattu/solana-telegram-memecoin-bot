package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.EncryptionUtils;

import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import javax.crypto.SecretKey;
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

    @Transactional
    public Wallet createNewWallet(User user, String encryptionKeysPath, String walletsPath) {
        try {
            // First create the Solana wallet
            Map<String, String> walletInfo = solanaUtils.createNewWallet(null); // Pass null since we don't have UUID yet
            
            // Create and save wallet entity with the address
            Wallet wallet = new Wallet();
            wallet.setUser(user);
            wallet.setWalletAddress(walletInfo.get("publicKey")); // Set address before saving
            wallet.setWalletLabel("Solana Wallet");
            wallet = walletRepository.save(wallet);

            // Now handle encryption with the saved wallet's UUID
            try {
                SecretKey encryptionKey = EncryptionUtils.generateEncryptionKey();
                storeWalletKeys(wallet, walletInfo.get("privateKey"), encryptionKey, encryptionKeysPath, walletsPath);
            } catch (Exception e) {
                // If encryption fails, delete the wallet and throw
                walletRepository.delete(wallet);
                throw e;
            }
            
            return wallet;
        } catch (Exception e) {
            log.error("Failed to create wallet", e);
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    private void storeWalletKeys(Wallet wallet, String privateKey, SecretKey encryptionKey, 
                                String encryptionKeysPath, String walletsPath) throws Exception {
        try {
            // Store encryption key
            String encryptionKeyFileName = wallet.getWalletId().toString() + ".key";
            Path encryptionKeyPath = Paths.get(encryptionKeysPath, encryptionKeyFileName);
            Files.createDirectories(encryptionKeyPath.getParent());
            Files.write(encryptionKeyPath, EncryptionUtils.serializeKey(encryptionKey).getBytes());
            Files.setPosixFilePermissions(encryptionKeyPath, 
                PosixFilePermissions.fromString("rw-------"));

            // Store encrypted private key
            String encryptedPrivateKey = EncryptionUtils.encrypt(privateKey, encryptionKey);
            String walletFileName = wallet.getWalletId().toString() + ".encrypted";
            Path walletPath = Paths.get(walletsPath, walletFileName);
            Files.createDirectories(walletPath.getParent());
            Files.write(walletPath, encryptedPrivateKey.getBytes());
            Files.setPosixFilePermissions(walletPath, 
                PosixFilePermissions.fromString("rw-------"));

            // Create initial key version
            walletKeyManager.createNewKeyVersion(wallet.getWalletId());
        } catch (Exception e) {
            // Clean up any files that might have been created
            try {
                Files.deleteIfExists(Paths.get(encryptionKeysPath, wallet.getWalletId().toString() + ".key"));
                Files.deleteIfExists(Paths.get(walletsPath, wallet.getWalletId().toString() + ".encrypted"));
            } catch (Exception cleanupError) {
                log.error("Failed to cleanup after error", cleanupError);
            }
            throw e;
        }
    }
}
