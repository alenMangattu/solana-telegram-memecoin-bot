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
public class TimePeriodButton implements ButtonHandler {
    private final String period;
    private final String buttonText;
    private final String callbackData;

    @Autowired
    private StateService stateService;
    
    @Autowired
    @Lazy
    private TokenInfo tokenInfo;

    // Use constructor injection to create different instances
    public TimePeriodButton(String period, String buttonText) {
        // Ensure consistent case for the time periods
        if (period.endsWith("h") || period.endsWith("H")) {
            // For hours
            this.period = period.substring(0, period.length()-1) + "H";
        } else if (period.endsWith("d") || period.endsWith("D")) {
            // For days
            this.period = period.substring(0, period.length()-1) + "D";
        } else if (period.endsWith("m") || period.endsWith("M")) {
            // For months (not minutes)
            this.period = period.substring(0, period.length()-1) + "M";
        } else {
            // Default case
            this.period = period;
        }
        
        this.buttonText = buttonText;
        this.callbackData = "time_period_" + this.period;
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
            // Format the period to uppercase
            String formattedPeriod = period.toUpperCase(); // Ensure the period is in uppercase
            
            // Get the current frequency (if any)
            String currentFrequency = stateData.has("timeFrequency") ? 
                stateData.get("timeFrequency").asText() : "15M";
            
            // Validate and possibly adjust the frequency based on the new period
            String adjustedFrequency = TimeUtils.validateTimeFrequency(currentFrequency, formattedPeriod);
            
            // Check if frequency was adjusted
            boolean frequencyAdjusted = !adjustedFrequency.equals(currentFrequency);
            
            // Acknowledge the callback to remove the loading spinner
            String message = "Updating chart with time period: " + formattedPeriod;
            if (frequencyAdjusted) {
                message += " (frequency adjusted to " + adjustedFrequency + ")";
            }
            MessageUtils.sendAnswerCallbackQuery(update, message, false);
            
            // Update the time period and possibly the frequency
            stateData.put("timeBefore", formattedPeriod);
            if (frequencyAdjusted) {
                stateData.put("timeFrequency", adjustedFrequency);
            }
            stateService.setStateData(userId, stateData);
            
            // Delete the previous message using the callback query context
            if (update.hasCallbackQuery()) {
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
                MessageUtils.deleteMessage(chatId, messageId); // Delete the previous message
            }
            
            // Show a loading message
            MessageUtils.sendMessage(update, message);
            
            // Let TokenInfo handle the refreshing
            tokenInfo.executeVoid(update);
        } else {
            MessageUtils.sendAnswerCallbackQuery(update, "No token information available", true);
        }
    }
} 