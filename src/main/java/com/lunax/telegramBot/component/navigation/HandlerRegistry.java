package com.lunax.telegramBot.component.navigation;

import com.lunax.telegramBot.component.navigation.components.ButtonHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class HandlerRegistry {
    private final Map<String, ButtonHandler> handlers = new HashMap<>();
    
    @Autowired
    private ApplicationContext applicationContext;
    
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
