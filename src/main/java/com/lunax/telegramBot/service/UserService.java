package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.repository.UserRepository;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Cacheable(value = "tosAcceptance", key = "#telegramId")
    public boolean hasAcceptedTos(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId);
        return user != null && user.isAcceptedTos();
    }
    
    @CachePut(value = "tosAcceptance", key = "#telegramId")
    public boolean updateTosAcceptance(Long telegramId, boolean accepted) {
        User user = userRepository.findByTelegramId(telegramId);
        if (user == null) {
            user = new User();
            user.setTelegramId(telegramId);
        }
        user.setAcceptedTos(accepted);
        userRepository.save(user);
        return accepted;
    }
}
