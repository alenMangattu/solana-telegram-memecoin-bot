package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeMintAuthority;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.StateService;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

    // @Autowired
    // private SolanaUtils solanaUtils;

    @Autowired
    private CreateMemeCoin createMemeCoin;

    @Autowired
    private NotConfirmed notConfirmed;

    @Autowired
    private AddLiquidity addLiquidity;

    @Autowired
    private RevokeMintAuthority revokeMintAuthority;

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
        String callbackData = update.getCallbackQuery().getData();
        log.debug("Received callback data: {}", callbackData);

        handleMemecoinName(update);
    }

    public void handleMemecoinName(Update update) {
        Long telegramId = null;
        if (update.hasCallbackQuery()) {
            telegramId = update.getCallbackQuery().getFrom().getId();
        } else if (update.hasMessage()) {
            telegramId = update.getMessage().getFrom().getId();
        }
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
                🪙 *Create Your Memecoin* (1/8)

                Let's create your own memecoin! First, what would you like to name your memecoin?

                _Example: Lunar Moon_
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_NAME);

    }

    public void handleMessage(Update update) {
        Message message = update.getMessage();
        Long telegramId = null;
        if (update.hasCallbackQuery()) {
            telegramId = update.getCallbackQuery().getFrom().getId();
        } else if (update.hasMessage()) {
            telegramId = update.getMessage().getFrom().getId();
        }

        ChatState currentState = stateService.getState(telegramId);

        // If there's no current state, initialize the memecoin creation process
        if (currentState == null || currentState == ChatState.IDLE) {
            handleMemecoinName(update);
            return;
        }

        ObjectNode stateData = stateService.getStateData(telegramId);

        // Initialize stateData if null
        if (stateData == null) {
            stateData = objectMapper.createObjectNode();
            stateService.setStateData(telegramId, stateData);
        }

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
        if (name == null || name.length() < 3 || name.length() > 50) {
            MessageUtils.sendMessage(update, "❌ Name must be between 3 and 50 characters. Please try again:");
            return;
        }

        stateData.put("name", name);
        stateService.setStateData(telegramId, stateData);

        String message = """
                Great! Now, what should be the ticker symbol for your memecoin? (2/8)

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
        if (symbol == null || !symbol.matches("^[A-Z0-9]{2,10}$")) {
            MessageUtils.sendMessage(update,
                    "❌ Symbol must be 2-10 characters and contain only uppercase letters and numbers. Please try again:");
            return;
        }

        stateData.put("symbol", symbol.toUpperCase());
        stateService.setStateData(telegramId, stateData);

        String message = """
                Perfect! Now, how many decimals should your token have? (3/8)

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
        if (decimalsStr == null) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number between 0 and 9:");
            return;
        }

        try {
            int decimals = Integer.parseInt(decimalsStr);
            if (decimals < 0 || decimals > 9) {
                throw new NumberFormatException();
            }

            stateData.put("decimals", decimals);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    Great! Now, what should be the total supply of your memecoin? (4/8)

                    _Example: 1000000000_

                    Requirements:
                    - Must be a positive number
                    - Maximum 1 quadrillion (1,000,000,000,000,000)
                    - Minimum 10
                    """;

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_SUPPLY);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number between 0 and 9:");
        }
    }

    private void handleMemecoinSupply(Update update, Long telegramId, String supplyStr, ObjectNode stateData) {
        if (supplyStr == null) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number for supply:");
            return;
        }

        try {
            double supply = Double.parseDouble(supplyStr);
            if (supply <= 10 || supply > 1_000_000_000_000_000.0) {
                throw new NumberFormatException();
            }

            stateData.put("supply", supply);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    Great! Now, provide a brief description for your memecoin. (5/8)

                    _Example: The first lunar-themed memecoin on Solana!_
                    """;

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_DESCRIPTION);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid number between 10 and 1 quadrillion:");
        }
    }

    private void handleMemecoinDescription(Update update, Long telegramId, String description, ObjectNode stateData) {
        if (description == null || description.length() > 500) {
            MessageUtils.sendMessage(update, "❌ Description must be less than 500 characters. Please try again:");
            return;
        }

        stateData.put("description", description);
        stateService.setStateData(telegramId, stateData);

        // After description, ask about mint authority
        String message = """
                🔒 *Authority Settings* (6/8)

                Would you like to revoke the mint authority?
                This means no more tokens can be minted after creation.

                Note: This action cannot be undone.
                """;

        ButtonHandler[][] buttons = {
                {
                        new ButtonHandler() {
                            public String getButtonText() {
                                return "Yes";
                            }

                            public String getCallbackData() {
                                return "revoke_mint_authority_confirm";
                            }

                            public void handleCallback(Update update) {
                                revokeMintAuthority.handleCallback(update);
                            }
                        },
                        new ButtonHandler() {
                            public String getButtonText() {
                                return "No";
                            }

                            public String getCallbackData() {
                                return "revoke_mint_authority_cancel";
                            }

                            public void handleCallback(Update update) {
                                revokeMintAuthority.handleCancel(update);
                            }
                        }
                }
        };

        MessageUtils.sendInlineKeyboard(update, message, buttons);
        stateService.setState(telegramId, ChatState.AWAITING_REVOKE_MINT_AUTHORITY_CONFIRMATION);
    }

    private void handleMemecoinImage(Update update, Long telegramId, Message message, ObjectNode stateData) {
        if (message == null || !message.hasPhoto()) {
            MessageUtils.sendMessage(update,
                    "❌ Provide a valid image.");
            return;
        }

        // Download the image using MessageUtils
        String imagePath = "";
        try {
            imagePath = MessageUtils.downloadImage(update);
        } catch (TelegramApiException e) {
            MessageUtils.sendMessage(update, "❌ Failed to download image: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (imagePath == null || !isImageFile(imagePath) || !isFileSizeValid(imagePath, 2 * 1024 * 1024)) {
            MessageUtils.sendMessage(update,
                    "❌ Invalid image format or size. Please send a valid image (JPG or PNG) under 2MB:");
            if (imagePath != null) {
                new File(imagePath).delete(); // Delete the image path if not null
            }
            return;
        }

        stateData.put("imageUrl", imagePath);
        stateService.setStateData(telegramId, stateData);

        // Show confirmation message with memecoin details
        String confirmationMessage = String.format("""
                📝 *Please Confirm Your Memecoin Details*

                Name: %s
                Symbol: %s
                Decimals: %d
                Supply: %s
                Description: %s
                Revoke Mint Authority: %s
                Revoke Freeze Authority: %s

                Are you sure you want to create this memecoin?

                Note: This action cannot be undone.
                """,
                stateData.get("name").asText(),
                stateData.get("symbol").asText(),
                stateData.get("decimals").asInt(),
                String.format("%,.0f", stateData.get("supply").asDouble()),
                stateData.get("description").asText(),
                stateData.has("revokeMintAuthority") ? (stateData.get("revokeMintAuthority").asBoolean() ? "Yes" : "No")
                        : "No",
                stateData.has("revokeFreezeAuthority")
                        ? (stateData.get("revokeFreezeAuthority").asBoolean() ? "Yes" : "No")
                        : "No");

        ButtonHandler[][] buttons = {
                { createMemeCoin },
                { addLiquidity },
                { notConfirmed }
        };

        // Store the confirmation message in the state data
        stateData.put("confirmationMessage", confirmationMessage);
        stateService.setStateData(telegramId, stateData);

        // Send the image with the confirmation message
        MessageUtils.sendImageMessageInlineKeyboard(update, imagePath, confirmationMessage, buttons);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_CONFIRMATION);
    }

    private boolean isImageFile(String filePath) {
        // Implement logic to check if the file is an image (e.g., check file extension)
        log.info("Checking if file is an image: {}", filePath);
        return filePath.endsWith(".jpg") || filePath.endsWith(".png");
    }

    private boolean isFileSizeValid(String filePath, long maxSize) {
        // Implement logic to check the file size
        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("File does not exist: {}", filePath);
            return false;
        }

        long fileSize = 0;
        try {
            fileSize = Files.size(file.toPath());
        } catch (IOException e) {
            log.error("Error getting file size for {}: {}", filePath, e.getMessage());
            return false;
        }

        log.info("Checking if file size is valid: {}", fileSize);
        log.info("Max size: {}", maxSize);
        return fileSize <= maxSize;
    }
}