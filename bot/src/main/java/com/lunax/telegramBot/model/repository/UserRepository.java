package com.lunax.telegramBot.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lunax.telegramBot.model.db.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByTelegramId(Long telegramId);
    
    // New method to check if a user exists by telegram ID
    boolean existsByTelegramId(Long telegramId);
    
    // New method to check if a user exists by telegram ID and has accepted TOS
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.telegramId = :telegramId AND u.acceptedTos = true")
    boolean existsByTelegramIdAndAcceptedTosTrue(@Param("telegramId") Long telegramId);
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.telegramId = :telegramId AND u.acceptedTos = true")
    boolean isAcceptedTos(@Param("telegramId") Long telegramId);
    
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.wallets WHERE u.telegramId = :telegramId")
    Optional<User> findByTelegramIdWithWallets(@Param("telegramId") Long telegramId);
}
