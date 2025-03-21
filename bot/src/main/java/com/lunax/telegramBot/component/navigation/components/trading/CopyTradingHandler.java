package com.lunax.telegramBot.component.navigation.components.trading;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.CopyTrading;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.CopyTradeService;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;

@Slf4j
@Component
public class CopyTradingHandler {

    private static final int PAGE_SIZE = 5; // Number of configs per page
    
    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private CopyTradeService copyTradeService;

    /**
     * Validate a Solana address format
     */
    public boolean isValidSolanaAddress(String address) {
        // Basic validation for Solana addresses
        if (address == null || address.isEmpty()) {
            return false;
        }
        
        // Check if it looks like an @ mention or command
        if (address.contains("@") || address.contains("/")) {
            return false;
        }
        
        // Check length (typical Solana addresses are 32-44 chars)
        if (address.length() < 32 || address.length() > 44) {
            return false;
        }
        
        // Check if it uses only Base58 characters
        String base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        for (char c : address.toCharArray()) {
            if (base58Chars.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Get copy trading configurations for a user with caching
     * @param userId The user ID
     * @return List of copy trading configurations
     */
    @Cacheable(value = "copyTradingConfigs", key = "#userId")
    private List<CopyTrading> getCopyTradingConfigsForUser(Long userId) {
        log.debug("Cache miss - fetching copy trading configs for user {}", userId);
        return copyTradeService.getCopyTradingByUserId(userId);
    }

    /**
     * Handle the Copy Trading Page button click with pagination
     */
    public void handleCopyTradingButton(Update update) {
        handleCopyTradingButton(update, 0); // Default to first page
    }
    
    /**
     * Handle the Copy Trading Page button click with specific page
     */
    public void handleCopyTradingButton(Update update, int page) {
        // Get user ID
        Long telegramId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
        
        // Get chat ID 
        Long chatId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getMessage().getChatId() : 
            update.getMessage().getChatId();
        
        // Get user
        User user = userService.findByTelegramId(telegramId).orElse(null);
        if (user == null) {
            log.error("User not found for Telegram ID: {}", telegramId);
            MessageUtils.sendMessage(update, "❌ User not found. Please start the bot again.");
            return;
        }
        
        // Check if user has a wallet
        String walletAddress = user.getCurrentWalletAddress();
        if (walletAddress == null) {
            log.error("Current wallet not set for user: {}", telegramId);
            MessageUtils.sendMessage(update, "❌ You need to create or select a wallet first!");
            return;
        }
        
        // Set the state
        stateService.setState(telegramId, ChatState.COPY_TRADING_PAGE);
        ObjectNode stateData = objectMapper.createObjectNode();
        stateData.put("currentPage", page); // Store current page in state
        stateService.setStateData(telegramId, stateData);
        
        // Show loading message
        Message loadingMessage;
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            // Edit existing message
            loadingMessage = MessageUtils.editMessageText(
                chatId, 
                update.getCallbackQuery().getMessage().getMessageId(),
                "⟳ Loading your copy trading settings...",
                null
            );
        } else {
            // Send new message
            loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your copy trading settings...");
        }
        
        try {
            // Get copy trading configurations for the user from cache
            List<CopyTrading> copyTradingConfigs = getCopyTradingConfigsForUser(user.getId());
            
            // Calculate pagination
            int totalConfigs = copyTradingConfigs.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalConfigs / PAGE_SIZE)); // At least 1 page
            
            int startIndex = page * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, totalConfigs);
            
            // Create formatted message
            StringBuilder formattedList = new StringBuilder();
            formattedList.append("*Copy Trading Settings*");
            formattedList.append(totalPages > 1 ? String.format(" (Page %d/%d)\n\n", page + 1, totalPages) : "\n\n");
            
            if (copyTradingConfigs.isEmpty()) {
                formattedList.append("You don't have any copy trading configurations set up yet. Use the buttons below to add a new configuration.");
            } else {
                formattedList.append("Your current copy trading configurations:\n\n");
                
                // Only display configs for the current page
                for (int i = startIndex; i < endIndex; i++) {
                    CopyTrading config = copyTradingConfigs.get(i);
                    String sourceWallet = config.getSourceWallet().getWalletAddress();
                    String targetWallet = config.getTargetWalletAddress();
                    boolean isEnabled = config.isEnabled();
                    double multiplier = config.getAmountMultiplier();
                    
                    // Format the source wallet (truncate for display)
                    String displaySourceWallet = sourceWallet.length() > 16 ?
                        sourceWallet.substring(0, 6) + "..." + sourceWallet.substring(sourceWallet.length() - 6) :
                        sourceWallet;
                    
                    // Format the target wallet (truncate for display)
                    String displayTargetWallet = targetWallet.length() > 16 ?
                        targetWallet.substring(0, 6) + "..." + targetWallet.substring(targetWallet.length() - 6) :
                        targetWallet;
                    
                    // Format the config info
                    formattedList.append(String.format(
                        "%d. Source: Your wallet (%s)\n   Target (copying from): `%s`\n   Multiplier: %.2fx\n   Status: %s\n\n",
                        i + 1, // Display the index as 1-based
                        displaySourceWallet,
                        displayTargetWallet,
                        multiplier,
                        isEnabled ? "🟢 Enabled" : "🔒 Disabled"
                    ));
                }
            }
            
            // Create keyboard markup with buttons
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            
            // Add pagination row if needed
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                // Previous page button
                if (page > 0) {
                    paginationRow.add(InlineKeyboardButton.builder()
                            .text("◀️ Previous")
                            .callbackData("copy_trading_page_" + (page - 1))
                            .build());
                }
                
                // Current page indicator (optional)
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(String.format("Page %d/%d", page + 1, totalPages))
                        .callbackData("no_op") // No operation
                        .build());
                
