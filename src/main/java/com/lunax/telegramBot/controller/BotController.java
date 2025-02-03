package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BotController extends TelegramLongPollingBot {

    private final String botUsername;
    
    @Autowired
    private Home home;

    @Autowired
    private HandlerRegistry handlerRegistry;

    public BotController(String botUsername, String botToken) {
        super(botToken);
        this.botUsername = botUsername;
        MessageUtils.setBotInstance(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            ButtonHandler handler = handlerRegistry.getHandler(callbackData);
            
            if (handler != null) {
                handler.handleCallback(update);
            } else {
                log.warn("No handler found for callback data: {}", callbackData);
            }
        } else if (update.hasMessage()) {
            if (update.getMessage().getText().startsWith("/start")) {
                home.executeVoid(update);
                return;
            }
            Message message = update.getMessage();
            log.info("Received: {}", message.getText());
        }
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }
}