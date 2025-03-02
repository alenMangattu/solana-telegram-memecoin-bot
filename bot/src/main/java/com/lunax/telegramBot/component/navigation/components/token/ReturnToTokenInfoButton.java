package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ReturnToTokenInfoButton implements ButtonHandler {

    @Autowired
    private StateService stateService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public String getButtonText() {
        return "Back to Token Info";
    }

    @Override
    public String getCallbackData() {
        return "return_to_token_info";
    }

    @Override
    public void handleCallback(Update update) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        try {
            // Get token information from state
            ObjectNode stateData = stateService.getStateData(userId);
            
            if (!stateData.has("tokenInfoMessage")) {
                MessageUtils.sendMessage(update, "Error: Token information not found.");
                return;
            }
            
            // Get the original token info message
            String tokenInfoMessage = stateData.get("tokenInfoMessage").asText();
            
            // Update state to indicate we're viewing token info
            stateData.put("viewingTopHolders", false);
            stateService.setStateData(userId, stateData);
            
            // Publish navigation event
            eventPublisher.publishEvent(new NavigationEvent(this, update, "token_info"));
            
        } catch (Exception e) {
            log.error("Error returning to token info: {}", e.getMessage());
            MessageUtils.sendMessage(update, "An error occurred while returning to token information: " + e.getMessage());
        }
    }
} 