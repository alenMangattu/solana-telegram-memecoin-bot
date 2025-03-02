package com.lunax.telegramBot.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;
import com.lunax.telegramBot.component.navigation.components.token.TopHoldersButton;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NavigationEventListener {

    @Autowired
    private StateService stateService;
    
    @Autowired
    private TokenInfo tokenInfo;
    
    // You would add other handlers here as needed
    
    @EventListener
    public void handleNavigationEvent(NavigationEvent event) {
        log.info("Navigation event received: {}", event.getDestination());
        
        String destination = event.getDestination();
        Long userId = event.getUpdate().getCallbackQuery().getFrom().getId();
        Long chatId = event.getUpdate().getCallbackQuery().getMessage().getChatId();
        Integer messageId = event.getUpdate().getCallbackQuery().getMessage().getMessageId();
        
        switch (destination) {
            case "token_info":
                ObjectNode stateData = stateService.getStateData(userId);
                
                if (stateData != null && stateData.has("tokenInfoMessage") && stateData.has("tokenAddress")) {
                    String tokenInfoMessage = stateData.get("tokenInfoMessage").asText();
                    
                    // Create keyboard with Top Holders button
                    ButtonHandler[][] keyboard = {
                        { new TopHoldersButton() }
                    };
                    
                    // Edit the message to show token info with the button
                    MessageUtils.editMessageText(chatId, messageId, tokenInfoMessage, keyboard);
                } else {
                    MessageUtils.editMessageText(chatId, messageId, "Token information not available. Please search for a token again.", null);
                }
                break;
                
            case "token_holders":
                // This is already handled in TopHoldersButton.java
                break;
                
            default:
                log.warn("Unknown navigation destination: {}", destination);
        }
    }
} 