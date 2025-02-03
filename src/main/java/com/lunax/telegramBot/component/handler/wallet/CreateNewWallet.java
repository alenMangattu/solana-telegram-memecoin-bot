package com.lunax.telegramBot.component.handler.wallet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.EncryptionUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.UserService;

@Component
public class CreateNewWallet implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletKeyManager walletKeyManager;
    private final UserService userService;
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;
    
    @Value("${wallets.path}")
    private String walletsPath;

    @Autowired
    public CreateNewWallet(@Value("🔑 Create New Wallet") String buttonText, 
                          @Value("create_new_wallet") String callbackData,
                          WalletRepository walletRepository,
                          UserRepository userRepository,
                          WalletKeyManager walletKeyManager,
                          UserService userService) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletKeyManager = walletKeyManager;
        this.userService = userService;
    }

    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }

    @Override
    public void handleCallback(Update update) {
        try {
            Map<String, String> walletInfo = SolanaUtils.createNewWallet();
            
            String publicKey = walletInfo.get("publicKey");
            String privateKey = walletInfo.get("privateKey");
            
            // Get the user from the update
            Long telegramUserId = update.getCallbackQuery().getFrom().getId();
            
            // Check if user has accepted ToS
            if (!userService.hasAcceptedTos(telegramUserId)) {
                throw new RuntimeException("Please accept the Terms of Service before creating a wallet");
            }
            
            // Get or create user
            User user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUserId);
                    newUser.setAcceptedTos(true); // We already checked this above
                    return userRepository.save(newUser);
                });

            // Create and save the wallet
            Wallet wallet = new Wallet();
            wallet.setUser(user);
            wallet.setWalletAddress(publicKey);
            wallet.setWalletLabel("Solana Wallet"); // Default label, can be customized later
            walletRepository.save(wallet);

            // Generate encryption key
            SecretKey encryptionKey = EncryptionUtils.generateEncryptionKey();
            
            // Store encryption key in a separate secure location
            String encryptionKeyFileName = wallet.getWalletId().toString() + ".key";
            Path encryptionKeyPath = Paths.get(encryptionKeysPath, encryptionKeyFileName);
            Files.createDirectories(encryptionKeyPath.getParent());
            Files.write(encryptionKeyPath, 
                       EncryptionUtils.serializeKey(encryptionKey).getBytes());
            Files.setPosixFilePermissions(encryptionKeyPath, 
                PosixFilePermissions.fromString("rw-------")); // Only owner can read/write
            
            // Encrypt and store private key
            String encryptedPrivateKey = EncryptionUtils.encrypt(privateKey, encryptionKey);
            String walletFileName = wallet.getWalletId().toString() + ".encrypted";
            Path walletPath = Paths.get(walletsPath, walletFileName);
            Files.createDirectories(walletPath.getParent());
            Files.write(walletPath, encryptedPrivateKey.getBytes());
            Files.setPosixFilePermissions(walletPath, 
                PosixFilePermissions.fromString("rw-------")); // Only owner can read/write
                
            // Create initial key version with rotation policy
            walletKeyManager.createNewKeyVersion(wallet.getWalletId());

        } catch (Exception e) {
            throw new RuntimeException("Failed to create and store wallet securely", e);
        }
    }
}
