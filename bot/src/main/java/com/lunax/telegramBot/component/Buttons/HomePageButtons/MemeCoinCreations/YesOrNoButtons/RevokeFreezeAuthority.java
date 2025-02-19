package com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.YesOrNoButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;

@Component
public class RevokeFreezeAuthority implements ButtonHandler {

    @Autowired
    private StateService stateService;

    private final String buttonText;
    private final String callbackData;

    public RevokeFreezeAuthority(@Value("Yes") String buttonText,
                                @Value("revoke_freeze_authority_confirm") String callbackData) {
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
        ObjectNode stateData = stateService.getStateData(telegramId);
        stateData.put("revokeFreezeAuthority", true);
        stateService.setStateData(telegramId, stateData);

        MessageUtils.editMessage(update, "Revoking freeze authority set to: " +(stateData.get("revokeFreezeAuthority").asBoolean() ? "true" : "false") + " (7/8)", null);
       
        // Now ask for image
        askForImage(update);
    }

    private void askForImage(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String message = """
                🖼️ *Upload Image* (8/8)

                Please upload an image for your memecoin.

                Requirements:
                - File size: max 2MB
                - Format: JPG or PNG 
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_IMAGE);
    }
} 