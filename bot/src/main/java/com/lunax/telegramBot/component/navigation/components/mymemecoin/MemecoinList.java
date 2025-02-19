package com.lunax.telegramBot.component.navigation.components.mymemecoin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.model.repository.MemecoinRepo;
import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.SolanaUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MemecoinList implements StateHandler {

    @Autowired
    private MemecoinRepo memecoinRepo;

    @Autowired
    private SolanaUtils solanaUtils;

    private Map<Long, Memecoin> memecoinCache = new ConcurrentHashMap<>();
    private static final int PAGE_SIZE = 5; // Number of memecoins per page

    @Override
    public void executeVoid(Update update) {
        executeVoid(update, 0); // Default to the first page if no page is specified
    }

    @Override
    public void executeVoid(Update update, int page) {
        // Send a loading message first
        Message loadingMessage = MessageUtils.sendLoadingMessage(update);

        // Get the original message from the update
        Message originalMessage = (Message) update.getCallbackQuery().getMessage();

        // Check if the original message is accessible
        if (originalMessage == null) {
            log.error("Original message is inaccessible.");
            return; // Exit if the message is not accessible
        }

        // Now proceed to fetch and format the memecoin data
        List<Memecoin> memecoins = getMemecoins();
        int totalPages = (int) Math.ceil((double) memecoins.size() / PAGE_SIZE);
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, memecoins.size());

        StringBuilder formattedList = new StringBuilder();
        formattedList.append(String.format("%-20s %-10s %-15s %-15s %-15s %-15s %-15s %-15s %-15s\n", 
                "Name", "Symbol", "Total Supply", "Current Price", "Market Cap", "Liquidity", "Age", "Volume", "Makers"));
        formattedList.append("------------------------------------------------------------------------------------------\n");

        for (int i = start; i < end; i++) {
            Memecoin memecoin = memecoins.get(i);
            Map<String, Object> details = solanaUtils.getMemecoinDetails(memecoin.getTokenAddress());
            formattedList.append(String.format("%-20s %-10s %-15.2f %-15s %-15s %-15s %-15s %-15s %-15s\n", 
                    memecoin.getName(), 
                    memecoin.getSymbol(), 
                    memecoin.getTotalSupply(), 
                    details.get("currentPrice"), 
                    details.get("marketCap"), 
                    details.get("liquidity"), 
                    details.get("age"), 
                    details.get("volume"), 
                    details.get("makers")));
        }

        // Create pagination buttons
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>(); // Create a new row for buttons

        if (page > 0) {
            // Previous page button
            InlineKeyboardButton prevButton = InlineKeyboardButton.builder()
                    .text("Previous (Page " + (page + 1) + ")") // Show the current page number
                    .callbackData("page_" + (page - 1))
                    .build();
            row.add(prevButton); // Add to the row
        }

        if (page < totalPages - 1) {
            // Next page button
            InlineKeyboardButton nextButton = InlineKeyboardButton.builder()
                    .text("Next (Page " + (page + 1) + ")") // Show the next page number
                    .callbackData("page_" + (page + 1))
                    .build();
            row.add(nextButton); // Add to the row
        }

        buttons.add(row); // Add the row to the buttons list
        markup.setKeyboard(buttons); // Set the keyboard

        // Edit the loading message with the actual data
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(loadingMessage.getChatId().toString());
        editMessage.setMessageId(loadingMessage.getMessageId());
        editMessage.setText(formattedList.toString());
        editMessage.setReplyMarkup(markup);

        try {
            // Execute the edit message
            MessageUtils.getBotInstance().execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Error editing message: {}", e.getMessage());
        }
    }

    private List<Memecoin> getMemecoins() {
        if (memecoinCache.isEmpty()) {
            List<Memecoin> memecoins = memecoinRepo.findAll();
            for (Memecoin memecoin : memecoins) {
                memecoinCache.put(memecoin.getId(), memecoin);
            }
        }
        return new ArrayList<>(memecoinCache.values());
    }
}