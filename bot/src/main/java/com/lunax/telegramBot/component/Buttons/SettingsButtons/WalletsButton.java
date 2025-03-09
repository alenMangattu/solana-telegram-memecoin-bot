package com.lunax.telegramBot.component.Buttons.SettingsButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.settings.wallet.Wallets;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class WalletsButton implements ButtonHandler {


    private final String buttonText;
    private final String callbackData;

    @Autowired
    private Wallets wallets;

    public WalletsButton(@Value("💸 Wallets") String buttonText,
            @Value("wallets") String callbackData) {
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
        wallets.executeVoid(update);
    }

}
