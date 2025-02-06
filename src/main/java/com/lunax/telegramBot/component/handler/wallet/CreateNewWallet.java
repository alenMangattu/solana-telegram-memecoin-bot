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

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.SolanaService;
import com.lunax.telegramBot.utils.EncryptionUtils;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class CreateNewWallet implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final WalletKeyManager walletKeyManager;
    private final UserService userService;
    private final SolanaUtils solanaUtils;
    private final SolanaService solanaService;

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
                          UserService userService, 
                          SolanaUtils solanaUtils,
                          SolanaService solanaService) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.walletKeyManager = walletKeyManager;
        this.userService = userService;
        this.solanaUtils = solanaUtils;
        this.solanaService = solanaService;
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
            Long telegramUserId = update.getCallbackQuery().getFrom().getId();
            
            if (!userService.hasAcceptedTos(telegramUserId)) {
                throw new RuntimeException("Please accept the Terms of Service before creating a wallet");
            }
            
            User user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUserId);
                    newUser.setAcceptedTos(true);
                    return userRepository.save(newUser);
                });

            MessageUtils.editMessage(update, "⏳ Generating wallet...", null);

            Wallet wallet = solanaService.createNewWallet(user, encryptionKeysPath, walletsPath);
            
            // Update user's current wallet
            user.setCurrentWallet(wallet.getWalletId());
            userRepository.save(user);

            Thread.sleep(750);

            String text = "✨ Wallet generated successfully!\n\nPublic Key: `" + wallet.getWalletAddress() + "`";
            MessageUtils.editMessage(update, text, null);
            MessageUtils.sendMessage(update, "You need to deposit 1 SOL to `" + wallet.getWalletAddress() + 
                "` wallet to start using it. (click to copy)");
            MessageUtils.sendMessage(update, "You can type /home or /start to go to the main menu.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create and store wallet securely", e);
        }
    }
}
