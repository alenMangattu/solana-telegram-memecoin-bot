package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.model.repository.UserStateRepository;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.db.UserState;
import com.lunax.telegramBot.model.db.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class StateService {

    private static final Logger log = LoggerFactory.getLogger(StateService.class);

    @Autowired
    private UserStateRepository userStateRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Cacheable(value = "userStates", key = "#telegramId")
    public ChatState getState(Long telegramId) {
        Optional<User> userOptional = userRepository.findByTelegramId(telegramId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            Optional<UserState> userState = userStateRepository.findByUser(user);
            return userState
                .map(state -> {
                    String statePage = state.getCurrentPage();
                    if (statePage == null || statePage.isEmpty()) {
                        return ChatState.IDLE;
                    }
                    try {
                        return ChatState.valueOf(statePage);
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid chat state '{}' for user {}, defaulting to IDLE", statePage, telegramId);
                        return ChatState.IDLE;
                    }
                })
                .orElse(ChatState.IDLE);
        }
        return null;
    }

    @CacheEvict(value = "userStates", key = "#telegramId")
    public void setState(Long telegramId, ChatState state) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        if (state == null) {
            // If state is null, delete any existing state
            userStateRepository.findByUser(user).ifPresent(userState -> 
                userStateRepository.delete(userState)
            );
            return;
        }
        
        UserState userState = userStateRepository.findByUser(user)
                .orElse(new UserState());
        userState.setUser(user);
        userState.setCurrentPage(state.name());
        userStateRepository.save(userState);
    }

    @Cacheable(value = "stateData", key = "#telegramId")
    public ObjectNode getStateData(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        UserState userState = userStateRepository.findByUser(user)
                .orElse(new UserState());
        return userState.getStateData() != null ? 
               (ObjectNode) userState.getStateData() : 
               objectMapper.createObjectNode();
    }

    @CacheEvict(value = "stateData", key = "#telegramId")
    public void setStateData(Long telegramId, ObjectNode stateData) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        UserState userState = userStateRepository.findByUser(user)
                .orElse(new UserState());
        userState.setUser(user);
        userState.setStateData(stateData);
        userStateRepository.save(userState);
    }

    @CacheEvict(value = {"userStates", "stateData"}, key = "#telegramId")
    public void clearStateData(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        UserState userState = userStateRepository.findByUser(user)
                .orElse(new UserState());
        userState.setUser(user);
        userState.setStateData(null);
        userState.setCurrentPage("");
        userStateRepository.save(userState);
    }
}
