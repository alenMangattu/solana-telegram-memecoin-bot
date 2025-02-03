package com.lunax.telegramBot.component.navigation.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.handler.AcceptTosHandler;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class Home implements StateHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private AcceptTosHandler acceptTosHandler;

    public static String tos = """
            Welcome to Solana Meme Coin Bot

            Before proceeding, please review and accept our Terms of Service:

            1. This bot is for creating and managing Solana-based meme coins
            2. You are responsible for keeping your private keys secure
            3. Use this bot at your own risk

            Do you accept these terms?
            """;

    @Override
    public String execute(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        if (!userService.hasAcceptedTos(telegramId)) {
            userService.updateTosAcceptance(telegramId, false);
            return tos;
        }
        return "Home";
    }

    @Override
    public void executeVoid(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        System.out.println("From ID: " + telegramId);

        ButtonHandler[][] buttonHandlers = {{
            acceptTosHandler
        }};
        
        if (!userService.hasAcceptedTos(telegramId)) {
            System.out.println("User has not yet accepted the TOS");
            userService.updateTosAcceptance(telegramId, false);
            MessageUtils.sendInlineKeyboard(update, tos, buttonHandlers); 
            return;
        }
        
        MessageUtils.sendMessage(update, "Home");
    }
}