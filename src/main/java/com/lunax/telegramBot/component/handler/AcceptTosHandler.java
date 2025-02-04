package com.lunax.telegramBot.component.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.createWallet.AddWallet;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class AcceptTosHandler implements ButtonHandler {

    private final String buttonText;
    private final String callbackData; 

    @Autowired
    private UserService userService;

    @Autowired
    private AddWallet createWallet;

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
        
        MessageUtils.editMessage(update, "*Terms of Service Accepted* ✅", null);
        createWallet.executeVoid(update);
    }
}