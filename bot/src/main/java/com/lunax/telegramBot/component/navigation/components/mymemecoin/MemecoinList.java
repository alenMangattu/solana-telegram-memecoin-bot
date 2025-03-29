package com.lunax.telegramBot.component.navigation.components.mymemecoin;

import java.util.ArrayList;
import java.util.List;

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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;

@Slf4j
@Component
public class MemecoinList implements StateHandler {

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StateService stateService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int PAGE_SIZE = 5; // Number of memecoins per page

    @Override
    public void executeVoid(Update update) {
        executeVoid(update, 0); // Default to the first page if no page is specified
    }

    @SuppressWarnings({ "unchecked", "null" })
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

        // Get current state data first
        ObjectNode stateData = stateService.getStateData(telegramId);

        boolean tokenReplyMessageIdExists = stateData != null && stateData.has("tokenReplyMessageId");

        // Clean up previous token selection message if exists
        int previousMessageId = 0;
        if (tokenReplyMessageIdExists) {
            previousMessageId = stateData.get("tokenReplyMessageId").asInt();
        }

        // Now set the state
        stateService.setState(telegramId, ChatState.AWAITING_TOKEN_SELECTION);

        // Create fresh state data but preserve the tokenReplyMessageId if it exists
        stateData = objectMapper.createObjectNode();
        if (tokenReplyMessageIdExists) {
            // Keep track of messages already sent to prevent duplicates
            stateData.put("tokenReplyMessageId", previousMessageId);
        }
        stateService.setStateData(telegramId, stateData);

        User user = getUserFromCacheOrDatabase(telegramId);

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
            tokenAddresses = cachedValue instanceof List<?> ? new ArrayList<>((List<String>) cachedValue)
                    : new ArrayList<>();
            log.info("Retrieved token addresses from cache for wallet: {}", walletAddress);
        } else {
            // Fetch from Solana and cache the result
            JsonNode createdTokens = solanaUtils.getTokensCreatedByAccount(walletAddress);
            tokenAddresses = new ArrayList<>();
            
            if (createdTokens != null && createdTokens.has("accounts") && createdTokens.get("accounts").isArray()) {
                for (JsonNode account : createdTokens.get("accounts")) {
                    if (account.has("mint")) {
                        tokenAddresses.add(account.get("mint").asText());
                    }
                }
            }
            
            // Save to cache
            if (userTokenAccountsCache != null) {
                userTokenAccountsCache.put(walletAddress, tokenAddresses);
                log.info("Saved token addresses to cache for wallet: {}", walletAddress);
            }
        }

        // Prepare the formatted list of tokens
        StringBuilder formattedList = new StringBuilder();
        formattedList.append("📜 *Here are the tokens where you are either the owner or mint (Page " + (page + 1) + "):*\n\n");

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

            // Get state data, ensuring it's never null
            ObjectNode stateData2 = stateService.getStateData(telegramId);
            if (stateData2 == null) {
                stateData2 = objectMapper.createObjectNode();
                stateService.setStateData(telegramId, stateData2);
            }

            // Only send token selection message if we haven't sent one already
            boolean messageSent = stateData2.has("tokenReplyMessageIdSent") &&
                    stateData2.get("tokenReplyMessageIdSent").asBoolean();

            if (!messageSent && !stateData2.has("tokenReplyMessageId")) {
                try {
                    // Set state for awaiting token selection BEFORE setting state data
                    stateService.setState(telegramId, ChatState.AWAITING_TOKEN_SELECTION);

                    // Now get the state data and update it
                    stateData2 = stateService.getStateData(telegramId);
                    if (stateData2 == null) {
                        stateData2 = objectMapper.createObjectNode();
                    }

                    stateData2.put("awaitingTokenAddress", true);

                    if (!tokenReplyMessageIdExists) {
                        SendMessage replyMessage = SendMessage.builder()
                                .chatId(originalMessage.getChatId().toString())
                                .text("Please enter a valid Solana token address to see more information:")
                                .parseMode("Markdown")
                                .build();

                        Message sentMessage = MessageUtils.getBotInstance().execute(replyMessage);

                        // Store the message ID in state data
                        stateData2.put("tokenReplyMessageId", sentMessage.getMessageId());
                        // Mark that we've sent a message to prevent duplicates
                        stateData2.put("tokenReplyMessageIdSent", true);

                        log.info("Sent token selection message to user {} with message ID {}", telegramId,
                                sentMessage.getMessageId());
                    }

                    stateService.setStateData(telegramId, stateData2);
                } catch (Exception e) {
                    log.error("Error sending token selection message: {}", e.getMessage());
                }
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
    // if (memecoinCache.isEmpty()) {
    // List<Memecoin> memecoins = memecoinRepo.findAll();
    // for (Memecoin memecoin : memecoins) {
    // memecoinCache.put(memecoin.getId(), memecoin);
    // }
    // }
    // return new ArrayList<>(memecoinCache.values());
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
            log.info("==========================");
        }
    }

    // Method to get user from cache or database
    @Cacheable(value = "userInfo", key = "#telegramId")
    private User getUserFromCacheOrDatabase(Long telegramId) {
        // Check if user data is cached
        User user = userService.findByTelegramId(telegramId).orElse(null);
        return user;
    }

    // You can call this method from your controller or handler for /start command
    public void resetUserState(Long telegramId, Long chatId) {
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData != null && stateData.has("tokenReplyMessageId")) {
            try {
                int previousMessageId = stateData.get("tokenReplyMessageId").asInt();
                DeleteMessage deleteMessage = new DeleteMessage();
                deleteMessage.setChatId(chatId.toString());
                deleteMessage.setMessageId(previousMessageId);
                MessageUtils.getBotInstance().execute(deleteMessage);
                log.info("Deleted token selection message during state reset: {}", previousMessageId);
            } catch (Exception e) {
                log.warn("Could not delete token selection message during state reset: {}", e.getMessage());
            }
        }

        // First set the state to IDLE to ensure current_page is not null
        stateService.setState(telegramId, ChatState.IDLE);

        // Then create and set the clean state data
        ObjectNode newStateData = objectMapper.createObjectNode();
        stateService.setStateData(telegramId, newStateData);
    }
}