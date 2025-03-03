package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class ReturnToTokenInfoButton implements ButtonHandler {

    @Lazy  // Add @Lazy to prevent circular dependency
    @Autowired
    private TokenInfo tokenInfo;

    @Override
    public String getButtonText() {
        return "← Back to Token Info";
    }

    @Override
    public String getCallbackData() {
        return "return_to_token_info";
    }

    @Override
    public ChatState getNextState() {
        return ChatState.TOKEN_INFO_PAGE;
    }

    @Override
    public void handleCallback(Update update) {
        // Delete the current message before calling TokenInfo
        MessageUtils.deleteMessage(update);
        // Directly call TokenInfo without going through TokenHolder
        tokenInfo.executeVoid(update);
    }
} 