package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.component.navigation.components.token.TokenHolder;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TopHoldersButton implements ButtonHandler {

    // @Autowired
    // private StateService stateService;

    @Autowired
    private TokenHolder tokenHolder; // Inject TokenHolder

    @Override
    public String getButtonText() {
        return "Top Holders";
    }

    @Override
    public String getCallbackData() {
        return "token_top_holders";
    }

    @Override
    public void handleCallback(Update update) {
        
        // Call the TokenHolder to handle the logic
        tokenHolder.executeVoid(update);
    }
} 