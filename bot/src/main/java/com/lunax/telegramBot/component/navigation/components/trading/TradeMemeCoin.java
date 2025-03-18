package com.lunax.telegramBot.component.navigation.components.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TradeMemeCoin implements StateHandler {
    
    private static final int PAGE_SIZE = 5; // Number of tokens per page

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;
    
    @Autowired
    private Buy buyHandler;
    
    @Autowired
    private Sell sellHandler;

   
    
    @Override
    public void executeVoid(Update update) {
        executeVoid(update, 0); // Default to the first page
    }
    
    public void executeVoid(Update update, int page) {
        // Get user ID
        Long telegramId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getFrom().getId() : 
            update.getMessage().getFrom().getId();
        
        // Get chat ID
        Long chatId = update.hasCallbackQuery() ? 
            update.getCallbackQuery().getMessage().getChatId() : 
            update.getMessage().getChatId();
        
        // Get user
        User user = getUserFromCacheOrDatabase(telegramId);
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
        ObjectNode stateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, stateData);
        
        // Show loading message
        Message loadingMessage;
        if (update.hasCallbackQuery()) {
            // Edit the existing message
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId.toString());
            editMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            editMessage.setText("⟳ Loading your tokens...");
            
            try {
                MessageUtils.getBotInstance().execute(editMessage);
                loadingMessage = (Message) update.getCallbackQuery().getMessage();
            } catch (TelegramApiException e) {
                log.error("Error showing loading message: {}", e.getMessage());
                // Send a new message if editing fails
                loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your tokens...");
            }
        } else {
            // Send a new message
            loadingMessage = MessageUtils.sendMessage(update, "⟳ Loading your tokens...");
        }
        
        // Fetch token accounts and balances
        List<Map<String, Object>> tokenDetails = getTokenDetailsWithCache(walletAddress);
        
        // Format the message
        StringBuilder formattedList = new StringBuilder();
        formattedList.append("📜 *Here is the list of your tokens you are holding (Page " + (page + 1) + "):*\n\n");
        formattedList.append("```\n");
        formattedList.append(String.format("%-44s %-15s %s\n", "Token", "Balance", "Price"));
        formattedList.append("-------------------------------------------------------------\n");
        
        int totalTokens = tokenDetails.size();
        int totalPages = (int) Math.ceil((double) totalTokens / PAGE_SIZE);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalTokens);
        
        for (int i = start; i < end; i++) {
            Map<String, Object> token = tokenDetails.get(i);
            String address = (String) token.get("address");
            double balance = (double) token.get("balance");
            double priceUSD = (double) token.get("priceUSD");
            
            // Truncate address for display
            String shortAddress = address;
            if (address.length() > 30) {
                shortAddress = address.substring(0, 30) + "...";
            }
            
            // Format the balance and price
            String balanceStr = String.format("%.6f", balance);
            String priceStr = String.format("$%.2f", priceUSD * balance);
            
            formattedList.append(String.format("%-44s %-15s %s\n", 
                shortAddress, balanceStr, priceStr));
        }
        formattedList.append("```\n\n");
        formattedList.append("Select a token address or use the buttons below.");
        
        // Create pagination buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        
        // Pagination row
        List<InlineKeyboardButton> paginationRow = new ArrayList<>();
        if (page > 0) {
            // Previous page button
            InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                    .text("⬅️ Page " + page)
                    .callbackData("trade_page_" + (page - 1))
                    .build();
            paginationRow.add(prevButton);
        }
        
        if (page < totalPages - 1) {
            // Next page button
            InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                    .text("Page " + (page + 2) + " ➡️")
                    .callbackData("trade_page_" + (page + 1))
                    .build();
            paginationRow.add(nextButton);
        }
        
        if (!paginationRow.isEmpty()) {
            buttons.add(paginationRow);
        }
        
        // Trading action buttons in multiple rows
        List<InlineKeyboardButton> actionRow1 = new ArrayList<>();
        actionRow1.add(InlineKeyboardButton.builder()
                .text("Create Limit Order")
                .callbackData("create_limit_order")
                .build());
        buttons.add(actionRow1);
        
        List<InlineKeyboardButton> actionRow2 = new ArrayList<>();
        actionRow2.add(InlineKeyboardButton.builder()
                .text("View Limit Orders")
                .callbackData("view_limit_orders")
                .build());
        actionRow2.add(InlineKeyboardButton.builder()
                .text("Delete Limit Order")
                .callbackData("delete_limit_order")
                .build());
        buttons.add(actionRow2);
        
        List<InlineKeyboardButton> actionRow3 = new ArrayList<>();
        actionRow3.add(InlineKeyboardButton.builder()
                .text("Copy Trading Page")
                .callbackData("copy_trading_page")
                .build());
        buttons.add(actionRow3);
        
        List<InlineKeyboardButton> actionRow4 = new ArrayList<>();
        actionRow4.add(InlineKeyboardButton.builder()
                .text("🔄 Buy")
                .callbackData("buy_token")
                .build());
        actionRow4.add(InlineKeyboardButton.builder()
                .text("💰 Sell")
                .callbackData("sell_token")
                .build());
        buttons.add(actionRow4);
        
        // Home button
        List<InlineKeyboardButton> homeRow = new ArrayList<>();
        homeRow.add(InlineKeyboardButton.builder()
                .text("Back to Home")
                .callbackData("back_to_home")
                .build());
        buttons.add(homeRow);
        
        markup.setKeyboard(buttons);
        
        // Edit the loading message with the token list
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(loadingMessage.getMessageId());
        editMessage.setText(formattedList.toString());
        editMessage.setParseMode("Markdown");
        editMessage.setReplyMarkup(markup);
        
        try {
            MessageUtils.getBotInstance().execute(editMessage);
            
            // Store page information in state data
            stateData.put("currentPage", page);
            stateService.setStateData(telegramId, stateData);
            
            // Ask for token selection
            SendMessage replyMessage = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text("Please enter the token address to trade or select an action from the buttons:")
                    .parseMode("Markdown")
                    .build();
            
            Message sentMessage = MessageUtils.getBotInstance().execute(replyMessage);
            stateData.put("tokenSelectionMessageId", sentMessage.getMessageId());
            stateService.setStateData(telegramId, stateData);
            
        } catch (TelegramApiException e) {
            log.error("Error showing token list: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error displaying token list: " + e.getMessage());
        }
    }
    
    @Cacheable(value = "tokenDetailsCache", key = "#walletAddress")
    public List<Map<String, Object>> getTokenDetailsWithCache(String walletAddress) {
        // Cache implementation
        Cache tokenDetailsCache = cacheManager.getCache("tokenDetailsCache");
        if (tokenDetailsCache != null) {
            Cache.ValueWrapper cachedValue = tokenDetailsCache.get(walletAddress);
            if (cachedValue != null && cachedValue.get() != null) {
                log.info("Retrieved token details from cache for wallet: {}", walletAddress);
                return (List<Map<String, Object>>) cachedValue.get();
            }
        }
        
        // Not in cache, fetch data
        List<String> tokenAddresses = solanaUtils.getTokenAccounts(walletAddress);
        List<Map<String, Object>> tokenDetails = new ArrayList<>();
        
        for (String tokenAddress : tokenAddresses) {
            Map<String, Object> tokenDetail = new HashMap<>();
            tokenDetail.put("address", tokenAddress);
            
            // Get balance
            double balance = 0.0;
            try {
                balance = solanaUtils.getTokenBalance(walletAddress, tokenAddress);
            } catch (Exception e) {
                log.error("Error getting balance for token {}: {}", tokenAddress, e.getMessage());
            }
            tokenDetail.put("balance", balance);
            
            // Get price
            double priceUSD = 0.0;
            try {
                Map<String, Object> tokenInfo = solanaUtils.getMemecoinDetails(tokenAddress);
                if (tokenInfo.containsKey("currentPrice")) {
                    priceUSD = (Double) tokenInfo.get("currentPrice");
                }
            } catch (Exception e) {
                log.error("Error getting price for token {}: {}", tokenAddress, e.getMessage());
            }
            tokenDetail.put("priceUSD", priceUSD);
            
            tokenDetails.add(tokenDetail);
        }
        
        // Store in cache
        if (tokenDetailsCache != null) {
            tokenDetailsCache.put(walletAddress, tokenDetails);
            log.info("Saved token details to cache for wallet: {}", walletAddress);
        }
        
        return tokenDetails;
    }
    
    @Cacheable(value = "userInfo", key = "#telegramId")
    private User getUserFromCacheOrDatabase(Long telegramId) {
        return userService.findByTelegramId(telegramId).orElse(null);
    }
}
