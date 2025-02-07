package com.lunax.telegramBot.component.handler.SettingsButtons;

import org.apache.logging.log4j.core.tools.picocli.CommandLine.Command;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class NotificationHub implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    public NotificationHub(@Value("🔔 Notification Hub") String buttonText, 
                      @Value("notification_hub") String callbackData) {
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
