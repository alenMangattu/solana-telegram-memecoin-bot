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
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Comparator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;

public class MessageUtils {

    private static TelegramLongPollingBot botInstance;
    private static String botToken;
    private static final Logger log = LoggerFactory.getLogger(MessageUtils.class);

    public static void setBotInstance(TelegramLongPollingBot bot) {
        botInstance = bot;
        botToken = bot.getBotToken();
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
        if (message == null)
            return;

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
        if (message == null)
            return;

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
        if (message == null)
            return;

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
        if (message == null)
            return;

        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(message.getChatId().toString());
        deleteMessage.setMessageId(message.getMessageId());
        try {
            botInstance.execute(deleteMessage);
        } catch (TelegramApiException e) {
            log.error("Error deleting message: {}", e.getMessage());
        }
    }

    public static String downloadImage(Update update) {
        try {
            String fileId;
            String mimeType = null;

            // Check if the message contains a photo
            if (update.getMessage().hasPhoto()) {
                List<PhotoSize> photos = update.getMessage().getPhoto();
                
                // Get the largest photo (best quality)
                PhotoSize photo = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElseThrow();
                fileId = photo.getFileId();
                mimeType = "image/jpeg";
                
                // Check photo dimensions - must be square and either 512x512 or 1024x1024
                int width = photo.getWidth();
                int height = photo.getHeight();
                boolean isValidSize = (width == 512 && height == 512) || (width == 1024 && height == 1024);
                if (!isValidSize) {
                    return null;
                }
            }
            // Check if the message contains a document (could be GIF or other image types)
            else if (update.getMessage().hasDocument()) {
                log.info("Message contains document");
                var document = update.getMessage().getDocument();
                mimeType = document.getMimeType();

                // Only process if it's an image
                if (mimeType != null && mimeType.startsWith("image/")) {
                    fileId = document.getFileId();
                } else {
                    return null;
                }
            } else {
                return null;
            }

            // Create images directory if it doesn't exist
            File directory = new File("src/main/resources/images");
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                log.info("Directory created: {}", created);
            }

            // Generate random filename with UUID
            String fileName = UUID.randomUUID().toString();
            // Get file from Telegram
            GetFile getFile = new GetFile();
            getFile.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File file = botInstance.execute(getFile);
            String filePath = file.getFilePath();
            log.info("File path from Telegram: {}", filePath);

            // Download the file
            String fileUrl = String.format("https://api.telegram.org/file/bot%s/%s", botToken, filePath);
            log.info("Generated file URL (token hidden): {}", fileUrl.replaceAll(botToken, "BOT_TOKEN"));
            URI uri = URI.create(fileUrl);
            URL url = uri.toURL();

            // If it's already a PNG or GIF, save directly
            if ("image/gif".equals(mimeType)) {
                log.info("Processing GIF file");
                try (InputStream in = url.openStream()) {
                    // For GIFs, check dimensions before saving
                    BufferedImage firstFrame = ImageIO.read(url);
                    if (firstFrame == null) {
                        log.error("Failed to read first frame of GIF");
                        return null;
                    }
                    
                    log.info("GIF dimensions: {}x{}", firstFrame.getWidth(), firstFrame.getHeight());
                    int width = firstFrame.getWidth();
                    int height = firstFrame.getHeight();
                    boolean isValidSize = (width == 512 && height == 512) || (width == 1024 && height == 1024);
                    if (!isValidSize) {
                        log.error("Invalid GIF dimensions: {}x{}. Must be 512x512 or 1024x1024", width, height);
                        return null;
                    }
                    
                    File outputFile = new File(directory, fileName + ".gif");
                    log.info("Saving GIF to: {}", outputFile.getPath());
                    java.nio.file.Files.copy(in, outputFile.toPath());
                    log.info("GIF saved successfully");
                    return outputFile.getPath();
                }
            } else {
                log.info("Processing non-GIF image");
                // For other formats, convert to PNG
                try {
                    BufferedImage image = ImageIO.read(url);
                    if (image != null) {
                        log.info("Image dimensions: {}x{}", image.getWidth(), image.getHeight());
                        // Check dimensions for non-GIF images
                        int width = image.getWidth();
                        int height = image.getHeight();
                        boolean isValidSize = (width == 512 && height == 512) || (width == 1024 && height == 1024);
                        if (!isValidSize) {
                            return null;
                        }
                        
                        File outputFile = new File(directory, fileName + ".png");
                        ImageIO.write(image, "PNG", outputFile);
                        return outputFile.getPath();
                    } else {
                        log.error("Failed to read image data");
                    }
                } catch (IOException e) {
                    return null;
                }
            }

        } catch (TelegramApiException | IOException e) {
            log.error("Error downloading image: {}", e.getMessage(), e);
            return null;
        }

        log.error("Reached end of function without success");
        return null;
    }

    public static void sendFileMessageInlineKeyboard(Update update, String filePath, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null)
            return;

        // Create keyboard markup if button handlers are provided
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
            markup.setKeyboard(buttons);
        }

        // Create file to send
        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(message.getChatId().toString());
        sendDocument.setDocument(new InputFile(new File(filePath)));
        sendDocument.setCaption(text);
        sendDocument.setParseMode("Markdown");
        if (buttonHandlers != null) {
            sendDocument.setReplyMarkup(markup);
        }

        try {
            botInstance.execute(sendDocument);
        } catch (TelegramApiException e) {
            log.error("Error sending file message: {}", e.getMessage());
        }
    }

    public static void sendImageMessageInlineKeyboard(Update update, String imagePath, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null)
            return;

        // Create keyboard markup if button handlers are provided
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
            markup.setKeyboard(buttons);
        }

        try {
            // Check if the path is a URL or local file
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                // For URLs
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(message.getChatId().toString());
                sendPhoto.setPhoto(new InputFile(URI.create(imagePath).toURL().openStream(), "image"));
                sendPhoto.setCaption(text);
                sendPhoto.setParseMode("Markdown");
                if (buttonHandlers != null) {
                    sendPhoto.setReplyMarkup(markup);
                }
                botInstance.execute(sendPhoto);
            } else {
                // For local files
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(message.getChatId().toString());
                sendPhoto.setPhoto(new InputFile(new File(imagePath)));
                sendPhoto.setCaption(text);
                sendPhoto.setParseMode("Markdown");
                if (buttonHandlers != null) {
                    sendPhoto.setReplyMarkup(markup);
                }
                botInstance.execute(sendPhoto);
            }
        } catch (TelegramApiException | IOException e) {
            log.error("Error sending image message: {}", e.getMessage());
        }
    }
}