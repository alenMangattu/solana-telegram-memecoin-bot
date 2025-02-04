package com.lunax.telegramBot.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.repository.UserRepository;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
   
    @Cacheable(value = "tosAcceptance", key = "#telegramId")
    public boolean hasAcceptedTos(Long telegramId) {
        Optional<User>  user = userRepository.findByTelegramId(telegramId); 
        return user.isPresent() && userRepository.isAcceptedTos(telegramId);
    }
    
    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }
    
    @CachePut(value = "tosAcceptance", key = "#telegramId")
    public boolean updateTosAcceptance(Long telegramId, boolean accepted) {
        User user = userRepository.findByTelegramId(telegramId)
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setTelegramId(telegramId);
                return newUser;
            });
        
        user.setAcceptedTos(accepted);
        userRepository.save(user);
        return accepted;
    }
}