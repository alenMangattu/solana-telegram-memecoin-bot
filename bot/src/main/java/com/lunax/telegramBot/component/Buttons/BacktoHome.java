package com.lunax.telegramBot.component.Buttons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BacktoHome implements ButtonHandler {

    @Autowired
    private StateService stateService;

    @Autowired
    private Home home;

    @Override
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        
        // Delete the current message
        if (update.hasCallbackQuery()) {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            try {
                MessageUtils.getBotInstance().execute(deleteMessage);
            } catch (TelegramApiException e) {
                log.error("Error deleting message: {}", e.getMessage());
            }
        }

        // Delete keyboard message if it exists in state data
        ObjectNode stateData = stateService.getStateData(telegramId);
        if (stateData != null && stateData.has("tokenKeyboardMessageId")) {
            Integer keyboardMessageId = stateData.get("tokenKeyboardMessageId").asInt();
            DeleteMessage deleteKeyboardMessage = new DeleteMessage();
            deleteKeyboardMessage.setChatId(chatId.toString());
            deleteKeyboardMessage.setMessageId(keyboardMessageId);
            try {
                MessageUtils.getBotInstance().execute(deleteKeyboardMessage);
            } catch (TelegramApiException e) {
                log.error("Error deleting keyboard message: {}", e.getMessage());
            }
        }

        // Set state to IDLE and clear state data
        stateService.setState(telegramId, ChatState.IDLE);
        stateService.clearStateData(telegramId);

        // Send the home page directly
        home.executeVoid(update);
    }

    @Override
    public String getButtonText() {
        return "Back to Home";
    }

    @Override
    public String getCallbackData() {
        return "back_to_home";
    }
}
