package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.StateHandler;

@Service
public class NavigatorService {

    @Autowired
    private HandlerRegistry handlerRegistry;
    
    @Autowired
    private StateService stateService;

    public void navigate(Update update, ChatState nextState) {
        if (update == null || nextState == null) {
            return;
        }
        
        Long telegramId = null;
        if (update.hasMessage()) {
            telegramId = update.getMessage().getFrom().getId();
        } else if (update.hasCallbackQuery()) {
            telegramId = update.getCallbackQuery().getFrom().getId();
        }
        
        if (telegramId == null) {
            return;
        }
        
        // Set the new state
        stateService.setState(telegramId, nextState);
        
        // Get the handler for this state - convert ChatState to String since HandlerRegistry expects a String
        StateHandler handler = handlerRegistry.getStateHandler(nextState.toString());
        if (handler != null) {
            handler.executeVoid(update);
        }
    }
} 