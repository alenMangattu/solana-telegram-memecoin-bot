package com.lunax.telegramBot.component.navigation.states.settings;

import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.states.StateHandler;

public class backToHome implements StateHandler {
    @Override
    public String getMessage(Update update) {
        return "Back to Home";
    }
}
