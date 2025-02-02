package com.lunax.telegramBot.utils.message;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface ButtonHandler {
    String getButtonText();
    String getCallbackData();
    void handleCallback(Update update);
}
