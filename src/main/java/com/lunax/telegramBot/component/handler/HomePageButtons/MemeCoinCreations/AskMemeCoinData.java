package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.SolanaUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Component
public class AskMemeCoinData implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;
    private final ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private CreateMemeCoin createMemeCoin;

    @Autowired
    private NotConfirmed notConfirmed;

    public AskMemeCoinData(@Value("🪙 Create Meme Coin") String buttonText,
            @Value("create_meme_coin") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
        this.objectMapper = new ObjectMapper();
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
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if user has a wallet
        if (user.getCurrentWallet() == null) {
            MessageUtils.sendMessage(update, "❌ You need to create or select a wallet first!");
            return;
        }

        // Initialize memecoin creation state
        stateService.setState(telegramId, ChatState.CREATE_MEMECOIN_START);
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);

        String message = """
                🪙 *Create Your Memecoin* (1/6)

                Let's create your own memecoin! First, what would you like to name your memecoin?

                _Example: Lunar Moon_
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_NAME);
    }

    public void handleMessage(Update update) {
        Message message = update.getMessage();
        Long telegramId = message.getFrom().getId();
        ChatState currentState = stateService.getState(telegramId);
        ObjectNode stateData = stateService.getStateData(telegramId);

        switch (currentState) {
            case AWAITING_MEMECOIN_NAME:
                handleMemecoinName(update, telegramId, message.getText(), stateData);
                break;
            case AWAITING_MEMECOIN_SYMBOL:
                handleMemecoinSymbol(update, telegramId, message.getText(), stateData);
                break;
            case AWAITING_MEMECOIN_DECIMALS:
                handleMemecoinDecimals(update, telegramId, message.getText(), stateData);
                break;
            case AWAITING_MEMECOIN_SUPPLY:
                handleMemecoinSupply(update, telegramId, message.getText(), stateData);
                break;
            case AWAITING_MEMECOIN_DESCRIPTION:
                handleMemecoinDescription(update, telegramId, message.getText(), stateData);
                break;
            case AWAITING_MEMECOIN_IMAGE:
                handleMemecoinImage(update, telegramId, message, stateData);
                break;
            default:
                log.warn("Unexpected state: {}", currentState);
                break;
        }
    }

    private void handleMemecoinName(Update update, Long telegramId, String name, ObjectNode stateData) {
        if (name.length() < 3 || name.length() > 50) {
            MessageUtils.sendMessage(update, "❌ Name must be between 3 and 50 characters. Please try again:");
            return;
        }

        stateData.put("name", name);
        stateService.setStateData(telegramId, stateData);

        String message = """
                Great! Now, what should be the ticker symbol for your memecoin? (2/6)

                _Example: LUNAR_

                Requirements:
                - 2-10 characters
                - Only uppercase letters and numbers
                - Must be unique
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_SYMBOL);
    }

    private void handleMemecoinSymbol(Update update, Long telegramId, String symbol, ObjectNode stateData) {
        symbol = symbol.toUpperCase();
        if (!symbol.matches("^[A-Z0-9]{2,10}$")) {
            MessageUtils.sendMessage(update,
                    "❌ Symbol must be 2-10 characters and contain only uppercase letters and numbers. Please try again:");
            return;
        }

        // TODO: Check if symbol already exists in database

        stateData.put("symbol", symbol);
        stateService.setStateData(telegramId, stateData);

        String message = """
                Perfect! Now, how many decimals should your token have? (3/6)

                _Example: 9_

                Requirements:
                - Must be a number between 0 and 9
                - Standard is 9 decimals (like SOL)
                - Fewer decimals = less divisible tokens
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_DECIMALS);
    }

    private void handleMemecoinDecimals(Update update, Long telegramId, String decimalsStr, ObjectNode stateData) {
        try {
            int decimals = Integer.parseInt(decimalsStr);
            if (decimals < 0 || decimals > 9) {
                throw new NumberFormatException();
            }

            stateData.put("decimals", decimals);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    Great! Now, what should be the total supply of your memecoin? (4/6)

                    _Example: 1000000000_

                    Requirements:
                    - Must be a positive number
                    - Maximum 1 quadrillion (1,000,000,000,000,000)
                    """;

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_SUPPLY);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number between 0 and 9:");
        }
    }

    private void handleMemecoinSupply(Update update, Long telegramId, String supplyStr, ObjectNode stateData) {
        try {
            double supply = Double.parseDouble(supplyStr);
            if (supply <= 0 || supply > 1_000_000_000_000_000.0) {
                throw new NumberFormatException();
            }

            stateData.put("supply", supply);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    Great! Now, provide a brief description for your memecoin. (5/6)

                    _Example: The first lunar-themed memecoin on Solana!_
                    """;

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_DESCRIPTION);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number between 0 and 1 quadrillion:");
        }
    }

    private void handleMemecoinDescription(Update update, Long telegramId, String description, ObjectNode stateData) {
        if (description.length() > 500) {
            MessageUtils.sendMessage(update, "❌ Description must be less than 500 characters. Please try again:");
            return;
        }

        stateData.put("description", description);
        stateService.setStateData(telegramId, stateData);

        String message = """
                Almost done! Finally, send me an image or GIF for your memecoin's logo. (6/6)

                Requirements:
                - File size: max 5MB
                - Format: JPG, PNG, or GIF
                - Dimensions: 512x512 or 1024x1024
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_IMAGE);
    }

    private void handleMemecoinImage(Update update, Long telegramId, Message message, ObjectNode stateData) {
        // Download the image using MessageUtils
        String imagePath = MessageUtils.downloadImage(update);
        if (imagePath == null) {
            MessageUtils.sendMessage(update,
                    "❌ Invalid image format. Please send a valid image and dimensions (512x512 or 1024x1024) (JPG, PNG, or GIF):");
            return;
        }

        stateData.put("imageUrl", imagePath);
        stateService.setStateData(telegramId, stateData);

        // Show confirmation message with memecoin details
        String confirmationMessage = String.format("""
                📝 *Please confirm your memecoin details:*

                Name: %s
                Symbol: %s
                Decimals: %d
                Supply: %s
                Description: %s

                Are you sure you want to create this memecoin?
                
                Note: This action cannot be undone.
                """,
                stateData.get("name").asText(),
                stateData.get("symbol").asText(),
                stateData.get("decimals").asInt(),
                String.format("%,.0f", stateData.get("supply").asDouble()),
                stateData.get("description").asText()
        );

        ButtonHandler[][] buttons = {
            {createMemeCoin},
            {notConfirmed}
        };

        MessageUtils.sendImageMessageInlineKeyboard(update, imagePath, confirmationMessage, buttons);
        // Create inline keyboard with confirmation button
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_CONFIRMATION);
    }
}
