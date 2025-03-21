package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TradePaginationButtonHandler implements ButtonHandler {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public TradePaginationButtonHandler() {
        // No dependencies in constructor
    }

    @Override
    public String getButtonText() {
        return "Trade Page Navigation"; // This is for identification only
    }

    @Override
    public String getCallbackData() {
        return "trade_page_"; // Prefix for trade pagination callbacks
    }

    @Override
    public void handleCallback(Update update) {
        // Extract the page number from the callback data
        String callbackData = update.getCallbackQuery().getData();
        log.debug("Received trade pagination callback: {}", callbackData);
        
        if (callbackData.startsWith("trade_page_")) {
            int page = Integer.parseInt(callbackData.split("_")[2]); // Format is "trade_page_<number>"
            // Create a custom event with the page number as part of the event data
            String destination = "trade_page_" + page;
            eventPublisher.publishEvent(new NavigationEvent(this, update, destination));
        }
    }
} 