package com.lunax.telegramBot.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import com.lunax.telegramBot.component.Buttons.Token.TopHoldersButton;
import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.components.trading.ViewLimitOrders;
import com.lunax.telegramBot.service.TradeMemeCoinService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.repository.CopyTradingRepository;
import com.lunax.telegramBot.component.navigation.components.trading.CopyTradingHandler;
import com.lunax.telegramBot.event.CallbackQueryEvent;

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
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CopyTradingRepository copyTradingRepository;
    
    @Autowired
    private CopyTradingHandler copyTradingHandler;
    
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
            destination.startsWith("delete_copy_config_") ||
            destination.startsWith("copy_trading_page_")) {
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
    private void handleDynamicCopyTradingCallback(String destination, Update update) {
        // Handle copy trading page navigation
        if (destination.startsWith("copy_trading_page_")) {
            String[] parts = destination.split("_");
            if (parts.length >= 4) {
                try {
                    int page = Integer.parseInt(parts[3]);
                    copyTradingHandler.handleCopyTradingPage(update, page);
                    return;
                } catch (NumberFormatException e) {
                    log.error("Error parsing page number from destination: {}", destination, e);
                }
            }
        }
        
        if (destination.startsWith("toggle_copy_config_")) {
            String[] parts = destination.split("_");
            Long configId = Long.parseLong(parts[3]);
            boolean newState = Boolean.parseBoolean(parts[4]);
            tradeMemeCoinService.handleToggleCopyConfig(update, configId, newState);
        } else if (destination.startsWith("delete_copy_config_")) {
            String[] parts = destination.split("_");
            Long configId = Long.parseLong(parts[3]);
            tradeMemeCoinService.handleDeleteCopyConfig(update, configId);
        }
    }

    @EventListener
    public void handleCallbackQuery(CallbackQueryEvent event) {
        Update update = event.getUpdate();
        String callbackData = update.getCallbackQuery().getData();
        log.debug("Received callback data: {}", callbackData);

        // Handle multiplier selection for copy trading
        if (callbackData.startsWith("copy_trade_multiplier_")) {
            String multiplierStr = callbackData.split("_")[3];
            copyTradingHandler.handleMultiplierSelection(update, multiplierStr);
            return;
        }
        
        // Handle dynamic copy trading callbacks
        if (callbackData.startsWith("toggle_copy_config_") || 
            callbackData.startsWith("delete_copy_config_") ||
            callbackData.startsWith("copy_trading_page_")) {
            handleDynamicCopyTradingCallback(callbackData, update);
            return;
        }
        
        // ... existing code to handle other callbacks ...
    }
    
    /**
     * Handle dynamic copy trading callbacks with security checks
     */
    private void handleCopyTradingCallback(String callbackData, Update update) {
        // Get user ID from callback
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Security check: Verify the user is authorized
        User user = userService.findByTelegramId(telegramId).orElse(null);
        if (user == null) {
            log.warn("Unauthorized copy trading callback attempt from unknown user ID: {}", telegramId);
            MessageUtils.sendAnswerCallbackQuery(update, "Unauthorized action", true);
            return;
        }
        
        if (callbackData.startsWith("toggle_copy_config_")) {
            // Extract the config ID and new state
            String[] parts = callbackData.split("_");
            if (parts.length >= 4) {
                try {
                    Long configId = Long.parseLong(parts[3]);
                    boolean newState = Boolean.parseBoolean(parts[4]);
                    
                    // Security check: Verify the config belongs to this user
                    if (isOwnedByUser(configId, user.getId())) {
                        copyTradingHandler.handleToggleCopyConfig(update, configId, newState);
                    } else {
                        log.warn("User {} attempted to toggle config {} which they don't own", telegramId, configId);
                        MessageUtils.sendAnswerCallbackQuery(update, "You do not own this configuration", true);
                    }
                } catch (Exception e) {
                    log.error("Error parsing toggle copy config callback: {}", e.getMessage());
                }
            }
        } else if (callbackData.startsWith("delete_copy_config_")) {
            // Extract the config ID
            String[] parts = callbackData.split("_");
            if (parts.length >= 3) {
                try {
                    Long configId = Long.parseLong(parts[3]);
                    
                    // Security check: Verify the config belongs to this user
                    if (isOwnedByUser(configId, user.getId())) {
                        copyTradingHandler.handleDeleteCopyConfig(update, configId);
                    } else {
                        log.warn("User {} attempted to delete config {} which they don't own", telegramId, configId);
                        MessageUtils.sendAnswerCallbackQuery(update, "You do not own this configuration", true);
                    }
                } catch (Exception e) {
                    log.error("Error parsing delete copy config callback: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Security helper: Check if a copy trading config is owned by a user
     */
    private boolean isOwnedByUser(Long configId, Long userId) {
        // Add this method to your CopyTradingRepository
        return copyTradingRepository.existsByIdAndUserId(configId, userId);
    }
} 