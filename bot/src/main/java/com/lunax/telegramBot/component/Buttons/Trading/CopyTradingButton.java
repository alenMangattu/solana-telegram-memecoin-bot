package com.lunax.telegramBot.component.Buttons.Trading;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CopyTradingButton implements ButtonHandler {

    @Autowired
    private TradeMemeCoin tradeMemeCoin;

    @Override
    public String getButtonText() {
        return "Copy Trading Page";
    }

    @Override
    public String getCallbackData() {
        return "copy_trading_page";
    }

    @Override
    public ChatState getNextState() {
        return ChatState.COPY_TRADING_PAGE;
    }

    @Override
    public void handleCallback(Update update) {
        log.debug("Copy Trading button clicked");
        tradeMemeCoin.handleCopyTradingButton(update);
    }
} 