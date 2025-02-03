package com.lunax.telegramBot.model.repository;

import com.lunax.telegramBot.model.db.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByWalletAddress(String walletAddress);
}
