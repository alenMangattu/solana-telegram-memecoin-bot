package com.lunax.telegramBot.component.handler.HomePageButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.settings.SettingsImpl;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class Settings implements ButtonHandler {
   
    private final String buttonText;
    private final String callbackData;

    @Autowired
    SettingsImpl settingsImpl;

    public Settings(@Value("⚙️ Settings") String buttonText, 
                           @Value("settings") String callbackData) {
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
        MessageUtils.editMessage(update, settingsImpl.getText(), settingsImpl.getButtonHandlers());
    } 
}
