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
public class DeleteLimitOrderButtonHandler implements ButtonHandler {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public DeleteLimitOrderButtonHandler() {
        // No dependencies in constructor
    }

    @Override
    public String getButtonText() {
        return "Delete Limit Order"; 
    }

    @Override
    public String getCallbackData() {
        return "delete_limit_order"; 
    }

    @Override
    public void handleCallback(Update update) {
        log.debug("Received delete limit order callback");
        
        // Publish a navigation event to delete limit order screen
        eventPublisher.publishEvent(new NavigationEvent(this, update, "delete_limit_order"));
    }
} 