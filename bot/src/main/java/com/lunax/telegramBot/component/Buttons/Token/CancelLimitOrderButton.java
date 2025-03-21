package com.lunax.telegramBot.component.Buttons.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.trading.ViewLimitOrders;
import com.lunax.telegramBot.model.db.LimitOrder;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.TransactionSendingService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
@Component
public class CancelLimitOrderButton implements ButtonHandler {
    
    @Autowired
    private TransactionSendingService transactionSendingService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private StateService stateService;
    
    @Autowired
    private ViewLimitOrders viewLimitOrders;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getButtonText() {
        return "Cancel Order"; 
    }

    @Override
    public String getCallbackData() {
        return "cancel_order_"; 
    }

    @Override
    public void handleCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        log.debug("Received cancel order callback: {}", callbackData);
        
        if (callbackData.startsWith("cancel_order_")) {
            // Extract the order ID from the callback data
            String orderId = callbackData.substring("cancel_order_".length());
            
            // Get user
            Long telegramId = update.getCallbackQuery().getFrom().getId();
            User user = userService.findByTelegramId(telegramId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Get the order first to show information about what's being canceled
            Optional<LimitOrder> orderOpt = transactionSendingService.getLimitOrder(orderId);
            
            if (orderOpt.isPresent() && orderOpt.get().getUserId().equals(user.getTelegramId())) {
                LimitOrder order = orderOpt.get();
                
                // Try to cancel the order
                boolean success = transactionSendingService.cancelOrder(orderId, user.getTelegramId());
                
                if (success) {
                    String successMessage = String.format("""
                            ✅ *Limit Order Successfully Canceled*
                            
                            Your %s order for %.4f SOL at $%.9f has been canceled.
                            
                            _Returning to your active orders..._
                            """, 
                            order.getAction(),
                            order.getSolAmount().doubleValue(),
                            order.getLimitPrice().doubleValue());
                    
                    MessageUtils.sendMessage(update, successMessage);
                    
                    // Get the current page from state data and refresh the orders view after a short delay
                    ObjectNode stateData = stateService.getStateData(telegramId);
                    int currentPage = stateData != null && stateData.has("currentPage") ? 
                                      stateData.get("currentPage").asInt() : 0;
                    
                    // Wait briefly before refreshing the orders view (for better UX)
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    viewLimitOrders.executeVoid(update, currentPage);
                } else {
                    MessageUtils.sendMessage(update, "❌ Failed to cancel order. It may have already been executed or canceled.");
                }
            } else {
                MessageUtils.sendMessage(update, "❌ Order not found or you do not have permission to cancel it.");
            }
        }
    }
} 