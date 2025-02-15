package com.lunax.telegramBot.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.lunax.telegramBot.utils.components.MessageUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@Slf4j
@RestController
@RequestMapping("/api/telegram")
public class TelegramController {

    @Autowired
    private HttpServletRequest request;

    @PostMapping("/send")
    public ResponseEntity<Void> sendMessage(@RequestBody TelegramMessage message) {
        log.info("=== Starting /send endpoint processing ===");
        log.info("Request URI: {}", request.getRequestURI());
        log.info("Request method: {}", request.getMethod());
        
        log.info("Request headers:");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            log.info("{}: {}", headerName, request.getHeader(headerName));
        }

        log.info("Message content: chatId={}, text={}", message.getChatId(), message.getText());

        try {
            log.info("Attempting to send message to chat ID: {}", message.getChatId());
            MessageUtils.sendMessage(message.getChatId(), message.getText());
            log.info("Message sent successfully to chat ID: {}", message.getChatId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to send telegram message to chat ID {}", message.getChatId(), e);
            log.error("Exception details:", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            log.info("=== Completed /send endpoint processing ===");
        }
    }

    @Data
    public static class TelegramMessage {
        private Long chatId;
        private String text;
    }
} 