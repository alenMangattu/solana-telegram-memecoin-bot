package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.token.TokenHolder;
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
    private StateService stateService;

    @Autowired
    private TokenHolder tokenHolder; // Inject TokenHolder

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
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        // Call the TokenHolder to handle the logic
        tokenHolder.executeVoid(update);
    }
} 