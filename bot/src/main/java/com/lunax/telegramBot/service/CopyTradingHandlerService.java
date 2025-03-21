package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CopyTradingHandlerService {

    private TradeMemeCoin tradeMemeCoin;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * Handle toggle copy config action
     */
    public void handleToggleCopyConfig(Update update, Long configId, boolean newEnabledState) {
        getTradeMemeCoin().handleToggleCopyConfig(update, configId, newEnabledState);
    }
    
    /**
     * Handle delete copy config action
     */
    public void handleDeleteCopyConfig(Update update, Long configId) {
        getTradeMemeCoin().handleDeleteCopyConfig(update, configId);
    }
    
    /**
     * Handle copy trading button
     */
    public void handleCopyTradingButton(Update update) {
        getTradeMemeCoin().handleCopyTradingButton(update);
    }
    
    /**
     * Handle add copy trading config
     */
    public void handleAddCopyTradingConfig(Update update) {
        getTradeMemeCoin().handleAddCopyTradingConfig(update);
    }
    
    /**
     * Handle multiplier selection
     */
    public void handleMultiplierSelection(Update update, String multiplierStr) {
        getTradeMemeCoin().getCopyTradingHandler().handleMultiplierSelection(update, multiplierStr);
    }
    
    /**
     * Get the TradeMemeCoin instance lazily to avoid circular dependency
     */
    private TradeMemeCoin getTradeMemeCoin() {
        if (tradeMemeCoin == null) {
            tradeMemeCoin = applicationContext.getBean(TradeMemeCoin.class);
        }
        return tradeMemeCoin;
    }
} 