package com.lunax.telegramBot.service;

import com.lunax.telegramBot.model.db.CopyTrading;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.dto.CopyTradingDTO;
import com.lunax.telegramBot.model.repository.CopyTradingRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CopyTradeService {
    
    private static final Logger rawDataLogger = LoggerFactory.getLogger("RawDataLogger");
    
    @Autowired
    private CopyTradingRepository copyTradingRepository;
    
    @Autowired
    private WalletService walletService;
    
    /**
     * Get all copy trading configurations for a user
     */
    @Transactional(readOnly = true)
    public List<CopyTrading> getCopyTradingByUserId(Long userId) {
        log.debug("Getting copy trading configurations for user ID: {}", userId);
        
        // Fetch from cache (if available)
        List<CopyTrading> cachedData = copyTradingRepository.findByUserIdOrderByCreatedAtAsc(userId);
        
        // Log the cached data
        log.debug("Cached data for user ID {}: {}", userId, cachedData);
        
        return cachedData;
    }
    
    /**
     * Enable or disable a copy trading configuration
     */
    @Transactional
    public CopyTrading toggleCopyTrading(Long configId, boolean enabled) {
        log.debug("Toggling copy trading configuration {}: enabled={}", configId, enabled);
        Optional<CopyTrading> configOpt = copyTradingRepository.findById(configId.intValue());
        
        if (configOpt.isPresent()) {
            CopyTrading config = configOpt.get();
            config.setEnabled(enabled);
            CopyTrading savedConfig = copyTradingRepository.save(config);
            clearUserConfigCache(config.getUser());
            return savedConfig;
        } else {
            log.error("Copy trading configuration not found with ID: {}", configId);
            return null;
        }
    }
    
    /**
     * Create a new copy trading configuration
     */
    @Transactional
    public CopyTrading createCopyTrading(User user, String sourceWalletAddress, String targetWalletAddress, double multiplier) {
        log.debug("Creating copy trading configuration for user: {}, source: {}, target: {}, multiplier: {}", 
            user.getTelegramId(), sourceWalletAddress, targetWalletAddress, multiplier);
        
        // For source wallet - only use existing wallets that belong to the user
        Wallet sourceWallet = walletService.findByWalletAddress(sourceWalletAddress)
            .orElseThrow(() -> new RuntimeException("Source wallet not found - must be one of your existing wallets"));
        
        // Check if source wallet belongs to user
        if (!sourceWallet.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Source wallet must be one of your own wallets");
        }
        
        // Validate target address format (but don't create a wallet for it)
        if (!isValidSolanaAddress(targetWalletAddress)) {
            throw new RuntimeException("Invalid target wallet address format");
        }
        
        // Create new configuration
        CopyTrading copyTrading = new CopyTrading();
        copyTrading.setUser(user);
        copyTrading.setSourceWallet(sourceWallet);  // User's own wallet
        copyTrading.setTargetAddress(targetWalletAddress); // External address to copy
        copyTrading.setEnabled(true);
        copyTrading.setAmountMultiplier(multiplier);
        
        CopyTrading savedConfig = copyTradingRepository.save(copyTrading);
        clearUserConfigCache(user);
        return savedConfig;
    }
    
    // Simple validator for Solana addresses
    private boolean isValidSolanaAddress(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        
        // Check if it looks like an @ mention or command
        if (address.contains("@") || address.contains("/")) {
            return false;
        }
        
        // Check length (typical Solana addresses are 32-44 chars)
        if (address.length() < 32 || address.length() > 44) {
            return false;
        }
        
        // Check if it uses only Base58 characters
        String base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        for (char c : address.toCharArray()) {
            if (base58Chars.indexOf(c) == -1) {
                return false;
            }
        }
        
        return true;
    }
    
    // Keep a backwards-compatible version
    @Deprecated
    public CopyTrading createCopyTrading(User user, String sourceWalletAddress, String targetWalletAddress) {
        return createCopyTrading(user, sourceWalletAddress, targetWalletAddress, 1.0);
    }
    
    /**
     * Delete a copy trading configuration
     */
    @Transactional
    public boolean deleteCopyTrading(Long configId) {
        log.debug("Deleting copy trading configuration: {}", configId);
        Optional<CopyTrading> configOpt = copyTradingRepository.findById(configId.intValue());
        
        if (configOpt.isPresent()) {
            copyTradingRepository.delete(configOpt.get());
            clearUserConfigCache(configOpt.get().getUser());
            return true;
        } else {
            log.error("Copy trading configuration not found with ID: {}", configId);
            return false;
        }
    }
    
    /**
     * Convert CopyTrading entities to DTOs
     */
    public List<CopyTradingDTO> convertToDTO(List<CopyTrading> configs) {
        return configs.stream()
            .map(config -> new CopyTradingDTO(
                config.getSourceWallet().getAddress(),
                config.getTargetAddress(),
                config.isEnabled()))
            .collect(Collectors.toList());
    }
    
    /**
     * Get a copy trading configuration by ID
     */
    @Transactional(readOnly = true)
    public CopyTrading getCopyTradingById(Long configId) {
        log.debug("Getting copy trading configuration with ID: {}", configId);
        return copyTradingRepository.findById(configId.intValue()).orElse(null);
    }

    @CacheEvict(value = "copyTradingConfigs", key = "#user.id")
    private void clearUserConfigCache(User user) {
        log.debug("Cleared copy trading config cache for user {}", user.getId());
    }

    // Method to fetch raw data from the database
    @Transactional(readOnly = true)
    public List<CopyTrading> getRawCopyTradingByUserId(Long userId) {
        log.debug("Fetching raw copy trading configurations for user ID: {}", userId);
        List<CopyTrading> rawData = copyTradingRepository.findByUserIdOrderByCreatedAtAsc(userId);
        
        // Log the raw data
        rawDataLogger.info("Raw data from DB for user ID {}: {}", userId, rawData);
        
        return rawData;
    }
}
