package com.lunax.telegramBot.controller;

import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.utils.components.MessageUtils;

@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    @PostMapping("/send")
    public void sendMessage(@RequestBody SendMessageRequest request) {
        // Use the chatId directly instead of creating an Update object
        MessageUtils.sendMessage(request.getChatId(), request.getText());
    }

    // Simplified request class
    public static class SendMessageRequest {
        private Long chatId;
        private String text;

        // Getters and Setters
        public Long getChatId() {
            return chatId;
        }

        public void setChatId(Long chatId) {
            this.chatId = chatId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
} 