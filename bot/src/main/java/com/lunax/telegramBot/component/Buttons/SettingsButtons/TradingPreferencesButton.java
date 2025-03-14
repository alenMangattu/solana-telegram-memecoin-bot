package com.lunax.telegramBot.component.Buttons.SettingsButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.settings.TraidingPerferences;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class TradingPreferencesButton implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;
    
    @Autowired
    private TraidingPerferences tradingPreferences;
    
    public TradingPreferencesButton(@Value("⚙️ Trading Preferences") String buttonText,
                                  @Value("trading_preferences") String callbackData) {
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
        tradingPreferences.executeVoid(update);
    }
} 