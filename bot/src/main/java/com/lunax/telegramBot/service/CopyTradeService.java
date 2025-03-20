package com.lunax.telegramBot.service;

import com.lunax.telegramBot.model.db.CopyTrading;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.dto.CopyTradingDTO;
import com.lunax.telegramBot.model.repository.CopyTradingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CopyTradeService {
    
    @Autowired
    private CopyTradingRepository copyTradingRepository;
    
    @Autowired
    private WalletService walletService;
    
    /**
     * Get all copy trading configurations for a user
     */
    public List<CopyTrading> getCopyTradingByUserId(Long userId) {
        log.debug("Getting copy trading configurations for user ID: {}", userId);
        return copyTradingRepository.findByUserId(userId.intValue());
    }
    
    /**
     * Enable or disable a copy trading configuration
     */
    public CopyTrading toggleCopyTrading(Long configId, boolean enabled) {
        log.debug("Toggling copy trading configuration {}: enabled={}", configId, enabled);
        Optional<CopyTrading> configOpt = copyTradingRepository.findById(configId.intValue());
        
        if (configOpt.isPresent()) {
            CopyTrading config = configOpt.get();
            config.setEnabled(enabled);
            return copyTradingRepository.save(config);
        } else {
            log.error("Copy trading configuration not found with ID: {}", configId);
            return null;
        }
    }
    
    /**
     * Create a new copy trading configuration
     */
    public CopyTrading createCopyTrading(User user, String sourceWalletAddress, String targetWalletAddress) {
        log.debug("Creating copy trading configuration for user: {}, source: {}, target: {}", 
            user.getTelegramId(), sourceWalletAddress, targetWalletAddress);
        
        // Find or create wallets
        Wallet sourceWallet = walletService.findOrCreateByAddress(sourceWalletAddress);
        Wallet targetWallet = walletService.findOrCreateByAddress(targetWalletAddress);
        
        if (sourceWallet == null || targetWallet == null) {
            log.error("Could not find or create wallets for copy trading");
            return null;
        }
        
        // Create new configuration
        CopyTrading copyTrading = new CopyTrading();
        copyTrading.setUser(user);
        copyTrading.setSourceWallet(sourceWallet);
        copyTrading.setTargetWallet(targetWallet);
        copyTrading.setEnabled(true);
        
        return copyTradingRepository.save(copyTrading);
    }
    
    /**
     * Delete a copy trading configuration
     */
    public boolean deleteCopyTrading(Long configId) {
        log.debug("Deleting copy trading configuration: {}", configId);
        Optional<CopyTrading> configOpt = copyTradingRepository.findById(configId.intValue());
        
        if (configOpt.isPresent()) {
            copyTradingRepository.delete(configOpt.get());
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
                config.getTargetWallet().getAddress(),
                config.isEnabled()))
            .collect(Collectors.toList());
    }
}
