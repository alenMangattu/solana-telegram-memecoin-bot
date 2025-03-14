package com.lunax.telegramBot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.UserSettings;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.UserSettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Cacheable(value = "userInfo", key = "#telegramId")
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    @Cacheable(value = "userValidation", key = "#telegramId")
    public boolean isValidUser(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

    @Cacheable(value = "tosAcceptance", key = "#telegramId")
    public boolean hasAcceptedTos(Long telegramId) {
        return userRepository.existsByTelegramIdAndAcceptedTosTrue(telegramId);
    }

    @CacheEvict(value = { "userInfo", "userValidation", "tosAcceptance" }, key = "#telegramId")
    public void updateTosAcceptance(Long telegramId, boolean accepted) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramId);
                    return newUser;
                });

        user.setAcceptedTos(accepted);
        userRepository.save(user);
    }

    @CacheEvict(value = { "userInfo", "userValidation", "tosAcceptance" }, key = "#telegramId")
    public void createUser(Long telegramId, String username) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setUsername(username);
        user.setCreatedAt(LocalDateTime.now());
        user.setAcceptedTos(false);
        userRepository.save(user);

        UserSettings userSettings = new UserSettings();
        userSettings.setUserId(user.getId());
        
        try {
            // Create comprehensive default settings
            ObjectNode settingsJson = objectMapper.createObjectNode();
            
            // Default buy amounts [0.1, 0.5, 2]
            ArrayNode buyArray = settingsJson.putArray("buy");
            buyArray.add(0.1);
            buyArray.add(0.5);
            buyArray.add(2.0);
            
            // Default sell amounts [0.1, 0.5, 2]
            ArrayNode sellArray = settingsJson.putArray("sell");
            sellArray.add(0.1);
            sellArray.add(0.5);
            sellArray.add(2.0);
            
            // Default buy_slippage [20]
            ArrayNode buySlippageArray = settingsJson.putArray("buy_slippage");
            buySlippageArray.add(20);
            
            // Default sell_slippage [50]
            ArrayNode sellSlippageArray = settingsJson.putArray("sell_slippage");
            sellSlippageArray.add(50);
            
            // Default EMA [1, 3]
            ArrayNode emaArray = settingsJson.putArray("EMA");
            emaArray.add(1);
            emaArray.add(3);
            
            // Default RSI 3
            settingsJson.put("RSI", 3);
            
            // Default speed MEDIUM
            settingsJson.put("speed", "MEDIUM");
            
            userSettings.setSettings(settingsJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create default settings", e);
        }
        
        userSettings.setCreatedAt(LocalDateTime.now());
        userSettings.setUpdatedAt(LocalDateTime.now());
        userSettingsRepository.save(userSettings);
    }

    public List<User> findByTelegramIds(List<Long> telegramIds) {
        return userRepository.findAllByTelegramIdIn(telegramIds);
    }
}