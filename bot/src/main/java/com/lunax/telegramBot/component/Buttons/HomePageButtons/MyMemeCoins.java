package com.lunax.telegramBot.component.Buttons.HomePageButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.mymemecoin.MemecoinList;
import com.lunax.telegramBot.utils.components.ButtonHandler;

@Component
public class MyMemeCoins implements ButtonHandler {

    
    @Autowired
    private MemecoinList memecoinList;
    
    private final String buttonText;
    private final String callbackData;

    public MyMemeCoins(@Value("💰 My Meme Coins") String buttonText, 
                           @Value("my_meme_coins") String callbackData) {
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
        memecoinList.executeVoid(update);
    }
}
