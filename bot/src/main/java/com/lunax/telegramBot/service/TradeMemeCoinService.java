package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TradeMemeCoinService {

    private TradeMemeCoin tradeMemeCoin;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public void executeTradeMemeCoin(Update update) {
        getTradeMemeCoin().executeVoid(update);
    }
    
    public void executeTradeMemeCoinWithPage(Update update, int page) {
        getTradeMemeCoin().executeVoid(update, page);
    }
    
    public void handleToggleCopyConfig(Update update, Long configId, boolean newEnabledState) {
        getTradeMemeCoin().handleToggleCopyConfig(update, configId, newEnabledState);
    }
    
    public void handleDeleteCopyConfig(Update update, Long configId) {
        getTradeMemeCoin().handleDeleteCopyConfig(update, configId);
    }
    
    public void handleCopyTradingButton(Update update) {
        getTradeMemeCoin().handleCopyTradingButton(update);
    }
    
    public void handleAddCopyTradingConfig(Update update) {
        getTradeMemeCoin().handleAddCopyTradingConfig(update);
    }
    
    private TradeMemeCoin getTradeMemeCoin() {
        if (tradeMemeCoin == null) {
            tradeMemeCoin = applicationContext.getBean(TradeMemeCoin.class);
        }
        return tradeMemeCoin;
    }
} 