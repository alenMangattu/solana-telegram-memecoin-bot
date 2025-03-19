package com.lunax.telegramBot.component.navigation.components.trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BackToTradingButtonHandler implements ButtonHandler {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public String getButtonText() {
        return "Back to Trading"; 
    }

    @Override
    public String getCallbackData() {
        return "back_to_trading"; 
    }

    @Override
    public void handleCallback(Update update) {
        log.debug("Received back to trading callback");
        
        // Navigate back to the trading page
        eventPublisher.publishEvent(new NavigationEvent(this, update, "trade_page_0"));
    }
} 