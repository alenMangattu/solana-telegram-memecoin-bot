package com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.MemecoinCreationService;
import com.lunax.telegramBot.utils.IpfsUtils;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateMemeCoin implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private MemecoinCreationService memecoinService;

    public CreateMemeCoin(@Value("Confirm Token Creation") String buttonText,
            @Value("confirm_memecoin_creation") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
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
        Long telegramId = update.getCallbackQuery().getFrom().getId();

        if (stateService.getState(telegramId) != ChatState.AWAITING_MEMECOIN_CONFIRMATION) {
            MessageUtils.sendMessage(update, "❌ Invalid state. Please start over.");
            return;
        }

        Message message = MessageUtils.getMessage(update);
        if (message == null) {
            MessageUtils.sendMessage(update, "❌ Error: Callback message is missing.");
            return;
        }

        ObjectNode stateData = stateService.getStateData(telegramId);
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getCurrentWallet() == null) {
            MessageUtils.sendMessage(update, "❌ Please create or select a wallet first!");
            return;
        }

        try {
            memecoinService.createMemecoin(update, user, stateData);
        } catch (Exception e) {
            log.error("Error creating memecoin: ", e);
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
        } finally {
            stateService.setState(telegramId, ChatState.IDLE);
            stateService.clearStateData(telegramId);
        }
    }

    private static String createImageUri(String filename) {
        // Upload the image file to IPFS
        String ipfsHash = IpfsUtils.addFile(filename);

        // Check if the upload was successful
        if (ipfsHash != null) {
            // Return the HTTP URL for the uploaded image
            return IpfsUtils.getIpfsUrl(ipfsHash);
        } else {
            // Handle the case where the upload failed
            log.error("Failed to upload image to IPFS: {}", filename);
            return null; // or throw an exception based on your error handling strategy
        }
    }
}
