package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.component.navigation.components.token.Buy;
import com.lunax.telegramBot.model.session.ChatState;

@Component
public class BuyButton implements ButtonHandler {

    @Autowired
    private Buy buy;

    @Override
    public String getButtonText() {
        return "🛒 Buy";
    }

    @Override
    public String getCallbackData() {
        return "buy_token";
    }

    @Override
    public ChatState getNextState() {
        return ChatState.TOKEN_INFO_PAGE;
    }

    @Override
    public void handleCallback(Update update) {
        buy.executeVoid(update);
    }
}