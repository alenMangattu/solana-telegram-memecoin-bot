package com.lunax.telegramBot.component.navigation.components.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class CopyTradingPaginationHandler implements ButtonHandler {

    @Autowired
    private CopyTradingHandler copyTradingHandler;

    @Override
    public String getButtonText() {
        return "Page"; // Not directly shown on a button
    }

    @Override
    public String getCallbackData() {
        return "copy_trading_page_"; // Prefix
    }

    @Override
    public void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        String[] parts = callbackData.split("_");
        if (parts.length >= 4) {
            try {
                int page = Integer.parseInt(parts[3]);
                copyTradingHandler.handleCopyTradingPage(update, page);
            } catch (NumberFormatException e) {
                // Handle invalid page number
            }
        }
    }
} 