package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ReturnToTokenInfoButton implements ButtonHandler {

    @Autowired
    private StateService stateService;
    
    @Autowired
    @Lazy
    private TokenInfo tokenInfo;

    @Override
    public String getButtonText() {
        return "🔙 Return to Token Info";
    }

    @Override
    public String getCallbackData() {
        return "return_to_token_info";
    }

    @Override
    public void handleCallback(Update update) {
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        try {
            // Get token information from state
            ObjectNode stateData = stateService.getStateData(userId);
            
            if (!stateData.has("tokenInfoMessage")) {
                MessageUtils.sendMessage(update, "Error: Token information not found.");
                return;
            }
            
            // Delete the current message
            MessageUtils.deleteMessage(update);
            
            // Re-send the original token info message
            if (stateData.has("tokenChartImagePath")) {
                String imagePath = stateData.get("tokenChartImagePath").asText();
                String tokenInfoMessage = stateData.get("tokenInfoMessage").asText();
                
                // Create keyboard with Top Holders button
                ButtonHandler[][] keyboard = {
                    { new TopHoldersButton() }
                };
                
                MessageUtils.sendImageMessageInlineKeyboard(update, imagePath, tokenInfoMessage, keyboard);
            } else {
                // If no image path, fall back to text message
                MessageUtils.sendMessage(update, stateData.get("tokenInfoMessage").asText());
            }
            
            // Update state
            stateData.put("viewingTopHolders", false);
            stateService.setStateData(userId, stateData);
            
        } catch (Exception e) {
            log.error("Error returning to token info: {}", e.getMessage());
            MessageUtils.sendMessage(update, "An error occurred while returning to token information: " + e.getMessage());
        }
    }
} 