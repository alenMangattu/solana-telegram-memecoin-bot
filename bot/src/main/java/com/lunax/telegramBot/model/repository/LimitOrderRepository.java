package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.LimitOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LimitOrderRepository extends JpaRepository<LimitOrder, String> {
    
    List<LimitOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    List<LimitOrder> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    
    List<LimitOrder> findByStatus(String status);

    @Query("SELECT lo FROM LimitOrder lo WHERE lo.userId = :userId AND lo.status IN ('active', 'pending') ORDER BY lo.createdAt DESC")
    List<LimitOrder> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId);
} 