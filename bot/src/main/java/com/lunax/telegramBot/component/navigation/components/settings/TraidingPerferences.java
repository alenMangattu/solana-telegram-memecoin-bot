package com.lunax.telegramBot.component.navigation.components.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.UserSettings;
import com.lunax.telegramBot.model.repository.UserSettingsRepository;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.service.UserSettingsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

@Component
public class TraidingPerferences implements StateHandler, ButtonHandler {
    
    private static final Logger log = LoggerFactory.getLogger(TraidingPerferences.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    
    @Autowired
    private StateService stateService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    @Override
    public void executeVoid(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Fetch user settings or create default if not exists
        UserSettings userSetting = getUserSettings(user);
        
        // Display settings and edit options
        displayTradingPreferences(update, userSetting);
        
        // Register callback handlers
        registerCallbackHandlers();
    }
    
    /**
     * Get existing user settings or create default ones
     */
    private UserSettings getUserSettings(User user) {
        UserSettings settings = userSettingsService.findByUserId(user.getId());
        
        if (settings != null) {
            return settings;
        } else {
            // Create default settings
            UserSettings defaultSettings = new UserSettings();
            defaultSettings.setUserId(user.getId());
            
            // Default JSON structure for trading preferences
            ObjectNode settingsJson = objectMapper.createObjectNode();
            
            // Default buy amounts [0.1, 0.5, 2]
            ArrayNode buyArray = settingsJson.putArray("buy");
            buyArray.add(0.1);
            buyArray.add(0.5);
            buyArray.add(2.0);
            
            // Default sell amounts [0.1, 0.5, 2]
            ArrayNode sellArray = settingsJson.putArray("sell");
            sellArray.add(0.1);
            sellArray.add(0.5);
            sellArray.add(2.0);
            
            // Default slippage 20%
            settingsJson.put("slippage", 20);
            
            // Default buy_slippage [20]
            ArrayNode buySlippageArray = settingsJson.putArray("buy_slippage");
            buySlippageArray.add(20);
            
            // Default sell_slippage [20]
            ArrayNode sellSlippageArray = settingsJson.putArray("sell_slippage");
            sellSlippageArray.add(20);
            
            // Default EMA [1, 3]
            ArrayNode emaArray = settingsJson.putArray("EMA");
            emaArray.add(1);
            emaArray.add(3);
            
            // Default RSI 3
            settingsJson.put("RSI", 3);
            
            // Default speed MEDIUM
            settingsJson.put("speed", "MEDIUM");
            
            defaultSettings.setSettings(settingsJson);
            
            return userSettingsService.save(defaultSettings);
        }
    }
    
    /**
     * Display the trading preferences page with current settings
     */
    private void displayTradingPreferences(Update update, UserSettings userSettings) {
        try {
            JsonNode settingsJson = userSettings.getSettings();
            
            // Create keyboard with amounts as buttons
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Buy amounts header button (non-interactive)
            List<InlineKeyboardButton> buyHeaderRow = new ArrayList<>();
            InlineKeyboardButton buyHeader = new InlineKeyboardButton();
            buyHeader.setText("Buy Amounts (SOL)");
            buyHeader.setCallbackData("none"); // No action
            buyHeaderRow.add(buyHeader);
            keyboard.add(buyHeaderRow);
            
            // Buy amount buttons row
            List<InlineKeyboardButton> buyAmountsRow = new ArrayList<>();
            JsonNode buyAmounts = settingsJson.get("buy");
            if (buyAmounts != null && buyAmounts.isArray()) {
                for (int i = 0; i < buyAmounts.size(); i++) {
                    InlineKeyboardButton amountBtn = new InlineKeyboardButton();
                    amountBtn.setText(String.format("%.2f", buyAmounts.get(i).asDouble()));
                    amountBtn.setCallbackData("edit_buy_amount:" + i);
                    buyAmountsRow.add(amountBtn);
                }
            }
            keyboard.add(buyAmountsRow);
            
            // Buy slippage button
            List<InlineKeyboardButton> buySlippageRow = new ArrayList<>();
            InlineKeyboardButton buySlippageBtn = new InlineKeyboardButton();
            int buySlippage = 20;
            if (settingsJson.has("buy_slippage") && settingsJson.get("buy_slippage").isArray() && 
                settingsJson.get("buy_slippage").size() > 0) {
                buySlippage = settingsJson.get("buy_slippage").get(0).asInt();
            }
            buySlippageBtn.setText("Buy Slippage: " + buySlippage + "%");
            buySlippageBtn.setCallbackData("edit_buy_slippage");
            buySlippageRow.add(buySlippageBtn);
            keyboard.add(buySlippageRow);
            
            // Sell amounts header button (non-interactive)
            List<InlineKeyboardButton> sellHeaderRow = new ArrayList<>();
            InlineKeyboardButton sellHeader = new InlineKeyboardButton();
            sellHeader.setText("Sell Amounts (SOL)");
            sellHeader.setCallbackData("none"); // No action
            sellHeaderRow.add(sellHeader);
            keyboard.add(sellHeaderRow);
            
            // Sell amount buttons row
            List<InlineKeyboardButton> sellAmountsRow = new ArrayList<>();
            JsonNode sellAmounts = settingsJson.get("sell");
            if (sellAmounts != null && sellAmounts.isArray()) {
                for (int i = 0; i < sellAmounts.size(); i++) {
                    InlineKeyboardButton amountBtn = new InlineKeyboardButton();
                    amountBtn.setText(String.format("%.2f", sellAmounts.get(i).asDouble()));
                    amountBtn.setCallbackData("edit_sell_amount:" + i);
                    sellAmountsRow.add(amountBtn);
                }
            }
            keyboard.add(sellAmountsRow);
            
            // Sell slippage button
            List<InlineKeyboardButton> sellSlippageRow = new ArrayList<>();
            InlineKeyboardButton sellSlippageBtn = new InlineKeyboardButton();
            int sellSlippage = 50;
            if (settingsJson.has("sell_slippage") && settingsJson.get("sell_slippage").isArray() && 
                settingsJson.get("sell_slippage").size() > 0) {
                sellSlippage = settingsJson.get("sell_slippage").get(0).asInt();
            }
            sellSlippageBtn.setText("Sell Slippage: " + sellSlippage + "%");
            sellSlippageBtn.setCallbackData("edit_sell_slippage");
            sellSlippageRow.add(sellSlippageBtn);
            keyboard.add(sellSlippageRow);
            
            // Chart Settings Header
            List<InlineKeyboardButton> chartHeaderRow = new ArrayList<>();
            InlineKeyboardButton chartHeader = new InlineKeyboardButton();
            chartHeader.setText("Chart Settings");
            chartHeader.setCallbackData("none");
            chartHeaderRow.add(chartHeader);
            keyboard.add(chartHeaderRow);
            
            // EMA and RSI buttons in one row
            List<InlineKeyboardButton> indicatorsRow = new ArrayList<>();
            
            // EMA button
            InlineKeyboardButton emaBtn = new InlineKeyboardButton();
            String emaText = "EMA: ";
            if (settingsJson.has("EMA") && settingsJson.get("EMA").isArray()) {
                ArrayNode emaArray = (ArrayNode) settingsJson.get("EMA");
                if (emaArray.size() > 0) {
                    emaText += emaArray.get(0).asInt();
                    for (int i = 1; i < emaArray.size(); i++) {
                        emaText += "," + emaArray.get(i).asInt();
                    }
                }
            } else {
                emaText += "1,3";
            }
            emaBtn.setText(emaText);
            emaBtn.setCallbackData("edit_ema");
            indicatorsRow.add(emaBtn);
            
            // RSI button
            InlineKeyboardButton rsiBtn = new InlineKeyboardButton();
            int rsi = settingsJson.has("RSI") ? settingsJson.get("RSI").asInt() : 3;
            rsiBtn.setText("RSI: " + rsi);
            rsiBtn.setCallbackData("edit_rsi");
            indicatorsRow.add(rsiBtn);
            
            keyboard.add(indicatorsRow);
            
            // Network Options Header
            List<InlineKeyboardButton> networkHeaderRow = new ArrayList<>();
            InlineKeyboardButton networkHeader = new InlineKeyboardButton();
            networkHeader.setText("Network Options");
            networkHeader.setCallbackData("none");
            networkHeaderRow.add(networkHeader);
            keyboard.add(networkHeaderRow);
            
            // Speed button
            List<InlineKeyboardButton> speedRow = new ArrayList<>();
            InlineKeyboardButton speedBtn = new InlineKeyboardButton();
            String speed = settingsJson.has("speed") ? settingsJson.get("speed").asText() : "MEDIUM";
            speedBtn.setText("Transaction Speed: " + speed);
            speedBtn.setCallbackData("edit_speed");
            speedRow.add(speedBtn);
            keyboard.add(speedRow);
            
            // Back button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Back to Settings");
            backButton.setCallbackData("settings");
            backRow.add(backButton);
            keyboard.add(backRow);
            
            // Convert to button handlers and display
            ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
            
            // Edit existing message or send new one based on the update type
            if (update.hasCallbackQuery()) {
                MessageUtils.editMessage(update, "*Trading Preferences*\n\nClick on any item to edit it:", buttonHandlers);
            } else {
                MessageUtils.sendInlineKeyboard(update, "*Trading Preferences*\n\nClick on any item to edit it:", buttonHandlers);
            }
            
        } catch (Exception e) {
            log.error("Error displaying trading preferences", e);
            MessageUtils.sendMessage(update, "Error displaying trading preferences: " + e.getMessage());
        }
    }
    
    /**
     * Create keyboard for trading preferences
     */
    private List<List<InlineKeyboardButton>> createPreferencesKeyboard() {
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        // Buy amounts edit button
        List<InlineKeyboardButton> buyRow = new ArrayList<>();
        InlineKeyboardButton buyButton = new InlineKeyboardButton();
        buyButton.setText("📝 Edit Buy Amounts");
        buyButton.setCallbackData("edit_buy_amounts");
        buyRow.add(buyButton);
        keyboard.add(buyRow);
        
        // Sell percentages edit button
        List<InlineKeyboardButton> sellRow = new ArrayList<>();
        InlineKeyboardButton sellButton = new InlineKeyboardButton();
        sellButton.setText("📝 Edit Sell Amounts");
        sellButton.setCallbackData("edit_sell_amounts");
        sellRow.add(sellButton);
        keyboard.add(sellRow);
        
        // Slippage edit button
        List<InlineKeyboardButton> slippageRow = new ArrayList<>();
        InlineKeyboardButton slippageButton = new InlineKeyboardButton();
        slippageButton.setText("📝 Edit Slippage");
        slippageButton.setCallbackData("edit_slippage");
        slippageRow.add(slippageButton);
        keyboard.add(slippageRow);
        
        // Back button
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("⬅️ Back to Settings");
        backButton.setCallbackData("settings");
        backRow.add(backButton);
        keyboard.add(backRow);
        
        return keyboard;
    }
    
    /**
     * Helper method to convert keyboard to button handlers
     */
    private ButtonHandler[][] convertKeyboardToButtonHandlers(List<List<InlineKeyboardButton>> keyboard) {
        ButtonHandler[][] buttonHandlers = new ButtonHandler[keyboard.size()][];
        for (int i = 0; i < keyboard.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            buttonHandlers[i] = new ButtonHandler[row.size()];
            
            for (int j = 0; j < row.size(); j++) {
                InlineKeyboardButton button = row.get(j);
                final String btnText = button.getText();
                final String btnCallback = button.getCallbackData();
                
                buttonHandlers[i][j] = new ButtonHandler() {
                    @Override
                    public String getButtonText() {
                        return btnText;
                    }
                    
                    @Override
                    public String getCallbackData() {
                        return btnCallback;
                    }
                    
                    @Override
                    public void handleCallback(Update update) {
                        // This won't be called directly
                    }
                };
            }
        }
        return buttonHandlers;
    }
    
    // Add this annotation to trigger registration at startup
    @PostConstruct
    private void registerAllCallbackHandlers() {
        log.info("Registering trading preferences callback handlers");
        registerCallbackHandlers();
    }

    private void registerCallbackHandlers() {
        // Your existing code here...
        
        // Make sure to use both methods of registration consistently
        MessageUtils.registerCallbackHandler("edit_buy_slippage", (update, matcher) -> {
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            
            // First set the state
            stateService.setState(telegramId, ChatState.AWAITING_SLIPPAGE_INPUT);
            
            // Then set state data
            ObjectNode stateData = objectMapper.createObjectNode();
            stateData.put("slippageType", "buy");
            stateService.setStateData(telegramId, stateData);
            
            MessageUtils.sendMessage(update, "Enter buy slippage percentage (1-50):");
        });
        
        MessageUtils.registerCallbackHandler("edit_sell_slippage", (update, matcher) -> {
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            
            // First set the state
            stateService.setState(telegramId, ChatState.AWAITING_SLIPPAGE_INPUT);
            
            // Then set state data
            ObjectNode stateData = objectMapper.createObjectNode();
            stateData.put("slippageType", "sell");
            stateService.setStateData(telegramId, stateData);
            
            MessageUtils.sendMessage(update, "Enter sell slippage percentage (1-50):");
        });
        
        // Register other handlers consistently
        
        log.info("Trading preferences callback handlers registered successfully");
    }
    
    /**
     * Handle editing buy amounts
     */
    public void handleEditBuyAmounts(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserSettings userSettings = getUserSettings(user);
        
        try {
            JsonNode settingsJson = userSettings.getSettings();
            JsonNode buyAmounts = settingsJson.get("buy");
            
            StringBuilder message = new StringBuilder("*Edit Buy Amounts*\n\n");
            message.append("Current buy amounts:\n");
            
            if (buyAmounts != null && buyAmounts.isArray()) {
                for (int i = 0; i < buyAmounts.size(); i++) {
                    message.append(i + 1).append(". ").append(buyAmounts.get(i).asDouble()).append(" SOL\n");
                }
            } else {
                message.append("No buy amounts configured\n");
            }
            
            message.append("\nSelect an amount to edit:");
            
            // Create keyboard with options for each amount
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            if (buyAmounts != null && buyAmounts.isArray()) {
                for (int i = 0; i < buyAmounts.size(); i++) {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText("Edit #" + (i + 1) + ": " + buyAmounts.get(i).asDouble() + " SOL");
                    button.setCallbackData("edit_buy_amount:" + i);
                    row.add(button);
                    keyboard.add(row);
                }
            }
            
            // Back button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Back");
            backButton.setCallbackData("trading_preferences");
            backRow.add(backButton);
            keyboard.add(backRow);
            
            ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
            MessageUtils.editMessage(update, message.toString(), buttonHandlers);
            
        } catch (Exception e) {
            log.error("Error handling edit buy amounts", e);
            MessageUtils.editMessage(update, "Error editing buy amounts: " + e.getMessage(), null);
        }
    }
    
    /**
     * Handle editing sell amounts
     */
    public void handleEditSellAmounts(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserSettings userSettings = getUserSettings(user);
        
        try {
            JsonNode settingsJson = userSettings.getSettings();
            JsonNode sellAmounts = settingsJson.get("sell");
            
            StringBuilder message = new StringBuilder("*Edit Sell Amounts*\n\n");
            message.append("Current sell amounts:\n");
            
            if (sellAmounts != null && sellAmounts.isArray()) {
                for (int i = 0; i < sellAmounts.size(); i++) {
                    message.append(i + 1).append(". ").append(sellAmounts.get(i).asDouble()).append(" SOL\n");
                }
            } else {
                message.append("No sell amounts configured\n");
            }
            
            message.append("\nSelect an amount to edit:");
            
            // Create keyboard with options for each amount
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            if (sellAmounts != null && sellAmounts.isArray()) {
                for (int i = 0; i < sellAmounts.size(); i++) {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText("Edit #" + (i + 1) + ": " + sellAmounts.get(i).asDouble() + " SOL");
                    button.setCallbackData("edit_sell_amount:" + i);
                    row.add(button);
                    keyboard.add(row);
                }
            }
            
            // Back button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Back");
            backButton.setCallbackData("trading_preferences");
            backRow.add(backButton);
            keyboard.add(backRow);
            
            ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
            MessageUtils.editMessage(update, message.toString(), buttonHandlers);
            
        } catch (Exception e) {
            log.error("Error handling edit sell amounts", e);
            MessageUtils.editMessage(update, "Error editing sell amounts: " + e.getMessage(), null);
        }
    }
    
    /**
     * Handle editing slippage
     */
    public void handleEditSlippage(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Set state to wait for new slippage value
        stateService.setState(telegramId, ChatState.AWAITING_SLIPPAGE_INPUT);
        
        // Create state data
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);
        
        StringBuilder message = new StringBuilder("*Edit Slippage Tolerance*\n\n");
        message.append("Please enter a new slippage tolerance percentage (1-50):\n\n");
        message.append("Example: Enter `20` for 20% slippage tolerance.");
        
        MessageUtils.editMessage(update, message.toString(), null);
        
        // Add cancel button as a new message
        ButtonHandler[][] cancelButton = new ButtonHandler[1][1];
        cancelButton[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        MessageUtils.sendInlineKeyboard(update, "Or click below to cancel:", cancelButton);
    }
    
    /**
     * Handle editing a specific buy amount
     */
    public void handleEditSpecificBuyAmount(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        int index = Integer.parseInt(matcher.group(1));
        
        // Set state to wait for new amount
        stateService.setState(telegramId, ChatState.AWAITING_BUY_AMOUNT_INPUT);
        
        // Create state data with the index to edit
        ObjectNode stateData = objectMapper.createObjectNode();
        stateData.put("index", index);
        stateService.setStateData(telegramId, stateData);
        
        // Get current amount
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserSettings userSettings = getUserSettings(user);
        JsonNode settingsJson = userSettings.getSettings();
        double currentAmount = settingsJson.get("buy").get(index).asDouble();
        
        StringBuilder message = new StringBuilder();
        message.append("*Current Buy Amount:* ").append(String.format("%.2f", currentAmount)).append(" SOL\n\n");
        message.append("Enter a new amount in SOL:\n");
        message.append("Example: Enter `0.5` for 0.5 SOL");
        
        MessageUtils.editMessage(update, message.toString(), null);
        
        // Add cancel button as a new message
        ButtonHandler[][] cancelButton = new ButtonHandler[1][1];
        cancelButton[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        MessageUtils.sendInlineKeyboard(update, "Or click below to cancel:", cancelButton);
    }
    
    /**
     * Handle editing a specific sell amount
     */
    public void handleEditSpecificSellAmount(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        int index = Integer.parseInt(matcher.group(1));
        
        // Set state to wait for new amount
        stateService.setState(telegramId, ChatState.AWAITING_SELL_AMOUNT_INPUT);
        
        // Create state data with the index to edit
        ObjectNode stateData = objectMapper.createObjectNode();
        stateData.put("index", index);
        stateService.setStateData(telegramId, stateData);
        
        // Get current amount
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserSettings userSettings = getUserSettings(user);
        JsonNode settingsJson = userSettings.getSettings();
        double currentAmount = settingsJson.get("sell").get(index).asDouble();
        
        StringBuilder message = new StringBuilder();
        message.append("*Current Sell Amount:* ").append(String.format("%.2f", currentAmount)).append(" SOL\n\n");
        message.append("Enter a new amount in SOL:\n");
        message.append("Example: Enter `0.5` for 0.5 SOL");
        
        MessageUtils.editMessage(update, message.toString(), null);
        
        // Add cancel button as a new message
        ButtonHandler[][] cancelButton = new ButtonHandler[1][1];
        cancelButton[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        MessageUtils.sendInlineKeyboard(update, "Or click below to cancel:", cancelButton);
    }
    
    /**
     * Handle editing EMA settings
     */
    public void handleEditEma(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Set state to wait for new EMA values
        stateService.setState(telegramId, ChatState.AWAITING_EMA_INPUT);
        
        // Create state data
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);
        
        StringBuilder message = new StringBuilder("*Edit EMA Values*\n\n");
        message.append("Please enter EMA values separated by commas:\n\n");
        message.append("Example: `1,3` for EMA-1 and EMA-3");
        
        MessageUtils.editMessage(update, message.toString(), null);
        
        // Add cancel button as a new message
        ButtonHandler[][] cancelButton = new ButtonHandler[1][1];
        cancelButton[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        MessageUtils.sendInlineKeyboard(update, "Or click below to cancel:", cancelButton);
    }
    
    /**
     * Handle editing RSI settings
     */
    public void handleEditRsi(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Set state to wait for new RSI value
        stateService.setState(telegramId, ChatState.AWAITING_RSI_INPUT);
        
        // Create state data
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);
        
        StringBuilder message = new StringBuilder("*Edit RSI Period*\n\n");
        message.append("Please enter the RSI period (1-14):\n\n");
        message.append("Example: `3` for RSI-3");
        
        MessageUtils.editMessage(update, message.toString(), null);
        
        // Add cancel button as a new message
        ButtonHandler[][] cancelButton = new ButtonHandler[1][1];
        cancelButton[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        MessageUtils.sendInlineKeyboard(update, "Or click below to cancel:", cancelButton);
    }
    
    /**
     * Handle editing Speed settings
     */
    public void handleEditSpeed(Update update, java.util.regex.Matcher matcher) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserSettings userSettings = getUserSettings(user);
        
        // Create speed selection buttons
        ButtonHandler[][] keyboard = new ButtonHandler[4][1];
        
        keyboard[0][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "FAST (Higher fees)"; }
            @Override public String getCallbackData() { return "speed_fast"; }
            @Override public void handleCallback(Update update) { 
                updateSpeed(update, telegramId, "FAST");
            }
        };
        
        keyboard[1][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "MEDIUM (Default)"; }
            @Override public String getCallbackData() { return "speed_medium"; }
            @Override public void handleCallback(Update update) { 
                updateSpeed(update, telegramId, "MEDIUM");
            }
        };
        
        keyboard[2][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "SLOW (Lower fees)"; }
            @Override public String getCallbackData() { return "speed_slow"; }
            @Override public void handleCallback(Update update) { 
                updateSpeed(update, telegramId, "SLOW");
            }
        };
        
