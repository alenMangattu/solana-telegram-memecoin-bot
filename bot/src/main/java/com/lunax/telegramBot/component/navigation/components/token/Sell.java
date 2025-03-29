package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.UserSettings;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.UserSettingsService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.service.TransactionSendingService;
import com.lunax.telegramBot.model.db.LimitOrder;
import com.lunax.telegramBot.model.repository.UserSettingsRepository;
import com.lunax.telegramBot.model.db.OrderAction;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
public class Sell implements StateHandler {

    @Autowired
    private StateService stateService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private UserSettingsService userSettingsService;
    
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private TransactionSendingService transactionSendingService;
    
    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    // Define callback data constants
    private static final String CONFIRM_SELL = "sell_confirm";
    private static final String CANCEL_SELL = "sell_cancel";
    private static final String TOGGLE_LIMIT_ORDER = "sell_toggle_limit";
    private static final String CUSTOM_AMOUNT = "sell_custom_amount";
    private static final String CUSTOM_SLIPPAGE = "sell_custom_slippage";
    private static final String SELL_TOKEN = "sell_token";

    public Sell() {
        // Register button handlers with the HandlerRegistry in the constructor
    }
    
    @PostConstruct
    public void init() {
        // Register the sell_token handler that's used from the token info page
        ButtonHandler sellTokenHandler = new ButtonHandler() {
            @Override
            public String getButtonText() { 
                return "Sell Token"; 
            }
            
            @Override
            public String getCallbackData() { 
                return SELL_TOKEN; 
            }
            
            @Override
            public void handleCallback(Update update) { 
                Long userId = update.getCallbackQuery().getFrom().getId();
                log.debug("Handling sell token callback for user: {}", userId);
                executeVoid(update); 
            }
        };
        
        getHandlerRegistry().registerHandler(SELL_TOKEN, sellTokenHandler);
        log.info("Registered sell_token handler");
    }

    @Override
    public void executeVoid(Update update) {
        Long userId = null;
        
        if (update.hasCallbackQuery()) {
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();
            log.debug("Received callback data: {}", callbackData);
            
            if (callbackData.equals(SELL_TOKEN)) {
                log.debug("User {} started sell process", userId);
                showSellScreen(update, userId);
                return;
            } else if (callbackData.equals(CONFIRM_SELL)) {
                log.debug("User {} confirmed sell", userId);
                handleConfirmSell(update, userId);
                return;
            } else if (callbackData.equals(CANCEL_SELL)) {
                log.debug("User {} canceled sell", userId);
                handleCancelSell(update, userId);
                return;
            } else if (callbackData.equals(TOGGLE_LIMIT_ORDER)) {
                log.debug("User {} toggled limit order", userId);
                toggleLimitOrder(update, userId);
                return;
            } else if (callbackData.equals(CUSTOM_AMOUNT)) {
                log.debug("User {} requested custom amount", userId);
                handleCustomAmount(update, userId);
                return;
            } else if (callbackData.equals(CUSTOM_SLIPPAGE)) {
                log.debug("User {} requested custom slippage", userId);
                handleCustomSlippage(update, userId);
                return;
            } else if (callbackData.startsWith("sell_amount_")) {
                String amountStr = callbackData.substring("sell_amount_".length());
                log.debug("User {} selected sell amount: {}", userId, amountStr);
                updateSellAmount(update, userId, amountStr);
                return;
            } else if (callbackData.startsWith("sell_slippage_")) {
                String slippageStr = callbackData.substring("sell_slippage_".length());
                log.debug("User {} selected slippage: {}", userId, slippageStr);
                updateSlippage(update, userId, slippageStr);
                return;
            }
        } else if (update.hasMessage()) {
            userId = update.getMessage().getFrom().getId();
            log.debug("Received message from user: {}", userId);
            
            // Handle text input based on state
            ChatState currentState = stateService.getState(userId);
            log.debug("Current state for user {}: {}", userId, currentState);
            if (currentState == ChatState.AWAITING_SELL_AMOUNT) {
                String amount = update.getMessage().getText();
                log.debug("User {} entered custom sell amount: {}", userId, amount);
                updateSellAmount(update, userId, amount);
                return;
            } else if (currentState == ChatState.AWAITING_SELL_SLIPPAGE) {
                String slippage = update.getMessage().getText();
                log.debug("User {} entered custom slippage: {}", userId, slippage);
                updateSlippage(update, userId, slippage);
                return;
            }
        }

        // Default action: show the sell confirmation screen
        showSellScreen(update, userId);
    }
    
