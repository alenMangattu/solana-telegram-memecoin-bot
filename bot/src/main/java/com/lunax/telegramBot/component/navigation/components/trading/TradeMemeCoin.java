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
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.component.navigation.components.token.Buy;
import com.lunax.telegramBot.component.navigation.components.token.Sell;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.TokenDetailsService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.Buttons.Token.TokenInfoButton;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TradeMemeCoin implements StateHandler {
    
    private static final int PAGE_SIZE = 5; // Number of tokens per page

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private Buy buyHandler;
    
    @Autowired
    private Sell sellHandler;

    @Autowired
    private TokenDetailsService tokenDetailsService;
    
    @Autowired
    private CopyTradingHandler copyTradingHandler;
    
    @Autowired
    private TokenInfoButton tokenInfoButton;
    
    @Override
    public void executeVoid(Update update) {
        
        executeVoid(update, 0); // Default to the first page
    }
    
    @Override
    public void executeVoid(Update update, int page) {
        // Get user ID and chat ID
        Long telegramId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
        
        Long chatId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getMessage().getChatId() : 
            update.getMessage().getChatId();
        
        // Check if user exists
        User user = tokenDetailsService.getUserFromCacheOrDatabase(telegramId);
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
        stateService.setState(telegramId, ChatState.TRADE_MEMECOIN_START);
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData == null) {
            stateData = objectMapper.createObjectNode();
        }
        
        try {
            // Create formatted message
            String formattedMessage = "*Trading Options*\n\nSelect an option from the buttons below:";
            
            // Create keyboard markup with buttons
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            
            // Limit Order buttons - Row 1
            List<InlineKeyboardButton> actionRow1 = new ArrayList<>();
            actionRow1.add(InlineKeyboardButton.builder()
                .text("View Limit Orders")
                .callbackData("view_limit_orders")
                .build());
            actionRow1.add(InlineKeyboardButton.builder()
                .text("Create Limit Order")
                .callbackData("create_limit_order")
                .build());
            buttons.add(actionRow1);
            
            // Copy Trading button - Row 2
            List<InlineKeyboardButton> actionRow2 = new ArrayList<>();
            actionRow2.add(InlineKeyboardButton.builder()
                .text("Copy Trading Page")
                .callbackData("copy_trading_page")
                .build());
            buttons.add(actionRow2);
            
            // Token Info button - Row 3
            List<InlineKeyboardButton> actionRow3 = new ArrayList<>();
            actionRow3.add(InlineKeyboardButton.builder()
                .text(tokenInfoButton.getButtonText())
                .callbackData(tokenInfoButton.getCallbackData())
                .build());
            buttons.add(actionRow3);
            
            // My Portfolio button - Row 4
            List<InlineKeyboardButton> actionRow4 = new ArrayList<>();
            actionRow4.add(InlineKeyboardButton.builder()
                .text("My Portfolio")
                .callbackData("my_portfolio")
                .build());
            buttons.add(actionRow4);
            
            // Buy and Sell buttons - Row 5
            List<InlineKeyboardButton> actionRow5 = new ArrayList<>();
            actionRow5.add(InlineKeyboardButton.builder()
                .text("🔄 Buy")
                .callbackData("buy_token")
                .build());
            actionRow5.add(InlineKeyboardButton.builder()
                .text("💰 Sell")
                .callbackData("sell_token")
                .build());
            buttons.add(actionRow5);
            
            // Back to Home button - Row 6
            List<InlineKeyboardButton> actionRow6 = new ArrayList<>();
            actionRow6.add(InlineKeyboardButton.builder()
                .text("Back to Home")
                .callbackData("back_to_home")
                .build());
            buttons.add(actionRow6);
            
            markup.setKeyboard(buttons);
            
            // If we have a callback query, delete the old message and send a new one
            if (update.hasCallbackQuery()) {
                MessageUtils.deleteMessage(update);
            }
            
            // Send new message with trading options
            MessageUtils.sendMessage(update, formattedMessage, markup);
            
            // Store the new message ID in state
            if (update.hasMessage()) {
                stateData.put("tradeMessageId", update.getMessage().getMessageId());
                stateService.setStateData(telegramId, stateData);
            }
            
        } catch (Exception e) {
            log.error("Error loading trading options: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error loading trading options: " + e.getMessage());
        }
    }
    
    /**
     * Handle the buy/sell buttons
     */
    public void handleTradeButton(Update update, boolean isBuy) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = tokenDetailsService.getUserFromCacheOrDatabase(telegramId);
        
        if (user == null) {
            log.error("User not found for Telegram ID: {}", telegramId);
            MessageUtils.sendMessage(update, "❌ User not found. Please start the bot again.");
            return;
        }
        
        // Get state data or create new one if it doesn't exist
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData == null) {
            stateData = objectMapper.createObjectNode();
        }
        
        // Set the state to awaiting token selection
        stateService.setState(telegramId, ChatState.AWAITING_TOKEN_SELECTION);
        
        // Store the trade type (buy or sell) in state data
        stateData.put("tradeType", isBuy ? "buy" : "sell");
        stateService.setStateData(telegramId, stateData);
        
        // Send message asking for token address
        String actionType = isBuy ? "buy" : "sell";
        String message = String.format("Please enter the token address you want to %s:", actionType);
        
        try {
            Message sentMessage = MessageUtils.sendMessage(update, message);
            if (sentMessage != null) {
                stateData.put("tokenRequestMessageId", sentMessage.getMessageId());
                stateService.setStateData(telegramId, stateData);
            }
        } catch (Exception e) {
            log.error("Error sending token request message: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
        }
    }

    /**
     * Process the token address input after Buy/Sell button clicks
     */
    public void processTokenAddressInput(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        
        Long telegramId = update.getMessage().getFrom().getId();
        String tokenAddress = update.getMessage().getText().trim();
        
        // Get state data
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData == null) {
            MessageUtils.sendMessage(update, "❌ Error: No state data found.");
            return;
        }
        
        if (!stateData.has("tradeType")) {
            MessageUtils.sendMessage(update, "❌ Please start the trading process again.");
            return;
        }
        
        // Validate token address format
        if (!solanaUtils.isValidSolanaAddress(tokenAddress)) {
            MessageUtils.sendMessage(update, "❌ Invalid token address format. A Solana token address should be a Base58 encoded string typically 32-44 characters long.\n\nPlease enter a valid Solana token address.");
            return;
        }
        
        // Validate token using SolanaUtils
        Map<String, Object> validationResult = solanaUtils.validateToken(tokenAddress);
        if (!(Boolean) validationResult.get("valid")) {
            String errorMessage = (String) validationResult.get("errorMessage");
            MessageUtils.sendMessage(update, "❌ " + errorMessage);
            return;
        }
        
        // Get the validated token info
        Map<String, Object> tokenInfo = (Map<String, Object>) validationResult.get("tokenInfo");
        if (tokenInfo == null) {
            MessageUtils.sendMessage(update, "❌ Error: Could not retrieve token information.");
            return;
        }
        
        // Store token address in state
        stateData.put("tokenAddress", tokenAddress);
        stateService.setStateData(telegramId, stateData);
        
        // Show loading message
        Message loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your tokens...");
        
        try {
            // Get user
            User user = tokenDetailsService.getUserFromCacheOrDatabase(telegramId);
            if (user == null) {
                MessageUtils.editMessage(update, "❌ Error: User not found.", null);
                return;
            }
            
            String walletAddress = user.getCurrentWalletAddress();
            if (walletAddress == null) {
                MessageUtils.editMessage(update, "❌ Error: No wallet address found.", null);
                return;
            }
            
            // Create token info JSON using the already validated token info
            ObjectNode tokenInfoJson = objectMapper.createObjectNode();
            tokenInfoJson.put("name", tokenInfo.getOrDefault("name", "Unknown").toString());
            tokenInfoJson.put("symbol", tokenInfo.getOrDefault("symbol", "Unknown").toString());
            tokenInfoJson.put("address", tokenAddress);
            tokenInfoJson.put("decimals", (Integer) tokenInfo.getOrDefault("decimals", 0));
            
            // Get token price from the validated token info, using priceUSD as the source of truth
            Double price = 0.0;
            if (tokenInfo.containsKey("priceUSD")) {
                Object priceUSDValue = tokenInfo.get("priceUSD");
                if (priceUSDValue instanceof Number) {
                    price = ((Number) priceUSDValue).doubleValue();
                } else if (priceUSDValue instanceof String) {
                    try {
                        price = Double.parseDouble((String) priceUSDValue);
                    } catch (NumberFormatException e) {
                        log.error("Failed to parse priceUSD string value: {}", priceUSDValue);
                        price = 0.0;
                    }
                }
            }
            tokenInfoJson.put("price", price);
            
            // Only check token balance for sell operations
            double balance = 0.0;
            String tradeType = stateData.get("tradeType").asText();
            if ("sell".equals(tradeType)) {
                try {
                    balance = solanaUtils.getTokenBalance(walletAddress, tokenAddress);
                    tokenInfoJson.put("balance", balance);
                } catch (Exception e) {
                    log.error("Error getting balance for token {}: {}", tokenAddress, e.getMessage());
                    tokenInfoJson.put("balance", 0.0);
                }
            } else {
                // For buy operations, set balance to 0 since we don't need it
                tokenInfoJson.put("balance", 0.0);
            }
            
            // Store the token info in state
            stateData.set("tokenInfo", tokenInfoJson);
            stateService.setStateData(telegramId, stateData);
            
            // Delete loading message
            MessageUtils.deleteMessage(loadingMessage);
            
            // Route to the appropriate handler based on trade type
            if ("buy".equals(tradeType)) {
                buyHandler.executeVoid(update);
            } else {
                sellHandler.executeVoid(update);
            }
            
        } catch (Exception e) {
            log.error("Error processing token: {}", e.getMessage());
            MessageUtils.editMessage(update, "❌ Error processing token: " + e.getMessage(), null);
            // Reset state to trade_memecoin_start
            stateService.setState(telegramId, ChatState.TRADE_MEMECOIN_START);
        }
    }

    // Delegate copy trading methods to the CopyTradingHandler
    
    public void handleCopyTradingButton(Update update) {
        copyTradingHandler.handleCopyTradingButton(update);
    }
    
    public void handleAddCopyTradingConfig(Update update) {
        copyTradingHandler.handleAddCopyTradingConfig(update);
    }
    
    public void processSourceWalletInput(Update update) {
        copyTradingHandler.processSourceWalletInput(update);
    }
    
    public void handleToggleCopyConfig(Update update, Long configId, boolean newEnabledState) {
        copyTradingHandler.handleToggleCopyConfig(update, configId, newEnabledState);
    }
    
    public void handleDeleteCopyConfig(Update update, Long configId) {
        copyTradingHandler.handleDeleteCopyConfig(update, configId);
    }

    /**
     * Getter for the CopyTradingHandler
     * @return the copy trading handler
     */
    public CopyTradingHandler getCopyTradingHandler() {
        return copyTradingHandler;
    }

    /**
     * Handle the My Portfolio button click
     */
    public void handleMyPortfolio(Update update, int page) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        // Get user
        User user = tokenDetailsService.getUserFromCacheOrDatabase(telegramId);
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
        
        try {
            // Delete the current message
            MessageUtils.deleteMessage(update);
            
            // Send loading message
            Message loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your portfolio from blockchain... _(This may take a few seconds)_");
            
            // Get token details (either from cache or by fetching)
            List<Map<String, Object>> tokenDetails = tokenDetailsService.getTokenDetailsWithCache(walletAddress);
            
            // Delete loading message
            if (loadingMessage != null) {
                MessageUtils.deleteMessage(loadingMessage);
            }
            
            int totalPages = (int) Math.ceil((double) tokenDetails.size() / PAGE_SIZE);
            if (totalPages == 0) totalPages = 1; // At least one page even if empty
            
            // Calculate start and end indices for current page
            int startIndex = page * PAGE_SIZE;
            int endIndex = Math.min(startIndex + PAGE_SIZE, tokenDetails.size());
            
            // Create formatted message with code blocks
            StringBuilder formattedList = new StringBuilder();
            formattedList.append("*My Portfolio*\n\n");
            
            if (tokenDetails.isEmpty()) {
                formattedList.append("You don't have any tokens yet. Use the buttons below to trade.");
            } else {
                for (int i = startIndex; i < endIndex; i++) {
                    Map<String, Object> token = tokenDetails.get(i);
                    String address = (String) token.get("address");
                    double balance = (Double) token.get("balance");
                    double priceUSD = (Double) token.get("priceUSD");
                    
                    // Format the token info in code blocks
                    formattedList.append(String.format(
                        "```\n%d. %s\nBalance: %.4f\nPrice: $%.6f\n```\n\n",
                        i + 1, address, balance, priceUSD
                    ));
                }
                
                // Add pagination info
                formattedList.append(String.format("Page %d of %d", page + 1, totalPages));
            }
            
            // Create keyboard markup with buttons
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            
            // Pagination buttons if needed
            if (totalPages > 1) {
                List<InlineKeyboardButton> paginationRow = new ArrayList<>();
                
                // Previous page button
                if (page > 0) {
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("◀️ Previous")
                        .callbackData("portfolio_page_" + (page - 1))
                        .build());
                }
                
                // Next page button
                if (page < totalPages - 1) {
                    paginationRow.add(InlineKeyboardButton.builder()
                        .text("Next ▶️")
                        .callbackData("portfolio_page_" + (page + 1))
                        .build());
                }
                
                buttons.add(paginationRow);
            }
            
            // Back to Trading button
            List<InlineKeyboardButton> backRow = new ArrayList<>();
            backRow.add(InlineKeyboardButton.builder()
                .text("Back to Trading")
                .callbackData("back_to_trading")
                .build());
            buttons.add(backRow);
            
            markup.setKeyboard(buttons);
            
            // Send new message with portfolio
            MessageUtils.sendMessage(update, formattedList.toString(), markup);
            
        } catch (Exception e) {
            log.error("Error loading portfolio: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error loading portfolio: " + e.getMessage());
        }
    }
}
