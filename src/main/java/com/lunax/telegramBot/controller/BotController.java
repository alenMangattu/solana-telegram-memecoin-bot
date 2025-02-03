package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.lunax.telegramBot.component.handler.Button1Handler;
import com.lunax.telegramBot.component.handler.Button2Handler;
import com.lunax.telegramBot.component.handler.Button4Handler;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.ButtonHandler;
import com.lunax.telegramBot.component.navigation.components.MessageUtils;
import com.lunax.telegramBot.component.navigation.states.Home;

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
                // SendMessage response = new SendMessage();
                // response.setChatId(update.getCallbackQuery().getMessage().getChatId());
                // response.setText("✅ Action completed successfully!");
                // execute(response);
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
            try {
                // Delete the message
                DeleteMessage deleteMessage = new DeleteMessage(message.getChatId().toString(), message.getMessageId());
                execute(deleteMessage);
                // Send a new message
                SendMessage sendMessage = new SendMessage(message.getChatId().toString(), "Hello World + " + message.getText());
                execute(sendMessage);
                
                // Create button configuration using handlers with custom text
                ButtonHandler[][] buttonHandlers = {
                    {
                        new Button1Handler("Click me now!"),
                        new Button2Handler("Press here")
                    },
                    {
                        new Button4Handler("Special button 4")
                    }
                };
                
                MessageUtils.sendInlineKeyboard(message, "Choose an option:", buttonHandlers);
            } catch (TelegramApiException e) {
                log.error("Error sending message, {}", e.getMessage());
            }
        }
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }
}