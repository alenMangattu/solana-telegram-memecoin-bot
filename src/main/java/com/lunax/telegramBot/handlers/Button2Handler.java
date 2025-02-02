package com.lunax.telegramBot.handlers;

import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.message.ButtonHandler;

public class Button2Handler implements ButtonHandler {
    private final String buttonText;
    private final String callbackData;

    public Button2Handler(String buttonText) {
        this.buttonText = buttonText;
        // Generate callback data using class name and text to make it unique
        this.callbackData = this.getClass().getSimpleName() + "_" + buttonText.replaceAll("\\s+", "_").toLowerCase();
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
    public void handleCallback(Update callbackData) {
        // Button 2 specific handling logic
    }
}
