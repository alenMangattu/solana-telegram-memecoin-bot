package com.lunax.telegramBot.component.Buttons.HomePageButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class RefreshHome implements ButtonHandler {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private final String buttonText;
    private final String callbackData;

    public RefreshHome(@Value("⟳ Refresh") String buttonText,
            @Value("refresh") String callbackData) {
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
        eventPublisher.publishEvent(new NavigationEvent(this, update, "HOME"));
    }
}