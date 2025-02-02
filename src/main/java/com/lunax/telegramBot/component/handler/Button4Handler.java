package com.lunax.telegramBot.component.handler;

import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.ButtonHandler;

public class Button4Handler implements ButtonHandler {
    private final String buttonText;
    private final String callbackData;

    public Button4Handler(String buttonText) {
        this.buttonText = buttonText;
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
    public void handleCallback(Update update) {
        System.out.println("Button 4 clicked with update: " + update);
        // Button 4 specific handling logic
    }
}
