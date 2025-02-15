package com.lunax.telegramBot.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.repository.UserRepository;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
   
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
    
    @CacheEvict(value = {"userInfo", "userValidation", "tosAcceptance"}, key = "#telegramId")
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

    @CacheEvict(value = {"userInfo", "userValidation", "tosAcceptance"}, key = "#telegramId")
    public void createUser(Long telegramId, String username) {
        User user = new User();
        user.setTelegramId(telegramId);
        user.setUsername(username);
        user.setCreatedAt(LocalDateTime.now());
        user.setAcceptedTos(false);
        userRepository.save(user);
    }
}