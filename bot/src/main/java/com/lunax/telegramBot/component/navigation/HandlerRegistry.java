package com.lunax.telegramBot.component.navigation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeFreezeAuthority;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeMintAuthority;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.component.navigation.components.mymemecoin.PaginationButtonHandler;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class HandlerRegistry {
    private final Map<String, ButtonHandler> handlers = new HashMap<>();
    private final Map<String, StateHandler> stateHandlers = new HashMap<>();
    
    @Autowired
    private ApplicationContext applicationContext;

    private static final Logger log = LoggerFactory.getLogger(HandlerRegistry.class);

    @Autowired
    public HandlerRegistry(
            AskMemeCoinData askMemeCoinData,
            AddLiquidity addLiquidity,
            RevokeMintAuthority revokeMintAuthority,
            RevokeFreezeAuthority revokeFreezeAuthority,
            PaginationButtonHandler paginationButtonHandler) {
        
        handlers.put(askMemeCoinData.getCallbackData(), askMemeCoinData);
        handlers.put(addLiquidity.getCallbackData(), addLiquidity);
        handlers.put("revoke_mint_authority_confirm", revokeMintAuthority);
        handlers.put("revoke_freeze_authority_confirm", revokeFreezeAuthority);
        handlers.put("page_", paginationButtonHandler);
        handlers.put("revoke_mint_authority_cancel", new CancelMintAuthority(revokeMintAuthority));
    }
    
    private static class CancelMintAuthority implements ButtonHandler {
        private final RevokeMintAuthority revokeMintAuthority;

        CancelMintAuthority(RevokeMintAuthority revokeMintAuthority) {
            this.revokeMintAuthority = revokeMintAuthority;
        }

        @Override
        public String getButtonText() { return "No"; }

        @Override
        public String getCallbackData() { return "revoke_mint_authority_cancel"; }

        @Override
        public void handleCallback(Update update) {
            revokeMintAuthority.handleCancel(update);
        }
    }
    
    @PostConstruct
    public void init() {
        // Get all beans that implement ButtonHandler
        Map<String, ButtonHandler> handlerBeans = applicationContext.getBeansOfType(ButtonHandler.class);
        
        // Register each handler by its callback data
        handlerBeans.values().forEach(handler -> 
            handlers.put(handler.getCallbackData(), handler)
        );
        
        // Get all beans that implement StateHandler
        Map<String, StateHandler> stateHandlerBeans = applicationContext.getBeansOfType(StateHandler.class);
        
        // Register each state handler by its state name
        stateHandlerBeans.values().forEach(handler -> {
            if (handler.getClass().getSimpleName() != null) {
                String stateName = handler.getClass().getSimpleName().toUpperCase();
                if (stateName.equals("TOKENINFO")) {
                    registerStateHandler(ChatState.TOKEN_INFO_PAGE.toString(), handler);
                } else {
                    try {
                        ChatState state = ChatState.valueOf(stateName);
                        registerStateHandler(state.toString(), handler);
                    } catch (IllegalArgumentException e) {
                        // Not a standard state name, ignore
                    }
                }
            }
        });
    }
    
    public ButtonHandler getHandler(String callbackData) {
        // First try exact match
        ButtonHandler handler = handlers.get(callbackData);
        if (handler != null) {
            return handler;
        }
        
        // If no exact match, try prefix match
        return handlers.entrySet().stream()
            .filter(entry -> !entry.getKey().isEmpty() && callbackData.startsWith(entry.getKey()))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);
    }
    
    public void registerHandler(String callbackData, ButtonHandler handler) {
        handlers.put(callbackData, handler);
        log.info("Registered handler for callback data: {}", callbackData);
    }
    
    public Map<String, ButtonHandler> getAllHandlers() {
        return handlers;
    }

    public StateHandler getHandler(ChatState state) {
        return getStateHandler(state.toString());
    }

    public void registerStateHandler(String stateName, StateHandler handler) {
        stateHandlers.put(stateName, handler);
    }

    public StateHandler getStateHandler(String stateName) {
        return stateHandlers.get(stateName);
    }
}
