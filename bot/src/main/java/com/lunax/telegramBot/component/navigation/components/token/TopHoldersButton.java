package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TopHoldersButton implements ButtonHandler {

    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private StateService stateService;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public String getButtonText() {
        return "👥 Top Holders";
    }

    @Override
    public String getCallbackData() {
        return "token_top_holders";
    }

    @Override
    public void handleCallback(Update update) {
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        try {
            // Get token information from state
            ObjectNode stateData = stateService.getStateData(userId);
            
            if (!stateData.has("tokenAddress")) {
                MessageUtils.sendMessage(update, "Error: Token information not found.");
                return;
            }
            
            String tokenAddress = stateData.get("tokenAddress").asText();
            String tokenName = stateData.get("tokenName").asText();
            String tokenSymbol = stateData.get("tokenSymbol").asText();
            
            // Fetch top 10 holders
            List<Map<String, Object>> topHolders = solanaUtils.getTopTokenHolders(tokenAddress, 10);
            
            if (topHolders == null || topHolders.isEmpty()) {
                MessageUtils.sendMessage(update, "No holder information found for this token.");
                return;
            }
            
            // Build the message with top holders information
            StringBuilder message = new StringBuilder();
            message.append(String.format("👥 *Top Holders for %s (%s)*\n\n", tokenName, tokenSymbol));
            
            int rank = 1;
            for (Map<String, Object> holder : topHolders) {
                String address = (String) holder.get("address");
                Long amount = (Long) holder.get("amount");
                Double percentage = (Double) holder.get("percentage");
                
                // Shorten address for display
                String shortAddress = address.substring(0, 4) + "..." + address.substring(address.length() - 4);
                
                message.append(String.format(
                    "%d. `%s`: %,d tokens (%.2f%%)\n", 
                    rank++, 
                    shortAddress, 
                    amount,
                    percentage
                ));
            }
            
            // Update the message with holder information
            MessageUtils.editMessageText(
                chatId,
                messageId,
                message.toString(),
                null
            );
            
            // Update state to indicate we're viewing top holders
            stateData.put("viewingTopHolders", true);
            stateService.setStateData(userId, stateData);
            
            // Publish navigation event
            eventPublisher.publishEvent(new NavigationEvent(this, update, "token_holders"));
            
        } catch (Exception e) {
            log.error("Error displaying top holders: {}", e.getMessage());
            MessageUtils.sendMessage(update, "An error occurred while fetching top holders: " + e.getMessage());
        }
    }
} 