package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.UserSettings;
import com.lunax.telegramBot.model.repository.UserSettingsRepository;

import java.util.Optional;

@Service
public class UserSettingsService {

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    public Optional<UserSettings> findByUserId(Long userId) {
        return userSettingsRepository.findByUserId(userId);
    }
} 