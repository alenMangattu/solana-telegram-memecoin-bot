package com.lunax.telegramBot.component.navigation.components.mymemecoin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.springframework.context.ApplicationEventPublisher;

import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.repository.MemecoinRepo;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MemecoinList implements StateHandler {

    @Autowired
    private MemecoinRepo memecoinRepo;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private StateService stateService;

    private Map<Long, Memecoin> memecoinCache = new ConcurrentHashMap<>();
    private static final int PAGE_SIZE = 5; // Number of memecoins per page

    @Override
    public void executeVoid(Update update) {
        executeVoid(update, 0); // Default to the first page if no page is specified
    }

    @SuppressWarnings("unchecked")
    @Override
    public void executeVoid(Update update, int page) {
        // Print cache contents at the start of each request
        printCacheContents();

        // Get the original message from the update
        Message originalMessage = (Message) update.getCallbackQuery().getMessage();

        // Check if the original message is accessible
        if (originalMessage == null) {
            log.error("Original message is inaccessible.");
            return; // Exit if the message is not accessible
        }

        // Fetch the user based on the Telegram ID
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        User user = userService.findByTelegramId(telegramId).orElse(null);

        if (user == null) {
            log.error("User not found for Telegram ID: {}", telegramId);
            return; // Exit if user is not found
        }

        // Check if data is in cache
        Cache userTokenAccountsCache = cacheManager.getCache("userTokenAccounts");
        String walletAddress = user.getCurrentWalletAddress();
        boolean isDataCached = false;

        if (userTokenAccountsCache != null && walletAddress != null) {
            Cache.ValueWrapper cachedValue = userTokenAccountsCache.get(walletAddress);
            isDataCached = (cachedValue != null && cachedValue.get() != null);
        }

        // Only show loading if data is not cached
        if (!isDataCached) {
            EditMessageText loadingMessage = new EditMessageText();
            loadingMessage.setChatId(originalMessage.getChatId().toString());
            loadingMessage.setMessageId(originalMessage.getMessageId());
            loadingMessage.setText("⟳ Loading...");
            loadingMessage.setReplyMarkup(null); // Remove keyboard

            try {
                MessageUtils.getBotInstance().execute(loadingMessage);
            } catch (TelegramApiException e) {
                log.error("Error setting loading message: {}", e.getMessage());
            }
        }

        // Fetch token accounts for the user
        if (walletAddress == null) {
            log.error("Current wallet not set for user: {}", telegramId);
            return; // Exit if wallet is not found
        }

        List<String> tokenAddresses;
        if (isDataCached && userTokenAccountsCache != null) {
            // Get from cache
            Object cachedValue = userTokenAccountsCache.get(walletAddress).get();
            tokenAddresses = cachedValue instanceof List<?> ? new ArrayList<>((List<String>) cachedValue) : new ArrayList<>();
            log.info("Retrieved token addresses from cache for wallet: {}", walletAddress);
        } else {
            // Fetch from Solana
            tokenAddresses = solanaUtils.getTokenAccounts(walletAddress);
            // Save to cache
            if (userTokenAccountsCache != null) {
                userTokenAccountsCache.put(walletAddress, tokenAddresses);
                log.info("Saved token addresses to cache for wallet: {}", walletAddress);
            }
        }

        // Prepare the formatted list of tokens
        StringBuilder formattedList = new StringBuilder();
        formattedList.append("*Here is the list of your token holdings (Page " + (page + 1) + "):*\n\n");
        
        int totalTokens = tokenAddresses.size();
        int totalPages = (int) Math.ceil((double) totalTokens / PAGE_SIZE);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, totalTokens);

        for (int i = start; i < end; i++) {
            formattedList.append("• `").append(tokenAddresses.get(i)).append("`\n");
        }

        // Create pagination buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        if (page > 0) {
            // Previous page button
            InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                    .text("⬅️ Page " + page + "")
                    .callbackData("page_" + (page - 1))
                    .build();
            row.add(prevButton);
        }

        if (page < totalPages - 1) {
            // Next page button
            InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                    .text("Page " + (page + 2) + " ➡️")
                    .callbackData("page_" + (page + 1))
                    .build();
            row.add(nextButton);
        }

        buttons.add(row); // Add the pagination buttons row to the buttons list

        // Add Back to Home button in a new row
        List<InlineKeyboardButton> backRow = new ArrayList<>();
        backRow.add(createBackToHomeButton());
        buttons.add(backRow);

        markup.setKeyboard(buttons);

        // Edit the original message with the actual data
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(originalMessage.getChatId().toString());
        editMessage.setMessageId(originalMessage.getMessageId());
        editMessage.setText(formattedList.toString());
        editMessage.setParseMode("Markdown");
        editMessage.setReplyMarkup(markup);

        try {
            MessageUtils.getBotInstance().execute(editMessage);
            
            // Delete existing custom keyboard message if it exists
            ObjectNode stateData = stateService.getStateData(telegramId);
            Integer previousMessageId = null;
            if (stateData.has("tokenKeyboardMessageId")) {
                previousMessageId = stateData.get("tokenKeyboardMessageId").asInt();
            }

            if (previousMessageId != null) {
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(originalMessage.getChatId().toString());
                deleteMessage.setMessageId(previousMessageId);
                try {
                    MessageUtils.getBotInstance().execute(deleteMessage);
                } catch (TelegramApiException e) {
                    log.error("Error deleting previous keyboard message: {}", e.getMessage());
                }
            }

            // Create keyboard with current page tokens
            String[][] keyboardButtons = new String[end - start][1];
            for (int i = start; i < end; i++) {
                keyboardButtons[i - start] = new String[]{tokenAddresses.get(i)};
            }

            // Send new custom keyboard message
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            
            for (String[] rowButton : keyboardButtons) {
                KeyboardRow keyboardRow = new KeyboardRow();
                for (String button : rowButton) {
                    keyboardRow.add(button);
                }
                keyboard.add(keyboardRow);
            }
            
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setOneTimeKeyboard(true);
            keyboardMarkup.setResizeKeyboard(true);

            SendMessage keyboardMsg = SendMessage.builder()
                .chatId(originalMessage.getChatId().toString())
                .text("Enter the token address to see more information:")
                .replyMarkup(keyboardMarkup)
                .build();

            try {
                Message sentMessage = MessageUtils.getBotInstance().execute(keyboardMsg);
                
                // First set the state to await token selection
                stateService.setState(telegramId, ChatState.AWAITING_TOKEN_SELECTION);
                
                // Then store the message ID in state data
                ObjectNode newStateData = stateData.deepCopy();
                newStateData.put("tokenKeyboardMessageId", sentMessage.getMessageId());
                stateService.setStateData(telegramId, newStateData);
                
            } catch (TelegramApiException e) {
                log.error("Error sending keyboard message: {}", e.getMessage());
            }

        } catch (TelegramApiException e) {
            log.error("Error editing message: {}", e.getMessage());
        }

        // After fetching token accounts, print cache again to see changes
        log.info("Token accounts fetched for wallet: {}", walletAddress);
        printCacheContents();
    }

    private InlineKeyboardButton createBackToHomeButton() {
        return InlineKeyboardButton.builder()
                .text("Back to Home")
                .callbackData("back_to_home")
                .build();
    }

    // private List<Memecoin> getMemecoins() {
    //     if (memecoinCache.isEmpty()) {
    //         List<Memecoin> memecoins = memecoinRepo.findAll();
    //         for (Memecoin memecoin : memecoins) {
    //             memecoinCache.put(memecoin.getId(), memecoin);
    //         }
    //     }
    //     return new ArrayList<>(memecoinCache.values());
    // }

    private void printCacheContents() {
        Cache userTokenAccountsCache = cacheManager.getCache("userTokenAccounts");
        if (userTokenAccountsCache != null) {
            log.info("=== Current Cache Contents ===");
            if (userTokenAccountsCache instanceof ConcurrentMapCache) {
                ConcurrentMapCache concurrentCache = (ConcurrentMapCache) userTokenAccountsCache;
                concurrentCache.getNativeCache().forEach((key, value) -> {
                    log.info("Cache Key: {}", key);
                    log.info("Cache Value: {}", value);
                });
            }
            log.info("===========================");
        }
    }

    // Add method to handle token selection
    public void handleTokenSelection(Update update) {
        String selectedToken = update.getMessage().getText();
        Long telegramId = update.getMessage().getFrom().getId();
        
        // Here you can add logic to show token information
        MessageUtils.sendMessage(update, "Selected token: " + selectedToken);
        
        // Reset state
        stateService.setState(telegramId, ChatState.IDLE);
        stateService.clearStateData(telegramId);
    }
}