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

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

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
            userSettings.setSettings(objectMapper.readTree(
                "{\"buy\": [0.1, 0.5, 2], \"sell\": [0.1, 0.5, 2], \"slippage\": 20}"
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse settings JSON", e);
        }
        
        userSettings.setCreatedAt(LocalDateTime.now());
        userSettings.setUpdatedAt(LocalDateTime.now());
        userSettingsRepository.save(userSettings);
    }

    public List<User> findByTelegramIds(List<Long> telegramIds) {
        return userRepository.findAllByTelegramIdIn(telegramIds);
    }
}