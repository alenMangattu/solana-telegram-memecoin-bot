package com.lunax.telegramBot.component.navigation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeMintAuthority;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.YesOrNoButtons.RevokeFreezeAuthority;
import com.lunax.telegramBot.utils.components.ButtonHandler;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HandlerRegistry {
    private final Map<String, ButtonHandler> handlers = new HashMap<>();
    
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    public HandlerRegistry(
            AskMemeCoinData askMemeCoinData,
            AddLiquidity addLiquidity,
            RevokeMintAuthority revokeMintAuthority,
            RevokeFreezeAuthority revokeFreezeAuthority) {
        
        handlers.put(askMemeCoinData.getCallbackData(), askMemeCoinData);
        handlers.put(addLiquidity.getCallbackData(), addLiquidity);
        handlers.put("revoke_mint_authority_confirm", revokeMintAuthority);
        handlers.put("revoke_freeze_authority_confirm", revokeFreezeAuthority);
        
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
    }
    
    public ButtonHandler getHandler(String callbackData) {
        return handlers.get(callbackData);
    }
    
    public Map<String, ButtonHandler> getAllHandlers() {
        return handlers;
    }
}
