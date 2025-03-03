package com.lunax.telegramBot.utils.components;

import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.model.session.ChatState;

public interface ButtonHandler {
    String getButtonText();
    String getCallbackData();
    void handleCallback(Update update);
    default void handleMessage(Update update) {}
    default ChatState getNextState() {
        return null;
    }
}
