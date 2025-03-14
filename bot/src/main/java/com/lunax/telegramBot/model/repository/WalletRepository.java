package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.db.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.List;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByWalletAddress(String walletAddress);

    @Cacheable(value = "userWallets", key = "#user.id")
    List<Wallet> findByUser(User user);

    @Override
    @CacheEvict(value = "userWallets", key = "#result?.user?.id", condition = "#result?.user != null")
    <S extends Wallet> S save(S wallet);

    @Query("SELECT w FROM Wallet w WHERE w.user = :user")
    @CacheEvict(value = "userWallets", key = "#user.id")
    void invalidateUserWalletsCache(@Param("user") User user);

    // New method to delete a wallet by its ID

    @Query("DELETE FROM Wallet w WHERE w.walletId = :walletId")
    void deleteByWalletId(@Param("walletId") UUID walletId);

    // Add a method that can be used when we have both user and wallet ID
    @CacheEvict(value = "userWallets", key = "#user.id")
    @Query("DELETE FROM Wallet w WHERE w.walletId = :walletId AND w.user = :user")
    void deleteByWalletIdAndUser(@Param("walletId") UUID walletId, @Param("user") User user);
}
