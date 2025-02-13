package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.Memecoin; // Ensure you have a Memecoin entity
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemecoinRepo extends JpaRepository<Memecoin, Long> {
    // You can define custom query methods here if needed
} 