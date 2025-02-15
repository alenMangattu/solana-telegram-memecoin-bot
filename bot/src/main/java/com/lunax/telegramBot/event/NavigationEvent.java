package com.lunax.telegramBot.event;

import org.telegram.telegrambots.meta.api.objects.Update;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NavigationEvent extends ApplicationEvent {
    private final Update update;
    private final String destination;

    public NavigationEvent(Object source, Update update, String destination) {
        super(source);
        this.update = update;
        this.destination = destination;
    }
} 