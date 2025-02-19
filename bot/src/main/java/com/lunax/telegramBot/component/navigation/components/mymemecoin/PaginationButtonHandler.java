package com.lunax.telegramBot.component.navigation.components.mymemecoin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class PaginationButtonHandler implements ButtonHandler {
    private final MemecoinList memecoinList;

    @Autowired
    public PaginationButtonHandler(MemecoinList memecoinList) {
        this.memecoinList = memecoinList;
    }

    @Override
    public String getButtonText() {
        return "Page Navigation"; // This can be customized
    }

    @Override
    public String getCallbackData() {
        return "page_"; // Changed to match the prefix of pagination callbacks
    }

    @Override
    public void handleCallback(Update update) {
        // Extract the page number from the callback data
        String callbackData = update.getCallbackQuery().getData();
        System.out.println("Received callback data: " + callbackData); // Debugging line
        if (callbackData.startsWith("page_")) {
            int page = Integer.parseInt(callbackData.split("_")[1]); // Assuming the format is "page_<number>"
            // Call the executeVoid method with the page number
            memecoinList.executeVoid(update, page);
        }
    }
} 