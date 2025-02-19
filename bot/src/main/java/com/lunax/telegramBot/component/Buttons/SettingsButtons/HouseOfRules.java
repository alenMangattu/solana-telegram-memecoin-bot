package com.lunax.telegramBot.component.Buttons.SettingsButtons;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class HouseOfRules implements ButtonHandler{

    private final String buttonText;
    private final String callbackData;

    public HouseOfRules(@Value("📜 House of Rules") String buttonText, 
                      @Value("house_of_rules") String callbackData) {
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
        throw new UnsupportedOperationException("Unimplemented method 'handleCallback'");
    }
    
}
