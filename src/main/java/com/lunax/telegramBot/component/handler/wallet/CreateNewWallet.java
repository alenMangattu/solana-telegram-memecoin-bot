package com.lunax.telegramBot.component.handler.wallet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.model.db.User;

@Component
public class CreateNewWallet implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Autowired
    public CreateNewWallet(@Value("🔑 Create New Wallet") String buttonText, 
                          @Value("create_new_wallet") String callbackData,
                          WalletRepository walletRepository,
                          UserRepository userRepository) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
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
        Map<String, String> walletInfo = SolanaUtils.createNewWallet();
        
        String publicKey = walletInfo.get("publicKey");
        String privateKey = walletInfo.get("privateKey");
        
        // Get the user from the update
        Long telegramUserId = update.getCallbackQuery().getFrom().getId();
        User user = userRepository.findByTelegramId(telegramUserId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // Create and save the wallet
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setWalletAddress(publicKey);
        wallet.setWalletLabel("Solana Wallet"); // Default label, can be customized later
        walletRepository.save(wallet);

        // Store private key securely
        try {
            String fileName = wallet.getWalletId().toString() + ".key";
            Path filePath = Paths.get("/home/computer/wallets", fileName);
            Files.write(filePath, privateKey.getBytes());
            Files.setPosixFilePermissions(filePath, 
                PosixFilePermissions.fromString("rw-------")); // Only owner can read/write
        } catch (IOException e) {
            throw new RuntimeException("Failed to store private key securely", e);
        }
    }
}
