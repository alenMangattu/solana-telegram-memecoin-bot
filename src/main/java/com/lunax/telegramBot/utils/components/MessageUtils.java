package com.lunax.telegramBot.utils.components;

import java.util.ArrayList;
import java.util.List;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageUtils {

    private static TelegramLongPollingBot botInstance;

    public static void setBotInstance(TelegramLongPollingBot bot) {
        botInstance = bot;
    }

    private static Message getMessage(Update update) {
        if (update.hasMessage()) {
            return update.getMessage();
        } else if (update.hasCallbackQuery()) {
            MaybeInaccessibleMessage maybeMessage = update.getCallbackQuery().getMessage();
            if (maybeMessage instanceof Message msg) {
                return msg;
            }
        }
        log.error("No valid message found in update");
        return null;
    }

    public static void sendInlineKeyboard(Update update, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null) return;

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
        sendMessage.enableMarkdown(true);
        try {
            botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message, {}", e.getMessage());
        }
    }

    public static void sendMessage(Update update, String text) {
        Message message = getMessage(update);
        if (message == null) return;

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.setText(text);
        sendMessage.enableMarkdown(true);
        try {
            botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public static void editMessage(Update update, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null) return;
        
        EditMessageText editMessage = EditMessageText.builder()
            .chatId(message.getChatId().toString())
            .messageId(message.getMessageId())
            .text(text)
            .parseMode("Markdown")
            .build();

        // Only set reply markup if we have buttons
        if (buttonHandlers != null && buttonHandlers.length > 0) {
            InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().build();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
            
            for (ButtonHandler[] buttonRow : buttonHandlers) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                for (ButtonHandler handler : buttonRow) {
                    InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(handler.getButtonText())
                        .callbackData(handler.getCallbackData())
                        .build();
                    row.add(button);
                }
                buttons.add(row);
            }
            markup.setKeyboard(buttons);
            editMessage.setReplyMarkup(markup);
        }

        editMessage.enableMarkdown(true);
        try {
            botInstance.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            log.error("Error editing message: {}", e.getMessage());
        }
    }

    public static void deleteMessage(Update update) {
        Message message = getMessage(update);
        if (message == null) return;

        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(message.getChatId().toString());
        deleteMessage.setMessageId(message.getMessageId());
        try {
            botInstance.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Error deleting message: {}", e.getMessage());
        }
    }
}