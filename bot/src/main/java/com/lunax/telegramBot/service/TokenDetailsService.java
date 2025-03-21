package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.utils.SolanaUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TokenDetailsService {

    @Autowired
    private UserService userService;
    
    @Autowired
    private SolanaUtils solanaUtils;
    
    @Autowired
    private CacheManager cacheManager;
    
    /**
     * Get token details with caching
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTokenDetailsWithCache(String walletAddress) {
        Cache tokenDetailsCache = cacheManager.getCache("tokenDetailsCache");
        
        // Check if data is in cache
        if (tokenDetailsCache != null) {
            Cache.ValueWrapper cachedValue = tokenDetailsCache.get(walletAddress);
            if (cachedValue != null && cachedValue.get() != null) {
                log.info("Retrieved token details from cache for wallet: {}", walletAddress);
                return (List<Map<String, Object>>) cachedValue.get();
            }
        }
        
        log.info("Token details not in cache for wallet {}, fetching from Solana...", walletAddress);
        
        // Not in cache, fetch data
        List<String> tokenAddresses = solanaUtils.getTokenAccounts(walletAddress);
        List<Map<String, Object>> tokenDetails = new ArrayList<>();
        
        for (String tokenAddress : tokenAddresses) {
            Map<String, Object> tokenDetail = new HashMap<>();
            tokenDetail.put("address", tokenAddress);
            
            // Get balance
            double balance = 0.0;
            try {
                balance = solanaUtils.getTokenBalance(walletAddress, tokenAddress);
            } catch (Exception e) {
                log.error("Error getting balance for token {}: {}", tokenAddress, e.getMessage());
            }
            tokenDetail.put("balance", balance);
            
            // Get price
            double priceUSD = 0.0;
            try {
                Map<String, Object> tokenInfo = solanaUtils.getMemecoinDetails(tokenAddress);
                if (tokenInfo.containsKey("currentPrice")) {
                    priceUSD = (Double) tokenInfo.get("currentPrice");
                }
            } catch (Exception e) {
                log.error("Error getting price for token {}: {}", tokenAddress, e.getMessage());
            }
            tokenDetail.put("priceUSD", priceUSD);
            
            tokenDetails.add(tokenDetail);
        }
        
        // Store in cache
        if (tokenDetailsCache != null) {
            tokenDetailsCache.put(walletAddress, tokenDetails);
            log.info("Saved token details to cache for wallet: {}", walletAddress);
        }
        
        return tokenDetails;
    }
    
    /**
     * Print cache contents for debugging
     */
    public void printCacheContents() {
        Cache tokenDetailsCache = cacheManager.getCache("tokenDetailsCache");
        if (tokenDetailsCache != null) {
            log.info("=== Current Token Details Cache Contents ===");
            if (tokenDetailsCache instanceof ConcurrentMapCache) {
                ConcurrentMapCache concurrentCache = (ConcurrentMapCache) tokenDetailsCache;
                concurrentCache.getNativeCache().forEach((key, value) -> {
                    log.info("Cache Key: {} (expires in ~30s from last access)", key);
                    log.info("Cache Value: {}", value);
                });
            }
            log.info("===========================");
        }
    }
    
    /**
     * Get user from cache or database
     */
    @Cacheable(value = "userInfo", key = "#telegramId")
    public User getUserFromCacheOrDatabase(Long telegramId) {
        return userService.findByTelegramId(telegramId).orElse(null);
    }
    
    /**
     * Validate if a string is a valid Solana address
     */
    public boolean isValidSolanaAddress(String address) {
        // Basic validation for Solana addresses
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
} 