    private void showSellScreen(Update update, Long userId) {
        // Get state data with token info
        ObjectNode stateData = stateService.getStateData(userId);
        if (stateData == null || !stateData.has("tokenInfo")) {
            log.debug("No token selected for user {}", userId);
            MessageUtils.sendMessage(update, "Please enter a valid Solana token address:");
            return;
        }
        
        // Get token information
        JsonNode tokenInfo = stateData.get("tokenInfo");
        
        // Additional validation checks
        if (!tokenInfo.has("name") || !tokenInfo.has("symbol") || !tokenInfo.has("price")) {
            String message = """
                    ❌ *Invalid Token Information*
                    
                    The token information is incomplete or invalid.
                    Please try with a different token address.
                    """;
            
            // Create keyboard with back button
            ButtonHandler[][] keyboard = new ButtonHandler[][] {
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "🔙 Back to Trading"; }
                        @Override public String getCallbackData() { return "back_to_trading"; }
                        @Override public void handleCallback(Update update) { 
                            handleCancelSell(update, update.getCallbackQuery().getFrom().getId());
                        }
                    }
                }
            };
            
            MessageUtils.sendInlineKeyboard(update, message, keyboard);
            stateService.setState(userId, ChatState.AWAITING_VALID_TOKEN);
            return;
        }
        
        String tokenName = tokenInfo.get("name").asText();
        String tokenSymbol = tokenInfo.get("symbol").asText();
        double tokenPrice = tokenInfo.get("price").asDouble();
        
        // Validate token price
        if (tokenPrice <= 0) {
            String message = """
                    ❌ *Invalid Token Price*
                    
                    This token currently has no valid price.
                    Trading is not available at this time.
                    """;
            
            // Create keyboard with back button
            ButtonHandler[][] keyboard = new ButtonHandler[][] {
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "🔙 Back to Trading"; }
                        @Override public String getCallbackData() { return "back_to_trading"; }
                        @Override public void handleCallback(Update update) { 
                            handleCancelSell(update, update.getCallbackQuery().getFrom().getId());
                        }
                    }
                }
            };
            
            MessageUtils.sendInlineKeyboard(update, message, keyboard);
            stateService.setState(userId, ChatState.AWAITING_VALID_TOKEN);
            return;
        }
        
        // Get user settings for sell amounts and slippage
        double[] sellAmounts = {1000, 5000, 10000}; // Default values
        double defaultSlippage = 20.0; // Default value
        boolean isLimitOrder = stateData.has("isLimitOrder") ? stateData.get("isLimitOrder").asBoolean() : false;
        
        // Try to get values from user settings
        Optional<User> userOpt = userService.findByTelegramId(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("Getting slippage for user ID: " + user.getId());
            
            // Get sell slippage from settings
            int sellSlippage = getSellSlippageFromSettings(user);
            System.out.println("Explicitly retrieved sell slippage: " + sellSlippage);
            
            // Set default slippage from the retrieved value
            defaultSlippage = sellSlippage;
            
            UserSettings settings = userSettingsService.findByUserId(user.getId());
            if (settings != null && settings.getSettings() != null) {
                JsonNode settingsJson = settings.getSettings();
                System.out.println("User settings JSON: " + settingsJson);
                
                // Get sell amounts from settings
                if (settingsJson.has("sell") && settingsJson.get("sell").isArray()) {
                    JsonNode sellArr = settingsJson.get("sell");
                    for (int i = 0; i < Math.min(sellArr.size(), 3); i++) {
                        sellAmounts[i] = sellArr.get(i).asDouble();
                    }
                }
            }
        }
        
        // Get current values from state (if set)
        double selectedAmount = stateData.has("sellAmount") ? stateData.get("sellAmount").asDouble() : sellAmounts[0];
        double selectedSlippage = stateData.has("slippage") ? stateData.get("slippage").asDouble() : defaultSlippage;
        double limitPrice = stateData.has("limitPrice") ? stateData.get("limitPrice").asDouble() : tokenPrice;
        
        // Update state with current values
        stateData.put("sellAmount", selectedAmount);
        stateData.put("slippage", selectedSlippage);
        stateData.put("isLimitOrder", isLimitOrder);
        if (isLimitOrder) {
            stateData.put("limitPrice", limitPrice);
        }
        stateService.setStateData(userId, stateData);
        
        // Calculate SOL amount
        double solanaPrice = solanaUtils.getSolanaPrice();
        double effectivePrice = isLimitOrder ? limitPrice : tokenPrice;
        double solAmount = (selectedAmount * effectivePrice) / solanaPrice;
        
        // Create message with new compact format
        String limitOrderSection = isLimitOrder ? String.format("""
                📊 *Limit Order Details*
                💰 Amount: %.4f %s
                📈 Limit Price: $%.6f
                ⏳ Status: Pending
                🔄 Will execute when price reaches $%.6f
                
                ⚠️ *COMING SOON: Limit Order Feature*
                The limit order feature is currently under development and not yet available.
                Your order will be saved but not executed until the feature is released.
                """, 
                selectedAmount,
                tokenSymbol,
                limitPrice,
                limitPrice) : "";

        String message = String.format("""
                🚀 SWAP $%s → SOL
                
                ⚡ Trade Details:
                🔒 You pay: %.4f $%s ($%.2f)
                ✅ You receive: %.4f SOL (estimated)
                Rate: 1 $%s = $%.6f
                
                ⚙️ Settings:
                🔄 Max slippage: %.1f%%
                ⏱️ Transaction deadline: 20 minutes
                
                %s
                
                ⚠️ Warning: Transactions cannot be reversed once confirmed.
                """,
                tokenSymbol,
                selectedAmount,
                tokenSymbol,
                selectedAmount * effectivePrice,
                solAmount,
                tokenSymbol,
                effectivePrice,
                selectedSlippage,
                limitOrderSection
        );

        log.debug("Prepared sell screen message for user {}: {}", userId, message);
        
        // Create buttons with preset sell amounts
        ButtonHandler[][] keyboard = new ButtonHandler[][] {
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return sellAmounts[0] + " " + tokenSymbol; }
                    @Override public String getCallbackData() { return "sell_amount_" + sellAmounts[0]; }
                    @Override public void handleCallback(Update update) { 
                        updateSellAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(sellAmounts[0])); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return sellAmounts[1] + " " + tokenSymbol; }
                    @Override public String getCallbackData() { return "sell_amount_" + sellAmounts[1]; }
                    @Override public void handleCallback(Update update) { 
                        updateSellAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(sellAmounts[1])); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return sellAmounts[2] + " " + tokenSymbol; }
                    @Override public String getCallbackData() { return "sell_amount_" + sellAmounts[2]; }
                    @Override public void handleCallback(Update update) { 
                        updateSellAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(sellAmounts[2])); 
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "Custom Amount"; }
                    @Override public String getCallbackData() { return CUSTOM_AMOUNT; }
                    @Override public void handleCallback(Update update) { 
                        handleCustomAmount(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "Slippage: " + selectedSlippage + "%"; }
                    @Override public String getCallbackData() { return CUSTOM_SLIPPAGE; }
                    @Override public void handleCallback(Update update) { 
                        handleCustomSlippage(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return isLimitOrder ? "✅ Limit Order" : "⬜ Limit Order"; }
                    @Override public String getCallbackData() { return TOGGLE_LIMIT_ORDER; }
                    @Override public void handleCallback(Update update) { 
                        toggleLimitOrder(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "🚀 Sell Now"; }
                    @Override public String getCallbackData() { return CONFIRM_SELL; }
                    @Override public void handleCallback(Update update) { 
                        handleConfirmSell(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "❌ Cancel"; }
                    @Override public String getCallbackData() { return CANCEL_SELL; }
                    @Override public void handleCallback(Update update) { 
                        handleCancelSell(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                }
            }
        };
        
        // Register handlers
        for (ButtonHandler[] row : keyboard) {
            for (ButtonHandler button : row) {
                getHandlerRegistry().registerHandler(button.getCallbackData(), button);
                log.debug("Registered button handler for callback data: {}", button.getCallbackData());
            }
        }
        
        // Send message with keyboard
        MessageUtils.sendInlineKeyboard(update, message, keyboard);
        stateService.setState(userId, ChatState.SELL_CONFIRMATION);
    }
    
    private void updateSellAmount(Update update, Long userId, String amountStr) {
        log.debug("Updating sell amount for user {} to {}", userId, amountStr);
        
        try {
            // Parse the amount
            double amount = Double.parseDouble(amountStr.replace(",", ".").trim());
            
            if (amount <= 0) {
                MessageUtils.sendMessage(update, "❌ Amount must be greater than 0");
                return;
            }
            
            // Update state with new amount
            ObjectNode stateData = stateService.getStateData(userId);
            stateData.put("sellAmount", amount);
            stateService.setStateData(userId, stateData);
            
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            // Show updated sell screen
            showSellScreen(update, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid amount format: {}", amountStr);
            MessageUtils.sendMessage(update, "❌ Please enter a valid number (example: 1000)");
            stateService.setState(userId, ChatState.AWAITING_SELL_AMOUNT);
        }
    }
    
    private void updateSlippage(Update update, Long userId, String slippageStr) {
        log.debug("Updating slippage for user {} to {}", userId, slippageStr);
        
        try {
            // Parse the slippage
            double slippage = Double.parseDouble(slippageStr.replace("%", "").replace(",", ".").trim());
            
            if (slippage < 0.1 || slippage > 100) {
                MessageUtils.sendMessage(update, "❌ Slippage must be between 0.1% and 100%");
                return;
            }
            
            // Update state with new slippage
            ObjectNode stateData = stateService.getStateData(userId);
            stateData.put("slippage", slippage);
            stateService.setStateData(userId, stateData);
            
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            // Show updated sell screen
            showSellScreen(update, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid slippage format: {}", slippageStr);
            MessageUtils.sendMessage(update, "❌ Please enter a valid percentage (example: 1.5)");
            stateService.setState(userId, ChatState.AWAITING_SELL_SLIPPAGE);
        }
    }
    
    private void updateLimitPrice(Update update, Long userId, String limitPriceStr) {
        log.debug("Updating limit price for user {} to {}", userId, limitPriceStr);
        
        try {
            // Parse the limit price
            double limitPrice = Double.parseDouble(limitPriceStr.replace("$", "").replace(",", ".").trim());
            
            if (limitPrice <= 0) {
                MessageUtils.sendMessage(update, "❌ Price must be greater than 0");
                return;
            }
            
            // Update state with new limit price
            ObjectNode stateData = stateService.getStateData(userId);
            stateData.put("limitPrice", limitPrice);
            stateService.setStateData(userId, stateData);
            
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            // Show updated sell screen
            showSellScreen(update, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid limit price format: {}", limitPriceStr);
            MessageUtils.sendMessage(update, "❌ Please enter a valid price (example: 0.00052)");
            stateService.setState(userId, ChatState.AWAITING_LIMIT_PRICE);
        }
    }
    
    private void toggleLimitOrder(Update update, Long userId) {
        ObjectNode stateData = stateService.getStateData(userId);
        boolean current = stateData.has("isLimitOrder") ? stateData.get("isLimitOrder").asBoolean() : false;
        
        // Toggle the value
        stateData.put("isLimitOrder", !current);
        
        // If enabling limit order and no limit price set, set it to current token price
        if (!current && (!stateData.has("limitPrice") || stateData.get("limitPrice").asDouble() <= 0)) {
            JsonNode tokenInfo = stateData.get("tokenInfo");
            if (tokenInfo != null && tokenInfo.has("price")) {
                double currentPrice = tokenInfo.get("price").asDouble();
                stateData.put("limitPrice", currentPrice);
            }
        }
        
        stateService.setStateData(userId, stateData);
        
        // If switching to limit order, prompt for price
        if (!current) {
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            JsonNode tokenInfo = stateData.get("tokenInfo");
            String tokenSymbol = tokenInfo.has("symbol") ? tokenInfo.get("symbol").asText() : "Unknown";
            double tokenPrice = tokenInfo.has("price") ? tokenInfo.get("price").asDouble() : 0.0;
            
            String message = String.format("""
                    📝 *Set Limit Order Price*
                    
                    Current %s price: $%.6f
                    
                    Please enter your desired limit price or select one of the options below:
                    """, 
                    tokenSymbol, tokenPrice);
            
            // Create buttons for preset discounts
            final double price5pct = tokenPrice * 1.05; // 5% above current price
            final double price10pct = tokenPrice * 1.10; // 10% above current price
            
            ButtonHandler[][] keyboard = new ButtonHandler[][] {
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "+5%: $" + String.format("%.6f", price5pct); }
                        @Override public String getCallbackData() { return "sell_limit_" + price5pct; }
                        @Override public void handleCallback(Update update) { 
                            updateLimitPrice(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(price5pct));
                        }
                    },
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "+10%: $" + String.format("%.6f", price10pct); }
                        @Override public String getCallbackData() { return "sell_limit_" + price10pct; }
                        @Override public void handleCallback(Update update) { 
                            updateLimitPrice(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(price10pct));
                        }
                    }
                },
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "↩️ Back"; }
                        @Override public String getCallbackData() { return CANCEL_SELL; }
                        @Override public void handleCallback(Update update) { 
                            handleCancelSell(update, update.getCallbackQuery().getFrom().getId());
                        }
                    }
                }
            };
            
            // Register these handlers
            for (ButtonHandler[] row : keyboard) {
                for (ButtonHandler button : row) {
                    getHandlerRegistry().registerHandler(button.getCallbackData(), button);
                }
            }
            
            MessageUtils.sendInlineKeyboard(update, message, keyboard);
            stateService.setState(userId, ChatState.AWAITING_LIMIT_PRICE);
        } else {
            // Just update the screen
            showSellScreen(update, userId);
        }
    }
    
    private void handleCustomAmount(Update update, Long userId) {
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        String message = """
                💰 *Enter Custom Amount*
                
                Please enter the amount of tokens you want to sell:
                
                Example: 1000
                """;
        
        log.debug("Prompting user {} for custom amount", userId);
        MessageUtils.sendMessage(update, message);
        stateService.setState(userId, ChatState.AWAITING_SELL_AMOUNT);
    }
    
    private void handleCustomSlippage(Update update, Long userId) {
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        String message = """
                ⚙️ *Set Slippage Tolerance*
                
                Enter your desired slippage percentage:
                
                Example: 1.5
                """;
        
        log.debug("Prompting user {} for custom slippage", userId);
        ButtonHandler[][] keyboard = new ButtonHandler[][] {
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "0.5%"; }
                    @Override public String getCallbackData() { return "sell_slippage_0.5"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "0.5");
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "1%"; }
                    @Override public String getCallbackData() { return "sell_slippage_1"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "1");
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "2%"; }
                    @Override public String getCallbackData() { return "sell_slippage_2"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "2");
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "5%"; }
                    @Override public String getCallbackData() { return "sell_slippage_5"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "5");
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "↩️ Back"; }
                    @Override public String getCallbackData() { return CANCEL_SELL; }
                    @Override public void handleCallback(Update update) { 
                        showSellScreen(update, update.getCallbackQuery().getFrom().getId());
                    }
                }
            }
        };
        
        // Register these handlers
        for (ButtonHandler[] row : keyboard) {
            for (ButtonHandler button : row) {
                getHandlerRegistry().registerHandler(button.getCallbackData(), button);
                log.debug("Registered button handler for callback data: {}", button.getCallbackData());
            }
        }
        
        MessageUtils.sendInlineKeyboard(update, message, keyboard);
        stateService.setState(userId, ChatState.AWAITING_SELL_SLIPPAGE);
    }
    
    private void handleConfirmSell(Update update, Long userId) {
        // Get token info and sell parameters from state
        ObjectNode stateData = stateService.getStateData(userId);
        
        if (!stateData.has("tokenInfo")) {
            log.error("Error: Token information not found for user {}", userId);
            MessageUtils.sendMessage(update, "❌ Error: Token information not found.");
            return;
        }
        
        JsonNode tokenInfo = stateData.get("tokenInfo");
        String tokenSymbol = tokenInfo.has("symbol") ? tokenInfo.get("symbol").asText() : "Unknown";
        String tokenAddress = stateData.has("tokenAddress") ? stateData.get("tokenAddress").asText() : null;
        
        if (tokenAddress == null) {
            log.error("Error: Token address not found in state for user {}", userId);
            MessageUtils.sendMessage(update, "❌ Error: Token address not found in state.");
            return;
        }
        
        // Validate token address format
        if (!solanaUtils.isValidSolanaAddress(tokenAddress)) {
            log.error("Error: Invalid token address format for user {}: {}", userId, tokenAddress);
            MessageUtils.sendMessage(update, "❌ Error: Invalid token address format.");
            return;
        }
        
        double sellAmount = stateData.has("sellAmount") ? stateData.get("sellAmount").asDouble() : 1000;
        
        // Delete current message 
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        // Retrieve user wallet information
        Optional<User> userOpt = userService.findByTelegramId(userId);
        if (!userOpt.isPresent()) {
            log.error("Error: User wallet not found for user {}", userId);
            MessageUtils.sendMessage(update, "❌ User wallet not found. Please setup your wallet first.");
            return;
        }
        
        User user = userOpt.get();
        
        if (user.getCurrentWallet() == null) {
            log.error("Error: No active wallet for user {}", userId);
            MessageUtils.sendMessage(update, "❌ You don't have an active wallet. Please set up a wallet first.");
            return;
        }

        // CRITICAL: Check token balance before proceeding
        try {
            double tokenBalance = solanaUtils.getTokenAmount(user.getCurrentWallet().toString(), tokenAddress);
            log.info("Token balance for user {}: {} {}", userId, tokenBalance, tokenSymbol);
            
            if (tokenBalance < sellAmount) {
                log.error("Error: Insufficient token balance for user {}. Has: {}, Wants to sell: {}", 
                    userId, tokenBalance, sellAmount);
                MessageUtils.sendMessage(update, String.format(
                    "❌ Insufficient balance. You have %.4f %s but trying to sell %.4f %s",
                    tokenBalance, tokenSymbol, sellAmount, tokenSymbol));
                return;
            }
        } catch (Exception e) {
            log.error("Error checking token balance for user {}: {}", userId, e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error checking token balance. Please try again.");
            return;
        }
        
        // Get slippage and speed from user settings
        int slippage = getSellSlippageFromSettings(user);
        SolanaUtils.TransactionPriority speed = getSpeedFromSettings(user);
        
        boolean isLimitOrder = stateData.has("isLimitOrder") ? stateData.get("isLimitOrder").asBoolean() : false;
        double limitPrice = stateData.has("limitPrice") ? stateData.get("limitPrice").asDouble() : 0.0;
        
        // Get the wallet's private key using WalletKeyManager
        UUID walletId = user.getCurrentWallet();
        String walletPrivateKey;
        
        try {
            walletPrivateKey = walletKeyManager.getPrivateKey(walletId);
            log.debug("Retrieved private key for wallet: {}", walletId);
        } catch (Exception e) {
            log.error("Error retrieving wallet private key for user {}: {}", userId, e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error accessing wallet: " + e.getMessage());
            return;
        }
        
        try {
            String priority = speed.toString();
            if (isLimitOrder) {
                // Validate limit price
                if (limitPrice <= 0) {
                    log.error("Error: Invalid limit price for user {}: {}", userId, limitPrice);
                    MessageUtils.sendMessage(update, "❌ Invalid limit price. Please try again.");
                    return;
                }

                // Create a limit order
                LimitOrder order = transactionSendingService.createLimitOrder(
                    user.getTelegramId(),
                    walletId,
                    tokenAddress,
                    BigDecimal.valueOf(sellAmount),
                    BigDecimal.valueOf(limitPrice),
                    BigDecimal.valueOf(slippage),
                    OrderAction.SELL
                );
                
                String limitOrderMessage = String.format("""
                        ⚠️ *COMING SOON: Limit Order Feature*
                        
                        The limit order feature is currently under development and not yet available.
                        Your order has been saved in the database but will not be executed until the feature is released.
                        
                        Order details:
                        💰 Amount: %.4f %s
                        📊 Limit Price: $%.6f
                        🔄 Slippage: %d%%
                        🔑 Order ID: %s
                        
                        We'll notify you when this feature becomes available.
                        """,
                        sellAmount,
                        tokenSymbol,
                        limitPrice,
                        slippage,
                        order.getOrderId()
                );
                
                MessageUtils.sendMessage(update, limitOrderMessage);
                
            } else {
                // Double check token price before market order
                double currentPrice = tokenInfo.has("price") ? tokenInfo.get("price").asDouble() : 0.0;
                if (currentPrice <= 0) {
                    log.error("Error: Invalid token price for user {}: {}", userId, currentPrice);
                    MessageUtils.sendMessage(update, "❌ Invalid token price. Please try again.");
                    return;
                }

                // Process market order
                String processingMessage = String.format("""
                        🔄 *Processing Your Transaction*
                        
                        Swapping *%.4f %s* for *SOL*
                        Current Price: $%.6f
                        Expected SOL: ~%.4f
                        
                        Please wait while we process your transaction...
                        """, 
                        sellAmount,
                        tokenSymbol,
                        currentPrice,
                        (sellAmount * currentPrice) / solanaUtils.getSolanaPrice());
                
                MessageUtils.sendMessage(update, processingMessage);
                
                // Execute the actual swap with real private key
                SolanaUtils.SwapResponse response = solanaUtils.sellToken(
                    tokenAddress,
                    sellAmount,
                    slippage,
                    walletPrivateKey,
                    SolanaUtils.TransactionPriority.valueOf(priority)
                );
                
                if (response.isSuccess()) {
                    // Send success message
                    String successMessage = String.format("""
                            ✅ *Transaction Complete!*
                            
                            Successfully swapped *%.4f %s* for *%.4f SOL*
                            Transaction ID: `%s`
                            
                            You can view this transaction on the explorer:
                            [View on Solscan](https://solscan.io/tx/%s)
                            """, 
                            sellAmount,
                            tokenSymbol,
                            response.getAmount(),
                            response.getSignature(),
                            response.getSignature());
                    
                    MessageUtils.sendMessage(update, successMessage);
                } else {
                    log.error("Transaction failed for user {}: {}", userId, response.getError());
                    MessageUtils.sendMessage(update, "❌ Transaction failed: " + response.getError());
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing transaction for user {}: {}", userId, e.getMessage(), e);
            MessageUtils.sendMessage(update, "❌ Transaction failed: " + e.getMessage());
        }
        
        // Reset state to TOKEN_INFO_PAGE when done
        stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
    }
    
    private void handleCancelSell(Update update, Long userId) {
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        MessageUtils.sendMessage(update, "🔙 Returned to token info");
        
        // Reset state to TOKEN_INFO_PAGE
        stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
    }

    // Update the getSellSlippageFromSettings method with higher log levels
    private int getSellSlippageFromSettings(User user) {
        try {
            // Use DEBUG level for visibility in logs
            log.debug("Getting sell slippage for user ID: {}", user.getId());
            UserSettings settings = userSettingsService.findByUserId(user.getId());
            
            if (settings == null) {
                log.debug("No settings found for user ID: {}", user.getId());
                return 20; // Default value
            }
            
            JsonNode settingsJson = settings.getSettings();
            log.debug("User settings retrieved: {}", settingsJson);
            
            if (settingsJson.has("sell_slippage")) {
                JsonNode sellSlippageNode = settingsJson.get("sell_slippage");
                if (sellSlippageNode.isArray() && sellSlippageNode.size() > 0) {
                    int slippage = sellSlippageNode.get(0).asInt();
                    log.debug("Retrieved sell slippage: {}", slippage);
                    return slippage;
                } else {
                    log.debug("sell_slippage exists but is not a valid array or is empty");
                }
            } else {
                log.debug("sell_slippage field not found in user settings");
            }
            
            // Try fallback to general slippage setting
            if (settingsJson.has("slippage")) {
                if (settingsJson.get("slippage").isNumber()) {
                    int slippage = settingsJson.get("slippage").asInt();
                    log.debug("Using fallback general slippage: {}", slippage);
                    return slippage;
                }
            }
            
            log.debug("Using default slippage value: 20");
            return 20; // Default value if not found
        } catch (Exception e) {
            log.debug("Error getting sell slippage from settings", e);
            return 20; // Default value on error
        }
    }

    // Also update the getSpeedFromSettings method
    private SolanaUtils.TransactionPriority getSpeedFromSettings(User user) {
        try {
            log.debug("Getting transaction speed for user ID: {}", user.getId());
            UserSettings settings = userSettingsService.findByUserId(user.getId());
            
            if (settings == null) {
                log.debug("No settings found for user ID: {}", user.getId());
                return SolanaUtils.TransactionPriority.MID;
            }
            
            JsonNode settingsJson = settings.getSettings();
            log.debug("User settings retrieved for speed: {}", settingsJson);
            
            if (settingsJson.has("speed")) {
                String speedStr = settingsJson.get("speed").asText();
                log.debug("Speed setting found: {}", speedStr);
                
                try {
                    SolanaUtils.TransactionPriority priority = SolanaUtils.TransactionPriority.valueOf(speedStr);
                    log.debug("Parsed speed setting to enum: {}", priority);
                    return priority;
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid speed setting value: {}", speedStr);
                }
            } else {
                log.debug("speed field not found in user settings");
            }
            
            log.debug("Using default speed: MID");
            return SolanaUtils.TransactionPriority.MID;
        } catch (Exception e) {
            log.debug("Error getting speed from settings", e);
            return SolanaUtils.TransactionPriority.MID;
        }
    }

    // Create a method to get HandlerRegistry lazily
    private HandlerRegistry getHandlerRegistry() {
        return applicationContext.getBean(HandlerRegistry.class);
    }
}