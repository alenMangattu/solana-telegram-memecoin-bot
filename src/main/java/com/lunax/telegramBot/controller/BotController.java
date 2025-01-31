package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotController extends TelegramLongPollingBot {

    private final String botUsername;

    public BotController(String botUsername, String botToken) {
        super(botToken);
        this.botUsername = botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        log.info("Received: {}", message.getText());
        try {
            // Delete the message
            org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage deleteMessage = new org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage(message.getChatId().toString(), message.getMessageId());
            execute(deleteMessage);
            // Send a new message
            SendMessage sendMessage = new SendMessage(message.getChatId().toString(), "Hello World + " + message.getText());
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message, {}", e.getMessage());
        }   
    }

    @Override
    public String getBotUsername() {
        return this.botUsername;
    }
}