package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class TokenInfoButton implements ButtonHandler {

    @Autowired
    private TokenInfo tokenInfo;
    
    private final String buttonText;
    private final String callbackData;

    public TokenInfoButton(@Value("Token Info") String buttonText, 
                          @Value("token_info") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
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
    public void handleCallback(Update update) {
        tokenInfo.executeVoid(update);
    }
} 