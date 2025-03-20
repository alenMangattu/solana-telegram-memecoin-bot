package com.lunax.telegramBot.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.Buttons.Token.TopHoldersButton;
import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.components.trading.ViewLimitOrders;
import com.lunax.telegramBot.service.TradeMemeCoinService;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NavigationEventListener {

    @Autowired
    private StateService stateService;
    
    @Autowired
    private TradeMemeCoinService tradeMemeCoinService;
    
    @Autowired
    private ViewLimitOrders viewLimitOrders;
    
    // You would add other handlers here as needed
    
    @EventListener
    public void handleNavigationEvent(NavigationEvent event) {
        log.info("Navigation event received: {}", event.getDestination());
        
        String destination = event.getDestination();
        Long userId = event.getUpdate().getCallbackQuery().getFrom().getId();
        Long chatId = event.getUpdate().getCallbackQuery().getMessage().getChatId();
        Integer messageId = event.getUpdate().getCallbackQuery().getMessage().getMessageId();
        
        // Handle trade page navigation events
        if (destination.startsWith("trade_page_")) {
            try {
                int page = Integer.parseInt(destination.split("_")[2]);
                tradeMemeCoinService.executeTradeMemeCoinWithPage(event.getUpdate(), page);
                return;
            } catch (NumberFormatException e) {
                log.error("Error parsing page number from destination: {}", destination, e);
            }
        }
        
        // Handle copy trading dynamic callbacks
        if (destination.startsWith("toggle_copy_config_") || 
            destination.startsWith("delete_copy_config_")) {
            handleDynamicCopyTradingCallback(destination, event.getUpdate());
            return;
        }
        
        switch (destination) {
            case "token_info":
                ObjectNode stateData = stateService.getStateData(userId);
                
                if (stateData != null && stateData.has("tokenInfoMessage") && stateData.has("tokenAddress")) {
                    String tokenInfoMessage = stateData.get("tokenInfoMessage").asText();
                    
                    // Create keyboard with Top Holders button
                    ButtonHandler[][] keyboard = {
                        { new TopHoldersButton() }
                    };
                    
                    // Edit the message to show token info with the button
                    MessageUtils.editMessageText(chatId, messageId, tokenInfoMessage, keyboard);
                } else {
                    MessageUtils.editMessageText(chatId, messageId, "Token information not available. Please search for a token again.", null);
                }
                break;
                
            case "token_holders":
                // This is already handled in TopHoldersButton.java
                break;
                
            case "trade_meme_coin":
                // Handle trade meme coin navigation
                tradeMemeCoinService.executeTradeMemeCoin(event.getUpdate());
                break;
                
            case "view_limit_orders":
                // Handle view limit orders navigation
                viewLimitOrders.executeVoid(event.getUpdate());
                break;
                
            case "copy_trading_page":
                tradeMemeCoinService.executeTradeMemeCoin(event.getUpdate());
                break;
            
            case "add_copy_trading_config":
                tradeMemeCoinService.executeTradeMemeCoin(event.getUpdate());
                break;
                
            // case "delete_limit_order":
            //     // For delete limit order, we'll use the same component as view, as it includes delete functionality
            //     viewLimitOrders.executeVoid(event.getUpdate());
            //     break;
                
            default:
                log.warn("Unknown navigation destination: {}", destination);
        }
    }

    /**
     * Handle dynamic copy trading callbacks
     */
    private void handleDynamicCopyTradingCallback(String callbackData, Update update) {
        if (callbackData.startsWith("toggle_copy_config_")) {
            String[] parts = callbackData.split("_");
            Long configId = Long.parseLong(parts[3]);
            boolean newState = Boolean.parseBoolean(parts[4]);
            tradeMemeCoinService.handleToggleCopyConfig(update, configId, newState);
        } else if (callbackData.startsWith("delete_copy_config_")) {
            String[] parts = callbackData.split("_");
            Long configId = Long.parseLong(parts[3]);
            tradeMemeCoinService.handleDeleteCopyConfig(update, configId);
        }
    }
} 