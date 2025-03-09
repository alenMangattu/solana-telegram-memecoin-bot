package com.lunax.telegramBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lunax.telegramBot.model.db.LimitOrder;
import com.lunax.telegramBot.model.repository.LimitOrderRepository;
import com.lunax.telegramBot.utils.SolanaUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TransactionSendingService {

    @Autowired
    private LimitOrderRepository limitOrderRepository;
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Creates a new limit order in the database
     */
    public LimitOrder createLimitOrder(
        Long userId,
        UUID walletId,
        String tokenMint,
        BigDecimal solAmount,
        BigDecimal limitPrice,
        BigDecimal slippage
    ) {
        String orderId = "order_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        
        LimitOrder order = new LimitOrder();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setWalletId(walletId);
        order.setTokenMint(tokenMint);
        order.setSolAmount(solAmount);
        order.setLimitPrice(limitPrice);
        order.setSlippage(slippage);
        order.setStatus("active");
        order.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        
        return limitOrderRepository.save(order);
    }
    
    /**
     * Retrieve a limit order by its ID
     */
    public Optional<LimitOrder> getLimitOrder(String orderId) {
        return limitOrderRepository.findById(orderId);
    }
    
    /**
     * Get all active limit orders for a user
     */
    public List<LimitOrder> getActiveOrdersForUser(Long userId) {
        return limitOrderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "active");
    }
    
    /**
     * Cancel a limit order
     */
    public boolean cancelOrder(String orderId, Long userId) {
        Optional<LimitOrder> orderOpt = limitOrderRepository.findById(orderId);
        
        if (orderOpt.isPresent() && orderOpt.get().getUserId().equals(userId)) {
            LimitOrder order = orderOpt.get();
            order.setStatus("cancelled");
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            limitOrderRepository.save(order);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check and execute limit orders that have met their price conditions
     * This would run periodically in a production system
     */
    @Scheduled(fixedRate = 60000) // Run every minute
    public void checkAndExecuteLimitOrders() {
        log.info("Checking for limit orders to execute...");
        
        // Get all active limit orders
        List<LimitOrder> activeOrders = limitOrderRepository.findByStatus("active");
        
        if (activeOrders.isEmpty()) {
            return;
        }
        
        log.info("Found {} active limit orders", activeOrders.size());
        
        // Get current market prices for all tokens in the orders
        // This would typically call an API or use the SolanaUtils class
        
        for (LimitOrder order : activeOrders) {
            try {
                // For demo purposes only - in a real app you would check actual market prices
                double currentPrice = solanaUtils.getTokenPrice(order.getTokenMint());
                
                // Check if price condition is met
                if (currentPrice <= order.getLimitPrice().doubleValue()) {
                    log.info("Executing limit order {}: current price {} <= limit price {}", 
                             order.getOrderId(), currentPrice, order.getLimitPrice());
                    
                    // Execute the order
                    executeOrder(order);
                }
            } catch (Exception e) {
                log.error("Error checking limit order {}: {}", order.getOrderId(), e.getMessage(), e);
            }
        }
    }
    
    /**
     * Execute a limit order that has met its price condition
     */
    private void executeOrder(LimitOrder order) {
        try {
            // Get wallet private key - in a real app this would use secure key retrieval
            String walletPrivateKey = "dummy_key"; // Replace with actual key retrieval
            
            // Execute the swap
            SolanaUtils.SwapResponse swapResult = solanaUtils.buyToken(
                order.getTokenMint(),
                order.getSolAmount().doubleValue(),
                order.getSlippage().doubleValue(),
                walletPrivateKey,
                SolanaUtils.TransactionPriority.MID
            );
            
            // Update order status
            if (swapResult.isSuccess()) {
                order.setStatus("executed");
                order.setTransactionSignature(swapResult.getSignature());
                order.setExecutedAt(Timestamp.valueOf(LocalDateTime.now()));
            } else {
                order.setStatus("failed");
            }
            
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            limitOrderRepository.save(order);
            
            log.info("Limit order {} executed with transaction signature: {}", 
                     order.getOrderId(), swapResult.getSignature());
        } catch (Exception e) {
            log.error("Error executing limit order {}: {}", order.getOrderId(), e.getMessage(), e);
            
            // Update order status to failed
            order.setStatus("failed");
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            limitOrderRepository.save(order);
        }
    }
} 