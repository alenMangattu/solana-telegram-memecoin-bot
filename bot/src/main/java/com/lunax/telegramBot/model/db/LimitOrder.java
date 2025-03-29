/**
 * COMING SOON: Limit Order Feature
 * This feature is currently under development and not yet available.
 * Orders can be created but will remain in the database until the feature is fully implemented.
 */
package com.lunax.telegramBot.model.db;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "limit_orders")
@Data
@NoArgsConstructor
public class LimitOrder {
    
    @Id
    @Column(name = "order_id")
    private String orderId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;
    
    @Column(name = "token_mint", nullable = false)
    private String tokenMint;
    
    @Column(name = "sol_amount", nullable = false, precision = 19, scale = 9)
    private BigDecimal solAmount;
    
    @Column(name = "limit_price", nullable = false, precision = 19, scale = 9)
    private BigDecimal limitPrice;
    
    @Column(name = "slippage", nullable = false, precision = 5, scale = 2)
    private BigDecimal slippage;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "created_at")
    private Timestamp createdAt;
    
    @Column(name = "updated_at")
    private Timestamp updatedAt;
    
    @Column(name = "executed_at")
    private Timestamp executedAt;
    
    @Column(name = "transaction_signature")
    private String transactionSignature;
    
    @Column(name = "action", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderAction action = OrderAction.BUY;
} 