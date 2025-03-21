package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import com.lunax.telegramBot.service.TradeMemeCoinService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.model.session.ChatState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("tokenTradeMemeCoinButton")
public class TradeMemeCoinButton implements ButtonHandler {

    // Remove direct dependency to TradeMemeCoin
    // @Autowired
    // private TradeMemeCoin tradeMemeCoin;
    
    // Use ApplicationContext instead
    @Autowired
    private ApplicationContext applicationContext;
    
    private TradeMemeCoin getTradeMemeCoin() {
        return applicationContext.getBean(TradeMemeCoin.class);
    }

    @Override
    public String getButtonText() {
        return "Trade This Token";
    }

    @Override
    public String getCallbackData() {
        return "trade_this_token";
    }

    @Override
    public ChatState getNextState() {
        return ChatState.TRADE_MEMECOIN_START;
    }

    @Override
    public void handleCallback(Update update) {
        log.info("Trade token button clicked");
        getTradeMemeCoin().executeVoid(update);
    }
} 