package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
public class Buy implements StateHandler {

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
    private HandlerRegistry handlerRegistry;

    // Define callback data constants
    private static final String CONFIRM_BUY = "buy_confirm";
    private static final String CANCEL_BUY = "buy_cancel";
    private static final String TOGGLE_LIMIT_ORDER = "buy_toggle_limit";
    private static final String CUSTOM_AMOUNT = "buy_custom_amount";
    private static final String CUSTOM_SLIPPAGE = "buy_custom_slippage";
    private static final String BUY_TOKEN = "buy_token";

    public Buy() {
        // Register button handlers with the HandlerRegistry in the constructor
    }
    
    @PostConstruct
    public void init() {
        // Register the buy_token handler that's used from the token info page
        ButtonHandler buyTokenHandler = new ButtonHandler() {
            @Override
            public String getButtonText() { 
                return "Buy Token"; 
            }
            
            @Override
            public String getCallbackData() { 
                return BUY_TOKEN; 
            }
            
            @Override
            public void handleCallback(Update update) { 
                Long userId = update.getCallbackQuery().getFrom().getId();
                log.debug("Handling buy token callback for user: {}", userId);
                executeVoid(update); 
            }
        };
        
        handlerRegistry.registerHandler(BUY_TOKEN, buyTokenHandler);
        log.info("Registered buy_token handler");
    }

