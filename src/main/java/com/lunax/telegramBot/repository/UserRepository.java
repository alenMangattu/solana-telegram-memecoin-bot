package com.lunax.telegramBot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lunax.telegramBot.model.db.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByTelegramId(Long telegramId);
}
