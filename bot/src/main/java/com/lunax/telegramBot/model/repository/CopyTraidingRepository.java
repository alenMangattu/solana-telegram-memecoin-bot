package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.CopyTrading; // Assuming you have a CopyTrading entity
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface CopyTraidingRepository extends JpaRepository<CopyTrading, Integer> {
    
    // Find all copy trading records for a specific user
    List<CopyTrading> findByUserId(Integer userId);
    
    // Find a specific copy trading record by its ID
    Optional<CopyTrading> findById(Integer id);
    
    // Additional custom queries can be added here
}
