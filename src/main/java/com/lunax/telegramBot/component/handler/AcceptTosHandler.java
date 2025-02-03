package com.lunax.telegramBot.component.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import com.lunax.telegramBot.component.navigation.components.ButtonHandler;
import com.lunax.telegramBot.component.navigation.components.MessageUtils;
import com.lunax.telegramBot.service.UserService;

@Component
public class AcceptTosHandler implements ButtonHandler {

    private final String buttonText;
    private final String callbackData; 

    @Autowired
    private UserService userService;

    public AcceptTosHandler(@Value("✅ Accept Terms of Service") String buttonText, 
                            @Value("accept_tos") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
    }

    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }

    @Override
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        userService.updateTosAcceptance(telegramId, true);
        
        MaybeInaccessibleMessage maybeMessage = update.getCallbackQuery().getMessage();
        if (maybeMessage instanceof Message message) {
            MessageUtils.editMessage(message, "*Terms of Service Accepted* ✅\nYou can now use all bot features!", null);
        }
    }
}