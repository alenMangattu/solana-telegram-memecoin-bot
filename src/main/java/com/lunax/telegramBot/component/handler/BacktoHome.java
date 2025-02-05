package com.lunax.telegramBot.component.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class BacktoHome implements ButtonHandler {

    @Autowired
    private StateService stateService;

    @Override
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        // Set state to IDLE to trigger home page
        stateService.setState(telegramId, ChatState.IDLE);
        // Clear any existing state data
        stateService.clearStateData(telegramId);
    }

    @Override
    public String getButtonText() {
        return "Back to Home";
    }

    @Override
    public String getCallbackData() {
        return "back_to_home";
    }
}
