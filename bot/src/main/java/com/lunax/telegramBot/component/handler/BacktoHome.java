package com.lunax.telegramBot.component.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BacktoHome implements ButtonHandler {

    @Autowired
    private StateService stateService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Override
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        // Set state to IDLE to trigger home page
        stateService.setState(telegramId, ChatState.IDLE);
        // Clear any existing state data
        stateService.clearStateData(telegramId);

        eventPublisher.publishEvent(new NavigationEvent(this, update, "HOME"));
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
