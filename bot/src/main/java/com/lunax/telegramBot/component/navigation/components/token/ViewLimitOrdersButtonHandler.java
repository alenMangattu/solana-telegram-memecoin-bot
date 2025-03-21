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
public class ViewLimitOrdersButtonHandler implements ButtonHandler {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public ViewLimitOrdersButtonHandler() {
        // No dependencies in constructor
    }

    @Override
    public String getButtonText() {
        return "View Limit Orders"; 
    }

    @Override
    public String getCallbackData() {
        return "view_limit_orders"; 
    }

    @Override
    public void handleCallback(Update update) {
        log.debug("Received view limit orders callback");
        
        // Publish a navigation event to view limit orders
        eventPublisher.publishEvent(new NavigationEvent(this, update, "view_limit_orders"));
    }
} 