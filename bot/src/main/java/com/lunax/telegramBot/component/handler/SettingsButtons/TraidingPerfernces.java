package com.lunax.telegramBot.component.handler.SettingsButtons;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class TraidingPerfernces implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    public TraidingPerfernces(@Value("📊 Traiding Perfernces") String buttonText, 
                      @Value("traiding_perfernces") String callbackData) {
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'handleCallback'");
    }

    
}
