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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.lunax.telegramBot.model.db.OrderAction;

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
     * @param userId The Telegram ID of the user (not the database ID)
     */
    @Transactional
    public LimitOrder createLimitOrder(
        Long userId, // This is the Telegram ID, not the database ID
        UUID walletId,
        String tokenMint,
        BigDecimal solAmount,
        BigDecimal limitPrice,
        BigDecimal slippage,
        OrderAction action
    ) {
        try {
            // Create a new limit order
            LimitOrder order = new LimitOrder();
            String orderId = "order_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
            order.setOrderId(orderId);
            order.setUserId(userId);
            order.setWalletId(walletId);
            order.setTokenMint(tokenMint);
            order.setSolAmount(solAmount);
            order.setLimitPrice(limitPrice);
            order.setSlippage(slippage);
            order.setStatus("pending");
            order.setAction(action != null ? action : OrderAction.BUY);
            order.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            
            // Save using repository
            return limitOrderRepository.save(order);
        } catch (Exception e) {
            log.error("Error creating limit order: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Also add a convenience overload that defaults to BUY
     */
    @Transactional
    public LimitOrder createLimitOrder(
        Long userId,
        UUID walletId,
        String tokenMint,
        BigDecimal solAmount,
        BigDecimal limitPrice,
        BigDecimal slippage
    ) {
        return createLimitOrder(userId, walletId, tokenMint, solAmount, limitPrice, slippage, OrderAction.BUY);
    }
    
    /**
     * Retrieve a limit order by its ID
     */
    public Optional<LimitOrder> getLimitOrder(String orderId) {
        Optional<LimitOrder> orderOpt = limitOrderRepository.findById(orderId);
        log.info("Fetched limit order with ID {}: {}", orderId, orderOpt);
        return orderOpt;
    }
    
    /**
     * Get all active and pending limit orders for a user
     */
    public List<LimitOrder> getActiveOrdersForUser(Long userId) {
        List<LimitOrder> orders = limitOrderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(
            userId
            // Arrays.asList("pending", "active")
        );
        
        log.info("Fetched {} active and pending orders for user {}: {}", orders.size(), userId, orders);
        return orders;
    }
    
    /**
     * Cancel a limit order
     */
    public boolean cancelOrder(String orderId, Long telegramId) {
        Optional<LimitOrder> orderOpt = limitOrderRepository.findById(orderId);
        
        if (orderOpt.isPresent() && orderOpt.get().getUserId().equals(telegramId)) {
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
                // COMMENTING OUT EXTERNAL API CALLS
                // For demo purposes, we'll just mark the order as executed if it's active
                log.info("Found active limit order {}: marking as executed for demo purposes", 
                         order.getOrderId());
                
                order.setStatus("executed");
                order.setExecutedAt(Timestamp.valueOf(LocalDateTime.now()));
                order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                limitOrderRepository.save(order);
                
                log.info("Limit order {} marked as executed", order.getOrderId());
                
                /* Original code commented out
                double currentPrice = solanaUtils.getTokenPrice(order.getTokenMint());
                
                // Check if price condition is met
                if (currentPrice <= order.getLimitPrice().doubleValue()) {
                    log.info("Executing limit order {}: current price {} <= limit price {}", 
                             order.getOrderId(), currentPrice, order.getLimitPrice());
                    
                    // Execute the order
                    executeOrder(order);
                }
                */
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
            // Execute the swap logic here (mocked for now)
            order.setStatus("executed"); // Change status to executed
            order.setTransactionSignature("mock_signature_" + System.currentTimeMillis());
            order.setExecutedAt(Timestamp.valueOf(LocalDateTime.now()));
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            limitOrderRepository.save(order);
            
            log.info("Limit order {} marked as executed with mock signature", order.getOrderId());
        } catch (Exception e) {
            log.error("Error executing limit order {}: {}", order.getOrderId(), e.getMessage());
            
            // Update order status to failed
            order.setStatus("failed");
            order.setUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
            limitOrderRepository.save(order);
        }
    }

    /**
     * Get all limit orders for a user by user ID and status
     */
    public List<LimitOrder> getLimitOrdersByUserIdAndIsActiveOrPending(Long userId) {
        // List<String> statuses = Arrays.asList("active", "pending");
        
        // // Log the SQL query
        // String sqlQuery = String.format(
        //     "SELECT * FROM limit_orders lo WHERE lo.user_id = %d AND lo.status IN (%s) ORDER BY lo.created_at DESC",
        //     userId,
        //     statuses.stream().map(status -> "'" + status + "'").collect(Collectors.joining(", "))
        // );
        
        // log.info("Executing query: {}", sqlQuery);
        
        List<LimitOrder> orders = limitOrderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId);
        log.info("Fetched {} limit orders for user {}: {}", orders.size(), userId, orders);
        return orders;
    }

    /**
     * Get all limit orders for a user by telegram ID and status
     */
    public List<LimitOrder> getLimitOrdersByTelegramId(Long telegramId) {
        String sqlQuery = String.format(
            "SELECT * FROM limit_orders lo WHERE lo.user_id = %d AND lo.status IN ('active', 'pending') ORDER BY lo.created_at DESC", 
            telegramId
        );
        
        log.info("Executing query with Telegram ID: {}", sqlQuery);
        
        // Execute the SQL query directly
        List<LimitOrder> orders = jdbcTemplate.query(sqlQuery, (rs, rowNum) -> {
            LimitOrder order = new LimitOrder();
            order.setOrderId(rs.getString("order_id"));
            order.setUserId(rs.getLong("user_id"));
            order.setWalletId(UUID.fromString(rs.getString("wallet_id")));
            order.setTokenMint(rs.getString("token_mint"));
            order.setSolAmount(rs.getBigDecimal("sol_amount"));
            order.setLimitPrice(rs.getBigDecimal("limit_price"));
            order.setSlippage(rs.getBigDecimal("slippage"));
            order.setStatus(rs.getString("status"));
            order.setCreatedAt(rs.getTimestamp("created_at"));
            order.setUpdatedAt(rs.getTimestamp("updated_at"));
            order.setExecutedAt(rs.getTimestamp("executed_at"));
            order.setTransactionSignature(rs.getString("transaction_signature"));
            order.setAction(OrderAction.valueOf(rs.getString("action")));
            return order;
        });
        
        log.info("Fetched {} limit orders for telegramId {}: {}", orders.size(), telegramId, orders);
        return orders;
    }
} 