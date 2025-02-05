package com.lunax.telegramBot.component.navigation.components;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface StateHandler {
    void executeVoid(Update update);
}
