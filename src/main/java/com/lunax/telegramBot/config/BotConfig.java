package com.lunax.telegramBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.lunax.telegramBot.controller.BotController;

import org.telegram.telegrambots.meta.TelegramBotsApi;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BotConfig {

    @Bean
    public BotController botController(@Value("${telegram.bot.name}") String botName, 
                                        @Value("${telegram.bot.token}") String botToken) {
            BotController botController = new BotController(botName, botToken);
            TelegramBotsApi telegramBotsApi;
            try {
                telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
                telegramBotsApi.registerBot(botController);
                
                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Shutting down bot session...");
                    botController.onClosing();
                }));
            } catch (TelegramApiException e) {
                log.error("Error initializing Telegram API, {}", e.getMessage());
                e.printStackTrace();
            }
            return botController;
        }
}