                // Next page button
                if (page < totalPages - 1) {
                    paginationRow.add(InlineKeyboardButton.builder()
                            .text("Next ▶️")
                            .callbackData("copy_trading_page_" + (page + 1))
                            .build());
                }
                
                buttons.add(paginationRow);
            }
            
            // Add action buttons for each config on this page
            for (int i = startIndex; i < endIndex; i++) {
                CopyTrading config = copyTradingConfigs.get(i);
                Long configId = config.getId();
                boolean isEnabled = config.isEnabled();
                
                List<InlineKeyboardButton> actionRow = new ArrayList<>();
                
                // Toggle button
                actionRow.add(InlineKeyboardButton.builder()
                        .text(isEnabled ? "Disable #" + (i + 1) : "Enable #" + (i + 1))
                        .callbackData("toggle_copy_config_" + configId + "_" + !isEnabled)
                        .build());
                
                // Delete button
                actionRow.add(InlineKeyboardButton.builder()
                        .text("Delete #" + (i + 1))
                        .callbackData("delete_copy_config_" + configId)
                        .build());
                
                buttons.add(actionRow);
            }
            
            // Add "Add New Configuration" button
            List<InlineKeyboardButton> addRow = new ArrayList<>();
            addRow.add(InlineKeyboardButton.builder()
                    .text("➕ Add New Configuration")
                    .callbackData("add_copy_trading_config")
                    .build());
            buttons.add(addRow);
            
            // Add "Back to Trading" button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                    .text("🔙 Back to Trading")
                    .callbackData("back_to_trading")
                    .build());
            buttons.add(backRow);
            
            markup.setKeyboard(buttons);
            
            // Edit the message with the complete data
            try {
                EditMessageText editMessage = new EditMessageText();
                editMessage.setChatId(chatId.toString());
                editMessage.setMessageId(loadingMessage.getMessageId());
                editMessage.setText(formattedList.toString());
                editMessage.setParseMode("Markdown");
                editMessage.setReplyMarkup(markup);
                
                MessageUtils.getBotInstance().execute(editMessage);
            } catch (TelegramApiException e) {
                log.error("Error showing copy trading list: {}", e.getMessage());
                MessageUtils.sendMessage(update, "❌ Error displaying copy trading list: " + e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error loading copy trading configs: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error loading copy trading configurations: " + e.getMessage());
        }
    }

    /**
     * Handle adding a new copy trading configuration
     */
    public void handleAddCopyTradingConfig(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Clear any previous state data first
        stateService.setState(telegramId, ChatState.AWAITING_SOURCE_WALLET_INPUT);
        stateService.setStateData(telegramId, objectMapper.createObjectNode());
        
        MessageUtils.sendMessage(update, """
            Please enter the wallet address you want to copy trades from:
            """);
    }

    /**
     * Process the wallet address input (the wallet to copy trades from)
     */
    public void processSourceWalletInput(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        
        Long telegramId = update.getMessage().getFrom().getId();
        String externalWalletAddress = update.getMessage().getText().trim();
        
        // Get user
        User user = userService.findByTelegramId(telegramId).orElse(null);
        if (user == null) {
            log.error("User not found for Telegram ID: {}", telegramId);
            MessageUtils.sendMessage(update, "❌ User not found. Please start the bot again.");
            return;
        }
        
        // Validate wallet address format
        if (!isValidSolanaAddress(externalWalletAddress)) {
            MessageUtils.sendMessage(update, "❌ Invalid wallet address format. A Solana wallet address should be a Base58 encoded string.");
            return;
        }
        
        // Get or initialize state data
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData == null) {
            stateData = objectMapper.createObjectNode();
        }
        
        // Store external wallet address in state data (the wallet to copy)
        stateData.put("targetWalletAddress", externalWalletAddress);
        log.debug("Saved targetWalletAddress in state data: {}", externalWalletAddress);
        stateService.setStateData(telegramId, stateData);
        
        // Set state to awaiting multiplier selection
        stateService.setState(telegramId, ChatState.AWAITING_COPY_TRADE_MULTIPLIER_SELECTION);
        
        // Show user a confirmation with the address and multiplier buttons
        String message = String.format("""
            You will copy trades from:
            `%s`
            
            Now select a copy trade multiplier (how much to trade relative to the copied wallet):
            """, externalWalletAddress);
        
        // Send message with multiplier buttons - this message must be sent through a different way 
        // since we need to include buttons
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(update.getMessage().getChatId().toString());
        sendMessage.setText(message);
        sendMessage.setParseMode("Markdown");
        
        // Create multiplier selection buttons
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        
        // Row 1: 0.1x, 0.25x, 0.5x
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(InlineKeyboardButton.builder()
                .text("0.1x")
                .callbackData("copy_trade_multiplier_0.1")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("0.25x")
                .callbackData("copy_trade_multiplier_0.25")
                .build());
        row1.add(InlineKeyboardButton.builder()
                .text("0.5x")
                .callbackData("copy_trade_multiplier_0.5")
                .build());
        buttons.add(row1);
        
        // Row 2: 0.75x, 1x, 2x
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(InlineKeyboardButton.builder()
                .text("0.75x")
                .callbackData("copy_trade_multiplier_0.75")
                .build());
        row2.add(InlineKeyboardButton.builder()
                .text("1x")
                .callbackData("copy_trade_multiplier_1.0")
                .build());
        row2.add(InlineKeyboardButton.builder()
                .text("2x")
                .callbackData("copy_trade_multiplier_2.0")
                .build());
        buttons.add(row2);
        
        // Row 3: Custom
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        row3.add(InlineKeyboardButton.builder()
                .text("Custom Amount")
                .callbackData("copy_trade_multiplier_custom")
                .build());
        buttons.add(row3);
        
        // Add buttons to message
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(buttons);
        sendMessage.setReplyMarkup(markup);
        
        try {
            MessageUtils.getBotInstance().execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending multiplier selection message: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error displaying multiplier options. Please try again.");
        }
    }

    /**
     * Handle toggling a copy trading configuration
     */
    public void handleToggleCopyConfig(Update update, Long configId, boolean newEnabledState) {
        try {
            // First get the configuration to access its wallet details
            CopyTrading config = copyTradeService.getCopyTradingById(configId);
            
            if (config != null) {
                // Get the target wallet address
                String targetWallet = config.getTargetWalletAddress();
                
                // Now toggle the configuration
                config = copyTradeService.toggleCopyTrading(configId, newEnabledState);
                
                if (config != null) {
                    // Clear the cache for this user
                    clearUserConfigCache(config.getUser());
                    
                    // Show confirmation message with the wallet address
                    String statusChange = newEnabledState ? "Started" : "Paused";
                    String message = String.format("%s copy trading from wallet `%s`", 
                        statusChange, targetWallet);
                    MessageUtils.sendMessage(update, message);
                } else {
                    MessageUtils.sendMessage(update, "❌ Error: Configuration not found.");
                }
            } else {
                MessageUtils.sendMessage(update, "❌ Error: Configuration not found.");
            }
        } catch (Exception e) {
            log.error("Error toggling copy trading configuration: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
        }
        
        // Refresh the copy trading page
        handleCopyTradingButton(update);
    }

    /**
     * Handle deleting a copy trading configuration
     */
    public void handleDeleteCopyConfig(Update update, Long configId) {
        try {
            // First, get the configuration to access its wallet details
            CopyTrading config = copyTradeService.getCopyTradingById(configId);
            
            if (config != null) {
                User user = config.getUser();
                String targetWallet = config.getTargetWalletAddress();
                
                // Now delete the configuration
                boolean success = copyTradeService.deleteCopyTrading(configId);
                
                if (success) {
                    // Clear the cache for this user
                    clearUserConfigCache(user);
                    
                    // Show confirmation message with the wallet address
                    String message = String.format("Deleted copy trading from wallet `%s`", targetWallet);
                    MessageUtils.sendMessage(update, message);
                } else {
                    MessageUtils.sendMessage(update, "❌ Error: Configuration not found.");
                }
            } else {
                MessageUtils.sendMessage(update, "❌ Error: Configuration not found.");
            }
        } catch (Exception e) {
            log.error("Error deleting copy trading configuration: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
        }
        
        // Refresh the copy trading page
        handleCopyTradingButton(update);
    }

    // Add a new method to handle multiplier button selections
    public void handleMultiplierSelection(Update update, String multiplierStr) {
        if (!update.hasCallbackQuery()) {
            return;
        }
        
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Make sure we don't lose the previous state data
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData == null) {
            log.error("State data is missing in multiplier selection");
            MessageUtils.sendMessage(update, "❌ Error in copy trading setup. Please start again.");
            return;
        }
        
        // If custom amount was selected, prompt for input
        if ("custom".equals(multiplierStr)) {
            stateService.setState(telegramId, ChatState.AWAITING_COPY_TRADE_MULTIPLIER);
            // Don't clear the state data here
            MessageUtils.sendMessage(update, """
                    Please enter the custom amount multiplier:
                    
                    Enter a number greater than 0. For example:
                    - 0.05 = 5% of source trades
                    - 0.75 = 75% of source trades
                    - 3.5 = 350% of source trades
                    """);
            return;
        }
        
        // Otherwise, use the selected preset multiplier
        try {
            double multiplier = Double.parseDouble(multiplierStr);
            createCopyTradingConfig(update, telegramId, multiplier);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Error processing multiplier. Please try again.");
        }
    }

    // Helper method to create the copy trading configuration
    private void createCopyTradingConfig(Update update, Long telegramId, double multiplier) {
        try {
            // Get state data
            ObjectNode stateData = stateService.getStateData(telegramId);
            if (stateData == null || !stateData.has("targetWalletAddress")) {
                log.error("Missing target wallet address in state data");
                MessageUtils.sendMessage(update, "❌ Error in copy trading setup. Please start again.");
                return;
            }
            
            // Get user and target wallet address (the one to copy)
            User user = userService.findByTelegramId(telegramId).orElse(null);
            String targetWalletAddress = stateData.get("targetWalletAddress").asText();
            
            if (user == null) {
                MessageUtils.sendMessage(update, "❌ User not found. Please start the bot again.");
                return;
            }
            
            // Get user's current wallet as the source
            String sourceWalletAddress = user.getCurrentWalletAddress();
            
            if (sourceWalletAddress == null) {
                MessageUtils.sendMessage(update, "❌ You need to set a current wallet first.");
                return;
            }
            
            try {
                // Create configuration with user's current wallet as source
                CopyTrading copyTrading = copyTradeService.createCopyTrading(
                    user, 
                    sourceWalletAddress,
                    targetWalletAddress,
                    multiplier
                );
                
                // Clear the cache for this user
                clearUserConfigCache(user);
                
                // Send success message
                MessageUtils.sendMessage(update, String.format("""
                        ✅ Copy trading configuration added successfully!
                        
                        Source: `%s` (your wallet)
                        Target: `%s` (wallet to copy)
                        Multiplier: %.2fx
                        """, 
                        copyTrading.getSourceWallet().getWalletAddress(),
                        copyTrading.getTargetWalletAddress(),
                        multiplier));
                
                // Reset state and refresh page
                stateService.setState(telegramId, ChatState.COPY_TRADING_PAGE);
                handleCopyTradingButton(update);
            } catch (Exception e) {
                log.error("Error creating copy trading configuration: {}", e.getMessage());
                MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating copy trading configuration: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error creating copy trading configuration: " + e.getMessage());
        }
    }

    /**
     * Process the multiplier input when user has selected "Custom Amount"
     */
    public void processAmountMultiplierInput(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        
        Long telegramId = update.getMessage().getFrom().getId();
        String input = update.getMessage().getText().trim();
        
        try {
            // Parse the multiplier
            double multiplier = Double.parseDouble(input);
            
            // Validate the multiplier
            if (multiplier <= 0) {
                MessageUtils.sendMessage(update, "❌ Multiplier must be greater than 0. Please enter a valid number:");
                return;
            }
            
            // Create the config using the helper method
            createCopyTradingConfig(update, telegramId, multiplier);
            
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Invalid number format. Please enter a valid number (e.g., 0.5, 1.0, 2.5):");
        }
    }

    /**
     * Handle pagination for copy trading page
     */
    public void handleCopyTradingPage(Update update, int page) {
        handleCopyTradingButton(update, page);
    }

    /**
     * Clear the cache when a configuration is created
     */
    @CacheEvict(value = "copyTradingConfigs", key = "#user.id")
    private void clearUserConfigCache(User user) {
        log.debug("Cleared copy trading config cache for user {}", user.getId());
    }
} 