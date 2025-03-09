package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.LimitOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimitOrderRepository extends JpaRepository<LimitOrder, String> {
    
    List<LimitOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<LimitOrder> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    List<LimitOrder> findByStatus(String status);
} 