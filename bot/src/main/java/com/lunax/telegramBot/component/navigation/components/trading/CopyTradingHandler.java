package com.lunax.telegramBot.component.navigation.components.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.CopyTrading;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.model.repository.CopyTradingRepository;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.CopyTradeService;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CopyTradingHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private CopyTradeService copyTradeService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CopyTradingRepository copyTradingRepository;

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
     * Handle the Copy Trading Page button click
     */
    public void handleCopyTradingButton(Update update) {
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
        stateService.setStateData(telegramId, stateData);
        
        // Show loading message
        Message loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your copy trading settings...");
        
        try {
            // Get copy trading configurations for the user
            List<CopyTrading> copyTradingConfigs = copyTradeService.getCopyTradingByUserId(user.getId().longValue());
            
            // Create formatted message
            StringBuilder formattedList = new StringBuilder();
            formattedList.append("*Copy Trading Settings*\n\n");
            
            if (copyTradingConfigs.isEmpty()) {
                formattedList.append("You don't have any copy trading configurations set up yet. Use the buttons below to add a new configuration.");
            } else {
                formattedList.append("Your current copy trading configurations:\n\n");
                
                for (int i = 0; i < copyTradingConfigs.size(); i++) {
                    CopyTrading config = copyTradingConfigs.get(i);
                    String sourceWallet = config.getSourceWallet().getWalletAddress();
                    String targetWallet = config.getTargetWallet().getWalletAddress();
                    boolean isEnabled = config.isEnabled();
                    
                    // Format the source wallet (truncate for display)
                    String displaySourceWallet = sourceWallet.length() > 16 ?
                        sourceWallet.substring(0, 6) + "..." + sourceWallet.substring(sourceWallet.length() - 6) :
                        sourceWallet;
                    
                    // Format the config info
                    formattedList.append(String.format(
                        "%d. Source: `%s`\n   Target: Your wallet\n   Status: %s\n\n",
                        i + 1, displaySourceWallet, isEnabled ? "✅ Enabled" : "❌ Disabled"
                    ));
                }
            }
            
            // Create keyboard markup with buttons
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            
            // Add new configuration button
            List<InlineKeyboardButton> addConfigRow = new ArrayList<>();
            addConfigRow.add(InlineKeyboardButton.builder()
                .text("➕ Add New Configuration")
                .callbackData("add_copy_trading_config")
                .build());
            buttons.add(addConfigRow);
            
            // Add buttons for each configuration if any exist
            if (!copyTradingConfigs.isEmpty()) {
                for (int i = 0; i < copyTradingConfigs.size(); i++) {
                    CopyTrading config = copyTradingConfigs.get(i);
                    boolean isEnabled = config.isEnabled();
                    
                    List<InlineKeyboardButton> configRow = new ArrayList<>();
                    
                    // Toggle button
                    configRow.add(InlineKeyboardButton.builder()
                        .text(isEnabled ? "🔴 Disable #" + (i+1) : "🟢 Enable #" + (i+1))
                        .callbackData("toggle_copy_config_" + config.getId() + "_" + !isEnabled)
                        .build());
                    
                    // Delete button
                    configRow.add(InlineKeyboardButton.builder()
                        .text("🗑️ Delete #" + (i+1))
                        .callbackData("delete_copy_config_" + config.getId())
                        .build());
                    
                    buttons.add(configRow);
                }
            }
            
            // Back button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("Back to Trading")
                .callbackData("back_to_trading")
                .build());
            buttons.add(backRow);
            
            markup.setKeyboard(buttons);
            
            // Edit the loading message with the copy trading list
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(loadingMessage.getMessageId());
            editMessage.setText(formattedList.toString());
            editMessage.setParseMode("Markdown");
            editMessage.setReplyMarkup(markup);
            
            try {
                MessageUtils.getBotInstance().execute(editMessage);
            } catch (TelegramApiException e) {
                log.error("Error showing copy trading list: {}", e.getMessage());
                MessageUtils.sendMessage(update, "❌ Error displaying copy trading list: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error loading copy trading settings: {}", e.getMessage());
            MessageUtils.editMessage(update, "❌ Error loading copy trading settings: " + e.getMessage(), null);
        }
    }

    /**
     * Handle adding a new copy trading configuration
     */
    public void handleAddCopyTradingConfig(Update update) {
        Long telegramId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
        
        // Set state to awaiting source wallet address
        stateService.setState(telegramId, ChatState.AWAITING_SOURCE_WALLET_ADDRESS);
        
        // Send message asking for source wallet address
        String message = "Please enter the wallet address you want to copy trades from:";
        MessageUtils.sendMessage(update, message);
    }

    /**
     * Process the source wallet input after Add Configuration button is clicked
     */
    public void processSourceWalletInput(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        
        Long telegramId = update.getMessage().getFrom().getId();
        String sourceWalletAddress = update.getMessage().getText().trim();
        
        // Get user
        User user = userService.findByTelegramId(telegramId).orElse(null);
        if (user == null) {
            log.error("User not found for Telegram ID: {}", telegramId);
            MessageUtils.sendMessage(update, "❌ User not found. Please start the bot again.");
            return;
        }
        
        // Validate wallet address format
        if (!isValidSolanaAddress(sourceWalletAddress)) {
            MessageUtils.sendMessage(update, "❌ Invalid wallet address format. A Solana wallet address should be a Base58 encoded string.");
            return;
        }
        
        try {
            // Get or create source wallet
            Wallet sourceWallet = walletRepository.findByWalletAddress(sourceWalletAddress)
                .orElseGet(() -> {
                    Wallet wallet = new Wallet();
                    wallet.setWalletAddress(sourceWalletAddress);
                    wallet.setUser(user); // Set the user here!
                    return walletRepository.save(wallet);
                });
            
            // Get target wallet (user's current wallet)
            Wallet targetWallet = walletRepository.findByWalletAddress(user.getCurrentWalletAddress())
                .orElseThrow(() -> new RuntimeException("User's current wallet not found"));
            
            // Create copy trading config
            CopyTrading copyTrading = new CopyTrading();
            copyTrading.setSourceWallet(sourceWallet);
            copyTrading.setTargetWallet(targetWallet);
            copyTrading.setUser(user);
            copyTrading.setEnabled(true);
            
            // Save copy trading config
            copyTradingRepository.save(copyTrading);
            
            // Send success message
            MessageUtils.sendMessage(update, "✅ Copy trading configuration added successfully!\n\n" +
                    "Source: `" + sourceWallet.getWalletAddress() + "`\n" +
                    "Target: `" + targetWallet.getWalletAddress() + "`");
            
            // Refresh the copy trading page
            handleCopyTradingButton(update);
            
        } catch (Exception e) {
            log.error("Error creating copy trading configuration: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error creating copy trading configuration: " + e.getMessage());
        }
    }

    /**
     * Handle toggling a copy trading configuration
     */
    public void handleToggleCopyConfig(Update update, Long configId, boolean newEnabledState) {
        try {
            CopyTrading config = copyTradeService.toggleCopyTrading(configId, newEnabledState);
            
            if (config != null) {
                // Show confirmation message
                String message = String.format("Copy trading configuration %s has been %s.",
                    configId, newEnabledState ? "enabled" : "disabled");
                MessageUtils.sendMessage(update, message);
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
            boolean success = copyTradeService.deleteCopyTrading(configId);
            
            if (success) {
                // Show confirmation message
                String message = String.format("Copy trading configuration %s has been deleted.", configId);
                MessageUtils.sendMessage(update, message);
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
} 