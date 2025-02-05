package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
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

    public CreateMemeCoin(@Value("✅ Confirm Creation") String buttonText, 
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
        
        // Verify user is in correct state
        if (stateService.getState(telegramId) != ChatState.AWAITING_MEMECOIN_CONFIRMATION) {
            MessageUtils.sendMessage(update, "❌ Invalid state. Please start over.");
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
            // Extract memecoin data from state
            String name = stateData.get("name").asText();
            String symbol = stateData.get("symbol").asText();
            int decimals = stateData.get("decimals").asInt();
            double supply = stateData.get("supply").asDouble();

            MessageUtils.sendMessage(update, "🔄 Creating your memecoin... Please wait.");

            // Create the memecoin
            String tokenAddress = SolanaUtils.createMemecoin(
                user.getCurrentWallet(),
                name,
                symbol,
                decimals,
                supply
            );

            // Send success message with token address
            String successMessage = String.format(
                "✅ Memecoin created successfully!\n\n" +
                "Name: %s\n" +
                "Symbol: %s\n" +
                "Decimals: %d\n" +
                "Supply: %.2f\n" +
                "Token Address: `%s`",
                name, symbol, decimals, supply, tokenAddress
            );
            MessageUtils.sendMessage(update, successMessage);

        } catch (Exception e) {
            log.error("Failed to create memecoin", e);
            MessageUtils.sendMessage(update, "❌ Failed to create memecoin: " + e.getMessage());
        } finally {
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            stateService.clearStateData(telegramId);
        }
    }
}
