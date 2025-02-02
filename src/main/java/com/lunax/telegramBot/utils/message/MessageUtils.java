package com.lunax.telegramBot.utils.message;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MessageUtils {

    private static TelegramLongPollingBot botInstance;

    public static void setBotInstance(TelegramLongPollingBot bot) {
        botInstance = bot;
    }

    public static void sendInlineKeyboard(Message message, String text, ButtonHandler[][] buttonHandlers) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        if (buttonHandlers != null) {
            for (ButtonHandler[] buttonRow : buttonHandlers) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                for (ButtonHandler handler : buttonRow) {
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText(handler.getButtonText());
                    button.setCallbackData(handler.getCallbackData());
                    row.add(button);
                }
                buttons.add(row);
            }
        }

        markup.setKeyboard(buttons);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText(text);
        sendMessage.setReplyMarkup(markup);
        try {
            botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message, {}", e.getMessage());
        }
    }
}
