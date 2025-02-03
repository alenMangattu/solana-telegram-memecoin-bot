package com.lunax.telegramBot.component.navigation.components.settings;

import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.StateHandler;

public class houseOfRules implements StateHandler {

    @Override
    public String execute(Update update) {
        return "House of Rules - Trading Guidelines and Policies";
    }
    
}
