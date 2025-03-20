package com.lunax.telegramBot.model.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lunax.telegramBot.model.db.CopyTrading;

@Repository
public interface CopyTradingRepository extends JpaRepository<CopyTrading, Long> {
    // Add any custom query methods here if needed


    // Find all copy trading records for a specific user
    List<CopyTrading> findByUserId(Integer userId);
    
    // Find a specific copy trading record by its ID
    Optional<CopyTrading> findById(Integer id);
    
} 