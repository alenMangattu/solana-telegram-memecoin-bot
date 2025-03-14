package com.lunax.telegramBot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.UserSettings;
import com.lunax.telegramBot.model.repository.UserSettingsRepository;

@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Cacheable(value = "userSettings", key = "#userId")
    public UserSettings findByUserId(Long userId) {
        log.debug("Getting user settings from DB for user ID: {}", userId);
        return userSettingsRepository.findByUserId(userId).orElse(null);
    }

    @CacheEvict(value = "userSettings", key = "#userSettings.userId")
    public UserSettings save(UserSettings userSettings) {
        log.debug("Saving user settings and evicting cache for user ID: {}", userSettings.getUserId());
        return userSettingsRepository.save(userSettings);
    }

    @CacheEvict(value = "userSettings", key = "#userId")
    public void clearCache(Long userId) {
        log.debug("Manually clearing settings cache for user ID: {}", userId);
    }
} 