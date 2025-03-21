package com.lunax.telegramBot.event;

import org.springframework.context.ApplicationEvent;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Event that represents a callback query from Telegram
 */
public class CallbackQueryEvent extends ApplicationEvent {
    private final Update update;
    
    public CallbackQueryEvent(Object source, Update update) {
        super(source);
        this.update = update;
    }
    
    public Update getUpdate() {
        return update;
    }
} 