package com.lunax.telegramBot.component.navigation.components;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface StateHandler {
    default String execute(Update update) {
        return null;
    }

    default void executeVoid(Update update) {
        // Default implementation
    }
}
