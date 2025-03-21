package com.lunax.telegramBot.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig {

    // List of caches that need time-based expiration (per user)
    private static final List<String> TIME_BASED_CACHES = Arrays.asList(
            "tokenDetailsCache",
            "userTokenAccounts",
            "copyTradingConfigs"
    );

    // Standard caches without time-based expiration
    private static final List<String> STANDARD_CACHES = Arrays.asList(
            "tosAcceptance",
            "userWallets",
            "userStates",
            "stateData",
            "userValidation",
            "userInfo",
            "walletsByUser",
            "topTokenHolders",
            "tokenHolders",
            "userSettings",
            "homeWalletCache",
            "limitOrders"
    );

    @Bean
    public CacheManager cacheManager() {
        log.info("Initializing cache manager...");
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        
        List<Cache> caches = new ArrayList<>();
        
        // Add standard caches
        for (String cacheName : STANDARD_CACHES) {
            caches.add(new ConcurrentMapCache(cacheName));
            log.info("Added standard cache: {}", cacheName);
        }
        
        // Add time-based caches
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats();
                
        for (String cacheName : TIME_BASED_CACHES) {
            caches.add(new CaffeineCache(cacheName, caffeine.build()));
            log.info("Added time-based cache with 30s expiration: {}", cacheName);
        }
        
        cacheManager.setCaches(caches);
        log.info("Cache manager initialized with {} caches", caches.size());
        
        return cacheManager;
    }
}