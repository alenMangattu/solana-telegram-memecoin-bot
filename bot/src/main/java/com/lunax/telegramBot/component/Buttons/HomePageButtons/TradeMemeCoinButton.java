package com.lunax.telegramBot.component.Buttons.HomePageButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TradeMemeCoinButton implements ButtonHandler {
    
    private final String buttonText;
    private final String callbackData;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public TradeMemeCoinButton(@Value("💱 Trade Meme Coin") String buttonText, 
                         @Value("trade_meme_coin") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
    }
    
    @Override
    public void handleCallback(Update update) {
        log.info("Trade Meme Coin button clicked");
        // Fire navigation event
        eventPublisher.publishEvent(new NavigationEvent(this, update, "trade_meme_coin"));
    }

    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }
}
