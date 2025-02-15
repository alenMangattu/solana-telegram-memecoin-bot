package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import java.io.File;

@Component
public class NotConfirmed implements ButtonHandler {

    @Autowired
    private StateService stateService;

    private final String buttonText;
    private final String callbackData;

    public NotConfirmed(@Value("Restart") String buttonText,
            @Value("not_confirmed") String callbackData) {
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

        // Delete the previous image if it exists
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData != null && stateData.has("imageUrl")) {
            String imagePath = stateData.get("imageUrl").asText();
            File imageFile = new File(imagePath);
            if (imageFile.exists()) {
                imageFile.delete();
            }
        }

        try {
            // Fake it cuz why not
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        MessageUtils.sendMessage(update, "⚠️ Memecoin process restarted.");

        stateService.clearStateData(telegramId);

        // Instead of calling askMemeCoinData directly, we'll just set up the initial
        // state
        stateService.setState(telegramId, ChatState.CREATE_MEMECOIN_START);
        String message = """
                🪙 *Create Your Memecoin* (1/6)

                Let's create your own memecoin! First, what would you like to name your memecoin?

                _Example: Lunar Moon_
                """;
        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_MEMECOIN_NAME);
    }
}