        keyboard[3][0] = new ButtonHandler() {
            @Override public String getButtonText() { return "❌ Cancel"; }
            @Override public String getCallbackData() { return "trading_preferences"; }
            @Override public void handleCallback(Update update) {}
        };
        
        // Register these handlers
        MessageUtils.registerCallbackHandler("speed_fast", (u, m) -> updateSpeed(u, telegramId, "FAST"));
        MessageUtils.registerCallbackHandler("speed_medium", (u, m) -> updateSpeed(u, telegramId, "MEDIUM"));
        MessageUtils.registerCallbackHandler("speed_slow", (u, m) -> updateSpeed(u, telegramId, "SLOW"));
        
        StringBuilder message = new StringBuilder("*Select Transaction Speed*\n\n");
        message.append("FAST: Higher fees, faster confirmation\n");
        message.append("MEDIUM: Balanced speed and fees\n");
        message.append("SLOW: Lower fees, may take longer\n\n");
        message.append("Current setting: ").append(userSettings.getSettings().has("speed") ? 
                                                   userSettings.getSettings().get("speed").asText() : "MEDIUM");
        
        MessageUtils.editMessage(update, message.toString(), keyboard);
    }
    
    /**
     * Update transaction speed setting
     */
    private void updateSpeed(Update update, Long telegramId, String speed) {
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Get user settings and update the speed
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Update the speed
            settingsJson.put("speed", speed);
            
            // Save updated settings
            userSettings.setSettings(settingsJson);
            userSettingsService.save(userSettings);
            
            MessageUtils.sendMessage(update, "✅ Transaction speed updated to " + speed);
            
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            
            // Show updated trading preferences
            sendTradingPreferencesView(update, telegramId);
            
        } catch (Exception e) {
            log.error("Error processing speed selection", e);
            MessageUtils.sendMessage(update, "❌ Error updating speed: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    /**
     * Process new EMA values from user input
     */
    public void processEmaInput(Update update, String input) {
        Long telegramId = update.getMessage().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Parse the comma-separated EMA values
            String[] emaValues = input.split(",");
            if (emaValues.length == 0) {
                MessageUtils.sendMessage(update, "❌ Please enter at least one EMA value");
                return;
            }
            
            // Get user settings and update the EMA values
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Create new EMA array
            ArrayNode emaArray = settingsJson.putArray("EMA");
            for (String emaValue : emaValues) {
                try {
                    int ema = Integer.parseInt(emaValue.trim());
                    if (ema <= 0) {
                        MessageUtils.sendMessage(update, "❌ EMA values must be positive integers");
                        return;
                    }
                    emaArray.add(ema);
                } catch (NumberFormatException e) {
                    MessageUtils.sendMessage(update, "❌ Invalid EMA value: " + emaValue);
                    return;
                }
            }
            
            // Save updated settings
            userSettings.setSettings(settingsJson);
            userSettingsService.save(userSettings);
            
            MessageUtils.sendMessage(update, "✅ EMA values updated");
            
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            
            // Show updated trading preferences
            sendTradingPreferencesView(update, telegramId);
            
        } catch (Exception e) {
            log.error("Error processing EMA input", e);
            MessageUtils.sendMessage(update, "❌ Error updating EMA values: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    /**
     * Process new RSI value from user input
     */
    public void processRsiInput(Update update, String input) {
        Long telegramId = update.getMessage().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Parse the RSI value
            int rsi = Integer.parseInt(input.trim());
            
            if (rsi < 1 || rsi > 14) {
                MessageUtils.sendMessage(update, "❌ RSI period must be between 1 and 14");
                return;
            }
            
            // Get user settings and update the RSI value
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Update the RSI value
            settingsJson.put("RSI", rsi);
            
            // Save updated settings
            userSettings.setSettings(settingsJson);
            userSettingsService.save(userSettings);
            
            MessageUtils.sendMessage(update, "✅ RSI period updated to " + rsi);
            
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            
            // Show updated trading preferences
            sendTradingPreferencesView(update, telegramId);
            
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Invalid number format. Please enter a valid integer.");
        } catch (Exception e) {
            log.error("Error processing RSI input", e);
            MessageUtils.sendMessage(update, "❌ Error updating RSI: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    // Add a new method to display trading preferences via a new message
    public void sendTradingPreferencesView(Update update, Long telegramId) {
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        UserSettings userSettings = getUserSettings(user);
        
        try {
            JsonNode settingsJson = userSettings.getSettings();
            
            // Create keyboard with amounts as buttons
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            // Buy amounts header button (non-interactive)
            List<InlineKeyboardButton> buyHeaderRow = new ArrayList<>();
            InlineKeyboardButton buyHeader = new InlineKeyboardButton();
            buyHeader.setText("Buy Amounts (SOL)");
            buyHeader.setCallbackData("none"); // No action
            buyHeaderRow.add(buyHeader);
            keyboard.add(buyHeaderRow);
            
            // Buy amount buttons row
            List<InlineKeyboardButton> buyAmountsRow = new ArrayList<>();
            JsonNode buyAmounts = settingsJson.get("buy");
            if (buyAmounts != null && buyAmounts.isArray()) {
                for (int i = 0; i < buyAmounts.size(); i++) {
                    InlineKeyboardButton amountBtn = new InlineKeyboardButton();
                    amountBtn.setText(String.format("%.2f", buyAmounts.get(i).asDouble()));
                    amountBtn.setCallbackData("edit_buy_amount:" + i);
                    buyAmountsRow.add(amountBtn);
                }
            }
            keyboard.add(buyAmountsRow);
            
            // Buy slippage button
            List<InlineKeyboardButton> buySlippageRow = new ArrayList<>();
            InlineKeyboardButton buySlippageBtn = new InlineKeyboardButton();
            int buySlippage = 20;
            if (settingsJson.has("buy_slippage") && settingsJson.get("buy_slippage").isArray() && 
                settingsJson.get("buy_slippage").size() > 0) {
                buySlippage = settingsJson.get("buy_slippage").get(0).asInt();
            }
            buySlippageBtn.setText("Buy Slippage: " + buySlippage + "%");
            buySlippageBtn.setCallbackData("edit_buy_slippage");
            buySlippageRow.add(buySlippageBtn);
            keyboard.add(buySlippageRow);
            
            // Sell amounts header button (non-interactive)
            List<InlineKeyboardButton> sellHeaderRow = new ArrayList<>();
            InlineKeyboardButton sellHeader = new InlineKeyboardButton();
            sellHeader.setText("Sell Amounts (SOL)");
            sellHeader.setCallbackData("none"); // No action
            sellHeaderRow.add(sellHeader);
            keyboard.add(sellHeaderRow);
            
            // Sell amount buttons row
            List<InlineKeyboardButton> sellAmountsRow = new ArrayList<>();
            JsonNode sellAmounts = settingsJson.get("sell");
            if (sellAmounts != null && sellAmounts.isArray()) {
                for (int i = 0; i < sellAmounts.size(); i++) {
                    InlineKeyboardButton amountBtn = new InlineKeyboardButton();
                    amountBtn.setText(String.format("%.2f", sellAmounts.get(i).asDouble()));
                    amountBtn.setCallbackData("edit_sell_amount:" + i);
                    sellAmountsRow.add(amountBtn);
                }
            }
            keyboard.add(sellAmountsRow);
            
            // Sell slippage button
            List<InlineKeyboardButton> sellSlippageRow = new ArrayList<>();
            InlineKeyboardButton sellSlippageBtn = new InlineKeyboardButton();
            int sellSlippage = 50;
            if (settingsJson.has("sell_slippage") && settingsJson.get("sell_slippage").isArray() && 
                settingsJson.get("sell_slippage").size() > 0) {
                sellSlippage = settingsJson.get("sell_slippage").get(0).asInt();
            }
            sellSlippageBtn.setText("Sell Slippage: " + sellSlippage + "%");
            sellSlippageBtn.setCallbackData("edit_sell_slippage");
            sellSlippageRow.add(sellSlippageBtn);
            keyboard.add(sellSlippageRow);
            
            // Chart Settings Header
            List<InlineKeyboardButton> chartHeaderRow = new ArrayList<>();
            InlineKeyboardButton chartHeader = new InlineKeyboardButton();
            chartHeader.setText("Chart Settings");
            chartHeader.setCallbackData("none");
            chartHeaderRow.add(chartHeader);
            keyboard.add(chartHeaderRow);
            
            // EMA and RSI buttons in one row
            List<InlineKeyboardButton> indicatorsRow = new ArrayList<>();
            
            // EMA button
            InlineKeyboardButton emaBtn = new InlineKeyboardButton();
            String emaText = "EMA: ";
            if (settingsJson.has("EMA") && settingsJson.get("EMA").isArray()) {
                ArrayNode emaArray = (ArrayNode) settingsJson.get("EMA");
                if (emaArray.size() > 0) {
                    emaText += emaArray.get(0).asInt();
                    for (int i = 1; i < emaArray.size(); i++) {
                        emaText += "," + emaArray.get(i).asInt();
                    }
                }
            } else {
                emaText += "1,3";
            }
            emaBtn.setText(emaText);
            emaBtn.setCallbackData("edit_ema");
            indicatorsRow.add(emaBtn);
            
            // RSI button
            InlineKeyboardButton rsiBtn = new InlineKeyboardButton();
            int rsi = settingsJson.has("RSI") ? settingsJson.get("RSI").asInt() : 3;
            rsiBtn.setText("RSI: " + rsi);
            rsiBtn.setCallbackData("edit_rsi");
            indicatorsRow.add(rsiBtn);
            
            keyboard.add(indicatorsRow);
            
            // Network Options Header
            List<InlineKeyboardButton> networkHeaderRow = new ArrayList<>();
            InlineKeyboardButton networkHeader = new InlineKeyboardButton();
            networkHeader.setText("Network Options");
            networkHeader.setCallbackData("none");
            networkHeaderRow.add(networkHeader);
            keyboard.add(networkHeaderRow);
            
            // Speed button
            List<InlineKeyboardButton> speedRow = new ArrayList<>();
            InlineKeyboardButton speedBtn = new InlineKeyboardButton();
            String speed = settingsJson.has("speed") ? settingsJson.get("speed").asText() : "MEDIUM";
            speedBtn.setText("Transaction Speed: " + speed);
            speedBtn.setCallbackData("edit_speed");
            speedRow.add(speedBtn);
            keyboard.add(speedRow);
            
            // Back button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Back to Settings");
            backButton.setCallbackData("settings");
            backRow.add(backButton);
            keyboard.add(backRow);
            
            // Convert to button handlers and display
            ButtonHandler[][] buttonHandlers = convertKeyboardToButtonHandlers(keyboard);
            
            // Send a new message instead of editing
            MessageUtils.sendInlineKeyboard(update, "*Trading Preferences*\n\nClick on any item to edit it:", buttonHandlers);
            
        } catch (Exception e) {
            log.error("Error displaying trading preferences", e);
            MessageUtils.sendMessage(update, "Error displaying trading preferences: " + e.getMessage());
        }
    }

    // Implementation of ButtonHandler interface methods
    @Override
    public String getButtonText() {
        return "Trading Preferences";
    }
    
    @Override
    public String getCallbackData() {
        return "trading_preferences";
    }
    
    @Override
    public void handleCallback(Update update) {
        executeVoid(update);
    }
    
    /**
     * Process a new buy amount from user input
     */
    public void processBuyAmountInput(Update update, String input) {
        Long telegramId = update.getMessage().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get the state data to find which index to update
        ObjectNode stateData = (ObjectNode) stateService.getStateData(telegramId);
        if (stateData == null || !stateData.has("index")) {
            MessageUtils.sendMessage(update, "❌ Error: Missing state data");
            stateService.setState(telegramId, ChatState.IDLE);
            return;
        }
        
        int index = stateData.get("index").asInt();
        
        try {
            // Parse the input as a double
            double amount = Double.parseDouble(input);
            
            if (amount <= 0) {
                MessageUtils.sendMessage(update, "❌ Amount must be greater than 0");
                return;
            }
            
            // Get user settings and update the buy amount at the specified index
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Update the buy amount
            if (settingsJson.has("buy") && settingsJson.get("buy").isArray()) {
                ArrayNode buyArray = (ArrayNode) settingsJson.get("buy");
                if (index >= 0 && index < buyArray.size()) {
                    buyArray.set(index, amount);
                    
                    // Save updated settings
                    userSettings.setSettings(settingsJson);
                    userSettingsService.save(userSettings);
                    
                    MessageUtils.sendMessage(update, "✅ Buy amount #" + (index + 1) + " updated to " + amount + " SOL");
                    
                    // Reset state
                    stateService.setState(telegramId, ChatState.IDLE);
                    
                    // Show updated trading preferences
                    sendTradingPreferencesView(update, telegramId);
                } else {
                    MessageUtils.sendMessage(update, "❌ Invalid index");
                }
            } else {
                MessageUtils.sendMessage(update, "❌ Buy amounts configuration is invalid");
            }
            
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Invalid number format. Please enter a valid decimal number.");
        } catch (Exception e) {
            log.error("Error processing buy amount input", e);
            MessageUtils.sendMessage(update, "❌ Error updating buy amount: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    // Add new method to handle all incoming messages based on state
    public void handleMessage(Update update) {
        Message message = update.getMessage();
        Long telegramId = message.getFrom().getId();
        String messageText = message.getText();
        
        ChatState currentState = stateService.getState(telegramId);
        
        // If there's no current state, initialize 
        if (currentState == null || currentState == ChatState.IDLE) {
            return;
        }
        
        ObjectNode stateData = stateService.getStateData(telegramId);
        
        // Initialize stateData if null
        if (stateData == null) {
            stateData = objectMapper.createObjectNode();
            stateService.setStateData(telegramId, stateData);
        }
        
        switch (currentState) {
            case AWAITING_BUY_AMOUNT_INPUT:
                processBuyAmountInput(update, messageText);
                break;
            case AWAITING_SELL_AMOUNT_INPUT:
                processSellAmountInput(update, messageText);
                break;
            case AWAITING_SLIPPAGE_INPUT:
                processSlippageInput(update, messageText);
                break;
            case AWAITING_EMA_INPUT:
                processEmaInput(update, messageText);
                break;
            case AWAITING_RSI_INPUT:
                processRsiInput(update, messageText);
                break;
            default:
                log.warn("Unexpected state in trading preferences: {}", currentState);
                break;
        }
    }

    // Add missing processSellAmountInput method
    /**
     * Process a new sell amount from user input
     */
    public void processSellAmountInput(Update update, String input) {
        Long telegramId = update.getMessage().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get the state data to find which index to update
        ObjectNode stateData = (ObjectNode) stateService.getStateData(telegramId);
        if (stateData == null || !stateData.has("index")) {
            MessageUtils.sendMessage(update, "❌ Error: Missing state data");
            stateService.setState(telegramId, ChatState.IDLE);
            return;
        }
        
        int index = stateData.get("index").asInt();
        
        try {
            // Parse the input as a double
            double amount = Double.parseDouble(input);
            
            if (amount <= 0) {
                MessageUtils.sendMessage(update, "❌ Amount must be greater than 0");
                return;
            }
            
            // Get user settings and update the sell amount at the specified index
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Update the sell amount
            if (settingsJson.has("sell") && settingsJson.get("sell").isArray()) {
                ArrayNode sellArray = (ArrayNode) settingsJson.get("sell");
                if (index >= 0 && index < sellArray.size()) {
                    sellArray.set(index, amount);
                    
                    // Save updated settings
                    userSettings.setSettings(settingsJson);
                    userSettingsService.save(userSettings);
                    
                    MessageUtils.sendMessage(update, "✅ Sell amount #" + (index + 1) + " updated to " + amount + " SOL");
                    
                    // Reset state
                    stateService.setState(telegramId, ChatState.IDLE);
                    
                    // Show updated trading preferences
                    sendTradingPreferencesView(update, telegramId);
                } else {
                    MessageUtils.sendMessage(update, "❌ Invalid index");
                }
            } else {
                MessageUtils.sendMessage(update, "❌ Sell amounts configuration is invalid");
            }
            
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Invalid number format. Please enter a valid decimal number.");
        } catch (Exception e) {
            log.error("Error processing sell amount input", e);
            MessageUtils.sendMessage(update, "❌ Error updating sell amount: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
    
    /**
     * Process a new slippage value from user input
     */
    public void processSlippageInput(Update update, String input) {
        Long telegramId = update.getMessage().getFrom().getId();
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get state data to check if we're editing buy or sell slippage
        ObjectNode stateData = (ObjectNode) stateService.getStateData(telegramId);
        String slippageType = stateData != null && stateData.has("slippageType") ? 
                              stateData.get("slippageType").asText() : "buy";
        
        try {
            // Parse the input as an integer
            int slippage = Integer.parseInt(input);
            
            if (slippage < 1 || slippage > 50) {
                MessageUtils.sendMessage(update, "❌ Slippage must be between 1 and 50");
                return;
            }
            
            // Get user settings and update the appropriate slippage
            UserSettings userSettings = getUserSettings(user);
            JsonNode settingsJsonNode = userSettings.getSettings();
            ObjectNode settingsJson = (ObjectNode) settingsJsonNode;
            
            // Update based on slippage type
            if ("buy".equals(slippageType)) {
                // Update buy slippage array
                ArrayNode buySlippageArray;
                if (settingsJson.has("buy_slippage") && settingsJson.get("buy_slippage").isArray()) {
                    buySlippageArray = (ArrayNode) settingsJson.get("buy_slippage");
                    buySlippageArray.removeAll();
                } else {
                    buySlippageArray = settingsJson.putArray("buy_slippage");
                }
                buySlippageArray.add(slippage);
                MessageUtils.sendMessage(update, "✅ Buy slippage updated to " + slippage + "%");
            } else if ("sell".equals(slippageType)) {
                // Update sell slippage array
                ArrayNode sellSlippageArray;
                if (settingsJson.has("sell_slippage") && settingsJson.get("sell_slippage").isArray()) {
                    sellSlippageArray = (ArrayNode) settingsJson.get("sell_slippage");
                    sellSlippageArray.removeAll();
                } else {
                    sellSlippageArray = settingsJson.putArray("sell_slippage");
                }
                sellSlippageArray.add(slippage);
                MessageUtils.sendMessage(update, "✅ Sell slippage updated to " + slippage + "%");
            }
            
            // Save updated settings
            userSettings.setSettings(settingsJson);
            userSettingsService.save(userSettings);
            
            // Reset state
            stateService.setState(telegramId, ChatState.IDLE);
            
            // Show updated trading preferences
            sendTradingPreferencesView(update, telegramId);
            
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Invalid number format. Please enter a valid integer.");
        } catch (Exception e) {
            log.error("Error processing slippage input", e);
            MessageUtils.sendMessage(update, "❌ Error updating slippage: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
        }
    }
}