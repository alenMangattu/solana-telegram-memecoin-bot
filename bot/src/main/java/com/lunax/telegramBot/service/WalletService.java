package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.utils.WalletKeyManager;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Optional;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    @Autowired
    private WalletRepository walletRepository;
    
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    
    @Autowired
    private WalletKeyManager walletKeyManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteWallet(User user, String walletId) {
        // First verify the wallet exists and belongs to the user
        Wallet wallet = walletRepository.findById(UUID.fromString(walletId))
            .orElseThrow(() -> new RuntimeException("Wallet not found"));
            
        if (!wallet.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("This wallet does not belong to you");
        }
        
        UUID uuid = UUID.fromString(walletId);
        
        // Use a direct query to delete
        int deleted = entityManager.createQuery("DELETE FROM Wallet w WHERE w.walletId = :id")
            .setParameter("id", uuid)
            .executeUpdate();
        
        log.info("Deleted {} wallet records with ID {}", deleted, walletId);
        
        // Delete security-related files
        try {
            walletKeyManager.deleteWalletFiles(walletId);
        } catch (Exception e) {
            log.warn("Failed to delete wallet files: {}", e.getMessage());
        }
        
        // Force commit and clear cache
        entityManager.flush();
        entityManager.clear();
        
        log.info("Wallet {} deleted successfully for user {}", walletId, user.getTelegramId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Wallet> findByUser(User user) {
        // Start with a fresh session
        entityManager.clear();
        
        List<Wallet> wallets = walletRepository.findByUser(user);
        log.info("Found {} wallets for user {}", wallets.size(), user.getTelegramId());
        
        // Log each wallet ID for debugging
        for (Wallet wallet : wallets) {
            log.info("  - Wallet ID: {}, Address: {}", wallet.getWalletId(), wallet.getWalletAddress());
        }
        
        return wallets;
    }

    @Transactional
    public void saveWallet(User user, Wallet wallet) {
        walletRepository.save(wallet);
        // Force persistence
        entityManager.flush();
    }

    /**
     * Clear Hibernate session cache to ensure fresh data
     */
    public void clearSessionCache() {
        entityManager.clear();
    }

    /**
     * Find a wallet by address or create a new one if it doesn't exist
     */
    public Wallet findOrCreateByAddress(String address) {
        Optional<Wallet> walletOpt = walletRepository.findByAddress(address);
        
        if (walletOpt.isPresent()) {
            return walletOpt.get();
        } else {
            Wallet wallet = new Wallet();
            wallet.setAddress(address);
            // Set other required fields based on your implementation
            return walletRepository.save(wallet);
        }
    }
}
