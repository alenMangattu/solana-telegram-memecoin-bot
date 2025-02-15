package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;

import java.util.List;
import java.util.UUID;

@Service
public class WalletService {
    @Autowired
    private WalletRepository walletRepository;

    @Cacheable(value = "walletsByUser", key = "#user.id")
    public List<Wallet> findByUser(User user) {
        return walletRepository.findByUser(user);
    }

    @CacheEvict(value = "walletsByUser", key = "#user.id")
    public void saveWallet(User user, Wallet wallet) {
        walletRepository.save(wallet);
    }

    @Transactional
    @CacheEvict(value = "walletsByUser", key = "#user.id")
    public void deleteWallet(User user, String walletId) {
        try {
            UUID uuid = UUID.fromString(walletId);
            walletRepository.deleteByWalletId(uuid);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid wallet ID format", e);
        }
    }
}
