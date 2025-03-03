package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;

@Component
public class SellButton implements ButtonHandler {

    @Override
    public String getButtonText() {
        return "💰 Sell";
    }

    @Override
    public String getCallbackData() {
        return "sell_token";
    }

    @Override
    public ChatState getNextState() {
        return ChatState.TOKEN_INFO_PAGE;
    }

    @Override
    public void handleCallback(Update update) {
        // Implementation can be added later
        MessageUtils.sendMessage(update, "Sell functionality will be implemented soon!");
    }
} 