package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.handler.HomePageButtons.CreateMemeCoin;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
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

    @Autowired
    private CreateMemeCoin createMemeCoin;

    @Autowired
    private StateService stateService;

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
            Message message = update.getMessage();
            
            // Check if it's a command (starts with '/')
            if (message.hasText() && message.getText().startsWith("/")) {
                Long telegramId = message.getFrom().getId();
                ChatState currentState = stateService.getState(telegramId);
                
                // If we were in memecoin creation, clear the state and notify
                if (currentState != null && currentState.isMemecoinCreation()) {
                    stateService.clearStateData(telegramId);
                    stateService.setState(telegramId, null);
                    MessageUtils.sendMessage(update, "⚠️ Memecoin creation process terminated.");
                }
                
                // Handle the start command
                if (message.getText().startsWith("/start")) {
                    home.executeVoid(update);
                }
                return;
            }
            
            // Only check state for non-command messages that might need state handling
            Long telegramId = message.getFrom().getId();
            ChatState currentState = stateService.getState(telegramId);
            
            if (currentState != null && currentState.isMemecoinCreation()) {
                createMemeCoin.handleMessage(update);
            } else if (message.hasText()) {
                log.info("Received: {}", message.getText());
            }
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void onClosing() {
        try {
            log.info("Closing bot resources...");
            if (this.getOptions() != null && this.getOptions().getBaseUrl() != null) {
                this.clearWebhook();
            }
            Thread.sleep(1000); // Give some time for pending operations to complete
        } catch (Exception e) {
            log.error("Error while closing bot resources: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}