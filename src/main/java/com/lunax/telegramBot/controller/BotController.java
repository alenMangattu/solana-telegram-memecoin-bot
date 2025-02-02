package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.lunax.telegramBot.utils.message.MessageUtils;
import com.lunax.telegramBot.utils.message.ButtonHandler;
import com.lunax.telegramBot.handlers.Button1Handler;
import com.lunax.telegramBot.handlers.Button2Handler;
import com.lunax.telegramBot.handlers.Button4Handler;

import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BotController extends TelegramLongPollingBot {

    private final String botUsername;
    private final Map<String, ButtonHandler> buttonHandlers;

    public BotController(String botUsername, String botToken) {
        super(botToken);
        this.botUsername = botUsername;
        MessageUtils.setBotInstance(this);
        this.buttonHandlers = new HashMap<>();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            ButtonHandler handler = buttonHandlers.get(callbackData);
            
            if (handler != null) {
                handler.handleCallback(update);
                SendMessage response = new SendMessage();
                response.setChatId(update.getCallbackQuery().getMessage().getChatId());
                response.setText("Handled by " + handler.getClass().getSimpleName() + " with text: " + handler.getButtonText());
                try {
                    execute(response);
                } catch (TelegramApiException e) {
                    log.error("Error sending message, {}", e.getMessage());
                }
            }
        } else if (update.hasMessage()) {
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
                
                // Register the new handlers in the map
                for (ButtonHandler[] row : buttonHandlers) {
                    for (ButtonHandler handler : row) {
                        this.buttonHandlers.put(handler.getCallbackData(), handler);
                    }
                }
                
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