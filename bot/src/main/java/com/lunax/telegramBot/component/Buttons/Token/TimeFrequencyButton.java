package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.components.TimeUtils;

@Component
public class TimeFrequencyButton implements ButtonHandler {
    private final String frequency;
    private final String buttonText;
    private final String callbackData;

    @Autowired
    private StateService stateService;
    
    @Autowired
    @Lazy
    private TokenInfo tokenInfo;

    // Use constructor injection to create different instances
    public TimeFrequencyButton(String frequency, String buttonText) {
        // Store frequency in the correct format - lowercase 'm' for minutes
        if (frequency.endsWith("m")) {
            // For minutes
            this.frequency = frequency.substring(0, frequency.length()-1) + "m";
        } else if (frequency.endsWith("M")) {
            // For minutes
            this.frequency = frequency.substring(0, frequency.length()-1) + "M";
        }
         else if (frequency.endsWith("h") || frequency.endsWith("H")) {
            // For hours
            this.frequency = frequency.substring(0, frequency.length()-1) + "H";
        } else if (frequency.endsWith("d") || frequency.endsWith("D")) {
            // For days
            this.frequency = frequency.substring(0, frequency.length()-1) + "D";
        } else {
            // Default case
            this.frequency = frequency;
        }
        
        this.buttonText = buttonText;
        this.callbackData = "time_freq_" + this.frequency;
    }

    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }

    @Override
    public ChatState getNextState() {
        return ChatState.TOKEN_INFO_PAGE;
    }

    @Override
    public void handleCallback(Update update) {
        Long userId = update.getCallbackQuery().getFrom().getId();
        ObjectNode stateData = stateService.getStateData(userId);
        
        if (stateData != null && stateData.has("tokenAddress")) {
            String currentPeriod = stateData.has("timeBefore") ? 
                stateData.get("timeBefore").asText() : "1D"; // Default

            // Validate frequency against the current period
            String validatedFrequency = TimeUtils.validateTimeFrequency(frequency, currentPeriod);
            if (validatedFrequency == null) {
                MessageUtils.sendAnswerCallbackQuery(update, 
                    "You cant change the frequency to " + frequency + " with the current period: " + currentPeriod, true);
                return; // Stop further processing
            }

            // Check if we already have time parameters stored
            String currentFrequency = stateData.has("timeFrequency") ? 
                stateData.get("timeFrequency").asText() : "15m"; // Default
            
            // Format the frequency to uppercase
            // String formattedFrequency = frequency.toUpperCase();
            
            // Check if frequency was adjusted
            boolean frequencyAdjusted = !validatedFrequency.equals(frequency);
            boolean needsUpdate = !validatedFrequency.equals(currentFrequency);

            // Only update if the frequency has changed and is valid
            if (needsUpdate) {
                // Acknowledge the callback to remove loading spinner
                String message = "Updating chart with frequency: " + validatedFrequency;
                if (frequencyAdjusted) {
                    message += " (adjusted for compatibility with time period " + currentPeriod + ")";
                }
                MessageUtils.sendAnswerCallbackQuery(update, message, false);
                
                stateData.put("timeFrequency", validatedFrequency);
                stateService.setStateData(userId, stateData);
                
                // Delete the previous message using the callback query context
                if (update.hasCallbackQuery()) {
                    Long chatId = update.getCallbackQuery().getMessage().getChatId();
                    Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
                    MessageUtils.deleteMessage(chatId, messageId); // Delete the previous message
                }
                
                // Show a loading message
                MessageUtils.sendMessage(update, message);
                
                // Re-execute TokenInfo to refresh the chart
                tokenInfo.executeVoid(update);
            } else {
                // No change needed, just acknowledge
                MessageUtils.sendAnswerCallbackQuery(update, "Already displaying " + validatedFrequency + " frequency", true);
            }
        } else {
            MessageUtils.sendAnswerCallbackQuery(update, "No token information available", true);
        }
    }
} 