    @Override
    public void executeVoid(Update update) {
        Long userId = null;
        
        if (update.hasCallbackQuery()) {
            userId = update.getCallbackQuery().getFrom().getId();
            String callbackData = update.getCallbackQuery().getData();
            log.debug("Received callback data: {}", callbackData);
            
            if (callbackData.equals(BUY_TOKEN)) {
                log.debug("User {} started buy process", userId);
                showBuyScreen(update, userId);
                return;
            } else if (callbackData.equals(CONFIRM_BUY)) {
                log.debug("User {} confirmed buy", userId);
                handleConfirmBuy(update, userId);
                return;
            } else if (callbackData.equals(CANCEL_BUY)) {
                log.debug("User {} canceled buy", userId);
                handleCancelBuy(update, userId);
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
            } else if (callbackData.startsWith("buy_amount_")) {
                String amountStr = callbackData.substring("buy_amount_".length());
                log.debug("User {} selected buy amount: {}", userId, amountStr);
                updateBuyAmount(update, userId, amountStr);
                return;
            } else if (callbackData.startsWith("buy_slippage_")) {
                String slippageStr = callbackData.substring("buy_slippage_".length());
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
            if (currentState == ChatState.AWAITING_BUY_AMOUNT) {
                String amount = update.getMessage().getText();
                log.debug("User {} entered custom buy amount: {}", userId, amount);
                updateBuyAmount(update, userId, amount);
                return;
            } else if (currentState == ChatState.AWAITING_BUY_SLIPPAGE) {
                String slippage = update.getMessage().getText();
                log.debug("User {} entered custom slippage: {}", userId, slippage);
                updateSlippage(update, userId, slippage);
                return;
            } else if (currentState == ChatState.AWAITING_LIMIT_PRICE) {
                String limitPrice = update.getMessage().getText();
                log.debug("User {} entered limit price: {}", userId, limitPrice);
                updateLimitPrice(update, userId, limitPrice);
                return;
            }
        }

        // Default action: show the buy confirmation screen
        showBuyScreen(update, userId);
    }
    
    private void showBuyScreen(Update update, Long userId) {
        // Get state data with token info
        ObjectNode stateData = stateService.getStateData(userId);
        if (stateData == null || !stateData.has("tokenInfo")) {
            log.debug("No token selected for user {}", userId);
            MessageUtils.sendMessage(update, "❌ No token selected. Please select a token first.");
            return;
        }
        
        // Get token information
        JsonNode tokenInfo = stateData.get("tokenInfo");
        String tokenName = tokenInfo.get("tokenName").asText();
        String tokenSymbol = tokenInfo.get("tokenSymbol").asText();
        double tokenPrice = tokenInfo.get("priceUSD").asDouble();
        
        // Get user settings for buy amounts and slippage
        double[] buyAmounts = {0.1, 0.5, 2.0}; // Default values
        double defaultSlippage = 20.0; // Default value
        boolean isLimitOrder = stateData.has("isLimitOrder") ? stateData.get("isLimitOrder").asBoolean() : false;
        
        // Try to get values from user settings
        Optional<User> userOpt = userService.findByTelegramId(userId);
        if (userOpt.isPresent()) {
            Optional<UserSettings> settingsOpt = userSettingsService.findByUserId(userOpt.get().getId());
            if (settingsOpt.isPresent() && settingsOpt.get().getSettings() != null) {
                JsonNode settings = settingsOpt.get().getSettings();
                
                // Get buy amounts from settings
                if (settings.has("buy") && settings.get("buy").isArray()) {
                    JsonNode buyArr = settings.get("buy");
                    for (int i = 0; i < Math.min(buyArr.size(), 3); i++) {
                        buyAmounts[i] = buyArr.get(i).asDouble();
                    }
                }
                
                // Get slippage from settings
                if (settings.has("slippage")) {
                    if (settings.get("slippage").isNumber()) {
                        defaultSlippage = settings.get("slippage").asDouble();
                    } else if (settings.get("slippage").isArray() && settings.get("slippage").size() > 0) {
                        defaultSlippage = settings.get("slippage").get(0).asDouble();
                    }
                }
            }
        }
        
        // Get current values from state (if set)
        double selectedAmount = stateData.has("buyAmount") ? stateData.get("buyAmount").asDouble() : buyAmounts[0];
        double selectedSlippage = stateData.has("slippage") ? stateData.get("slippage").asDouble() : defaultSlippage;
        double limitPrice = stateData.has("limitPrice") ? stateData.get("limitPrice").asDouble() : tokenPrice;
        
        // Update state with current values
        stateData.put("buyAmount", selectedAmount);
        stateData.put("slippage", selectedSlippage);
        stateData.put("isLimitOrder", isLimitOrder);
        if (isLimitOrder) {
            stateData.put("limitPrice", limitPrice);
        }
        stateService.setStateData(userId, stateData);
        
        // Calculate token amount
        double solanaPrice = solanaUtils.getSolanaPrice();
        double effectivePrice = isLimitOrder ? limitPrice : tokenPrice;
        double tokenAmount = (selectedAmount * solanaPrice) / effectivePrice;
        
        // Create message
        String orderType = isLimitOrder ? "LIMIT" : "MARKET";
        String limitPriceText = isLimitOrder ? String.format("\n💹 Limit Price: $%.6f", limitPrice) : "";
        
        String message = String.format("""
                🚀 *%s ORDER: SOL → %s*
                
                💰 You pay: *%.3f SOL* ($%.2f)
                📈 You receive: *%.2f %s* (estimated)
                💱 Rate: 1 %s = $%.6f
                🔄 Slippage: %.1f%%%s
                
                ⚠️ Please confirm your transaction
                """,
                orderType,
                tokenSymbol,
                selectedAmount,
                selectedAmount * solanaPrice,
                tokenAmount,
                tokenSymbol,
                tokenSymbol,
                effectivePrice,
                selectedSlippage,
                limitPriceText
        );

        log.debug("Prepared buy screen message for user {}: {}", userId, message);
        
        // Create buttons with preset buy amounts
        ButtonHandler[][] keyboard = new ButtonHandler[][] {
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return buyAmounts[0] + " SOL"; }
                    @Override public String getCallbackData() { return "buy_amount_" + buyAmounts[0]; }
                    @Override public void handleCallback(Update update) { 
                        updateBuyAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(buyAmounts[0])); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return buyAmounts[1] + " SOL"; }
                    @Override public String getCallbackData() { return "buy_amount_" + buyAmounts[1]; }
                    @Override public void handleCallback(Update update) { 
                        updateBuyAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(buyAmounts[1])); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return buyAmounts[2] + " SOL"; }
                    @Override public String getCallbackData() { return "buy_amount_" + buyAmounts[2]; }
                    @Override public void handleCallback(Update update) { 
                        updateBuyAmount(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(buyAmounts[2])); 
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
                    @Override public String getButtonText() { return "🚀 Buy Now"; }
                    @Override public String getCallbackData() { return CONFIRM_BUY; }
                    @Override public void handleCallback(Update update) { 
                        handleConfirmBuy(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "❌ Cancel"; }
                    @Override public String getCallbackData() { return CANCEL_BUY; }
                    @Override public void handleCallback(Update update) { 
                        handleCancelBuy(update, update.getCallbackQuery().getFrom().getId()); 
                    }
                }
            }
        };
        
        // Register handlers
        for (ButtonHandler[] row : keyboard) {
            for (ButtonHandler button : row) {
                handlerRegistry.registerHandler(button.getCallbackData(), button);
                log.debug("Registered button handler for callback data: {}", button.getCallbackData());
            }
        }
        
        // Send message with keyboard
        MessageUtils.sendInlineKeyboard(update, message, keyboard);
        stateService.setState(userId, ChatState.BUY_CONFIRMATION);
    }
    
    private void updateBuyAmount(Update update, Long userId, String amountStr) {
        log.debug("Updating buy amount for user {} to {}", userId, amountStr);
        
        try {
            // Parse the amount
            double amount = Double.parseDouble(amountStr.replace(",", ".").trim());
            
            if (amount <= 0) {
                MessageUtils.sendMessage(update, "❌ Amount must be greater than 0");
                return;
            }
            
            // Update state with new amount
            ObjectNode stateData = stateService.getStateData(userId);
            stateData.put("buyAmount", amount);
            stateService.setStateData(userId, stateData);
            
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            // Show updated buy screen
            showBuyScreen(update, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid amount format: {}", amountStr);
            MessageUtils.sendMessage(update, "❌ Please enter a valid number (example: 0.25)");
            stateService.setState(userId, ChatState.AWAITING_BUY_AMOUNT);
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
            
            // Show updated buy screen
            showBuyScreen(update, userId);
            
        } catch (NumberFormatException e) {
            log.debug("Invalid slippage format: {}", slippageStr);
            MessageUtils.sendMessage(update, "❌ Please enter a valid percentage (example: 1.5)");
            stateService.setState(userId, ChatState.AWAITING_BUY_SLIPPAGE);
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
            
            // Show updated buy screen
            showBuyScreen(update, userId);
            
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
            double tokenPrice = stateData.get("tokenInfo").get("priceUSD").asDouble();
            stateData.put("limitPrice", tokenPrice);
        }
        
        stateService.setStateData(userId, stateData);
        
        // If switching to limit order, prompt for price
        if (!current) {
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            String tokenSymbol = stateData.get("tokenInfo").get("tokenSymbol").asText();
            double tokenPrice = stateData.get("tokenInfo").get("priceUSD").asDouble();
            
            String message = String.format("""
                    📝 *Set Limit Order Price*
                    
                    Current %s price: $%.6f
                    
                    Please enter your desired limit price or select one of the options below:
                    """, 
                    tokenSymbol, tokenPrice);
            
            // Create buttons for preset discounts
            final double price5pct = tokenPrice * 0.95;
            final double price10pct = tokenPrice * 0.90;
            
            ButtonHandler[][] keyboard = new ButtonHandler[][] {
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "-5%: $" + String.format("%.6f", price5pct); }
                        @Override public String getCallbackData() { return "buy_limit_" + price5pct; }
                        @Override public void handleCallback(Update update) { 
                            updateLimitPrice(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(price5pct));
                        }
                    },
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "-10%: $" + String.format("%.6f", price10pct); }
                        @Override public String getCallbackData() { return "buy_limit_" + price10pct; }
                        @Override public void handleCallback(Update update) { 
                            updateLimitPrice(update, update.getCallbackQuery().getFrom().getId(), String.valueOf(price10pct));
                        }
                    }
                },
                {
                    new ButtonHandler() {
                        @Override public String getButtonText() { return "↩️ Back"; }
                        @Override public String getCallbackData() { return CANCEL_BUY; }
                        @Override public void handleCallback(Update update) { 
                            handleCancelBuy(update, update.getCallbackQuery().getFrom().getId());
                        }
                    }
                }
            };
            
            // Register these handlers
            for (ButtonHandler[] row : keyboard) {
                for (ButtonHandler button : row) {
                    handlerRegistry.registerHandler(button.getCallbackData(), button);
                }
            }
            
            MessageUtils.sendInlineKeyboard(update, message, keyboard);
            stateService.setState(userId, ChatState.AWAITING_LIMIT_PRICE);
        } else {
            // Just update the screen
            showBuyScreen(update, userId);
        }
    }
    
    private void handleCustomAmount(Update update, Long userId) {
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        String message = """
                💰 *Enter Custom Amount*
                
                Please enter the amount of SOL you want to spend:
                
                Example: 0.25
                """;
        
        log.debug("Prompting user {} for custom amount", userId);
        MessageUtils.sendMessage(update, message);
        stateService.setState(userId, ChatState.AWAITING_BUY_AMOUNT);
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
                    @Override public String getCallbackData() { return "buy_slippage_0.5"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "0.5");
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "1%"; }
                    @Override public String getCallbackData() { return "buy_slippage_1"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "1");
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "2%"; }
                    @Override public String getCallbackData() { return "buy_slippage_2"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "2");
                    }
                },
                new ButtonHandler() {
                    @Override public String getButtonText() { return "5%"; }
                    @Override public String getCallbackData() { return "buy_slippage_5"; }
                    @Override public void handleCallback(Update update) { 
                        updateSlippage(update, update.getCallbackQuery().getFrom().getId(), "5");
                    }
                }
            },
            {
                new ButtonHandler() {
                    @Override public String getButtonText() { return "↩️ Back"; }
                    @Override public String getCallbackData() { return CANCEL_BUY; }
                    @Override public void handleCallback(Update update) { 
                        showBuyScreen(update, update.getCallbackQuery().getFrom().getId());
                    }
                }
            }
        };
        
        // Register these handlers
        for (ButtonHandler[] row : keyboard) {
            for (ButtonHandler button : row) {
                handlerRegistry.registerHandler(button.getCallbackData(), button);
                log.debug("Registered button handler for callback data: {}", button.getCallbackData());
            }
        }
        
        MessageUtils.sendInlineKeyboard(update, message, keyboard);
        stateService.setState(userId, ChatState.AWAITING_BUY_SLIPPAGE);
    }
    
    private void handleConfirmBuy(Update update, Long userId) {
        // Get token info and buy parameters from state
        ObjectNode stateData = stateService.getStateData(userId);
        
        if (!stateData.has("tokenInfo")) {
            log.debug("Error: Token information not found for user {}", userId);
            MessageUtils.sendMessage(update, "❌ Error: Token information not found.");
            return;
        }
        
        JsonNode tokenInfo = stateData.get("tokenInfo");
        String tokenSymbol = tokenInfo.get("tokenSymbol").asText();
        double buyAmount = stateData.has("buyAmount") ? stateData.get("buyAmount").asDouble() : 0.1;
        double slippage = stateData.has("slippage") ? stateData.get("slippage").asDouble() : 1.0;
        boolean isLimitOrder = stateData.has("isLimitOrder") ? stateData.get("isLimitOrder").asBoolean() : false;
        double limitPrice = stateData.has("limitPrice") ? stateData.get("limitPrice").asDouble() : 0.0;
        String txId = stateData.has("buyTxId") ? stateData.get("buyTxId").asText() : 
                     UUID.randomUUID().toString().substring(0, 8);
        
        // Delete current message 
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        if (isLimitOrder) {
            // Process limit order
            String limitOrderMessage = String.format("""
                    📋 *Limit Order Placed!*
                    
                    Your order to buy %s is now active:
                    
                    💰 Amount: %.4f SOL
                    📊 Limit Price: $%.6f
                    🔄 Slippage: %.1f%%
                    🔑 Order ID: %s
                    
                    Your order will execute automatically when the price reaches $%.6f
                    You will be notified when your order is filled.
                    """,
                    tokenSymbol,
                    buyAmount,
                    limitPrice,
                    slippage,
                    txId,
                    limitPrice
            );
            
            MessageUtils.sendMessage(update, limitOrderMessage);
            
        } else {
            // Process market order
            String processingMessage = String.format("""
                    🔄 *Processing Your Transaction*
                    
                    Swapping *%.4f SOL* for *%s*
                    Transaction ID: `%s`
                    
                    Please wait while we process your transaction...
                    """, 
                    buyAmount, 
                    tokenSymbol,
                    txId);
            
            MessageUtils.sendMessage(update, processingMessage);
            
            // Simulate transaction processing
            try {
                // Simulate a delay for transaction processing
                Thread.sleep(3000);
                
                // Send success message
                String successMessage = String.format("""
                        ✅ *Transaction Complete!*
                        
                        Successfully swapped *%.4f SOL* for *%s*
                        Transaction ID: `%s`
                        
                        You can view this transaction on the explorer:
                        [View on Solscan](https://solscan.io/tx/%s)
                        """, 
                        buyAmount, 
                        tokenSymbol,
                        txId,
                        txId);
                
                MessageUtils.sendMessage(update, successMessage);
                
            } catch (Exception e) {
                log.error("Error processing transaction", e);
                MessageUtils.sendMessage(update, "❌ Transaction failed: " + e.getMessage());
            }
        }
        
        // Reset state to TOKEN_INFO_PAGE when done
        stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
    }
    
    private void handleCancelBuy(Update update, Long userId) {
        if (update.hasCallbackQuery()) {
            MessageUtils.deleteMessage(update);
        }
        
        MessageUtils.sendMessage(update, "🔙 Returned to token info");
        
        // Reset state to TOKEN_INFO_PAGE
        stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
    }
}