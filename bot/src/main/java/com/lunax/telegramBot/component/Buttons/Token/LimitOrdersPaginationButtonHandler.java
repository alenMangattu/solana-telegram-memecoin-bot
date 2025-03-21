package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.trading.ViewLimitOrders;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LimitOrdersPaginationButtonHandler implements ButtonHandler {
    
    @Autowired
    private ViewLimitOrders viewLimitOrders;

    @Override
    public String getButtonText() {
        return "Limit Orders Pagination"; 
    }

    @Override
    public String getCallbackData() {
        return "limit_orders_page_"; 
    }

    @Override
    public void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        log.debug("Received limit orders pagination callback: {}", callbackData);
        
        if (callbackData.startsWith("limit_orders_page_")) {
            try {
                int page = Integer.parseInt(callbackData.split("_")[3]); // Format is "limit_orders_page_<number>"
                viewLimitOrders.executeVoid(update, page);
            } catch (Exception e) {
                log.error("Error parsing page number from callback: {}", callbackData, e);
                MessageUtils.sendMessage(update, "❌ Error navigating to the requested page");
            }
        }
    }
} 