package com.lunax.telegramBot.handlers;

import com.lunax.telegramBot.utils.message.ButtonHandler;
import org.telegram.telegrambots.meta.api.objects.Update;

public class Button1Handler implements ButtonHandler {
    private final String buttonText;
    private final String callbackData;

    public Button1Handler(String buttonText) {
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
        // Access the update object directly
        System.out.println("Button 1 clicked with update: " + update);
        // Button 1 specific handling logic
    }
}
