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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

public class MessageUtils {

    private static TelegramLongPollingBot botInstance;
    private static String botToken;
    private static final Logger log = LoggerFactory.getLogger(MessageUtils.class);

    public static void setBotInstance(TelegramLongPollingBot bot) {
        botInstance = bot;
        botToken = bot.getBotToken();
    }

    public static Message getMessage(Update update) {
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

    public static void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text(text)
                    .parseMode(ParseMode.MARKDOWN)
                    .build();

            botInstance.execute(message);
        } catch (Exception e) {
            log.error("Error sending message: ", e);
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

        try {
            botInstance.execute(editMessage);
        } catch (TelegramApiException e) {
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

    public static String downloadImage(Update update) throws TelegramApiException {
        String fileId = getFileIdFromUpdate(update);
        if (fileId == null) {
            return null;
        }

        String mimeType = "image/jpeg"; // Default mime type for photos
        String fileUrl = String.format("https://api.telegram.org/file/bot%s/%s", botToken, getFilePath(fileId));
        return downloadImageFromUrl(fileUrl, mimeType);
    }

    private static String getFileIdFromUpdate(Update update) {
        if (update.getMessage().hasPhoto()) {
            List<PhotoSize> photos = update.getMessage().getPhoto();
            // Get the largest photo (best quality)
            PhotoSize photo = photos.stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElseThrow();
            return photo.getFileId();
        } else if (update.getMessage().hasDocument()) {
            log.info("Message contains document");
            var document = update.getMessage().getDocument();
            String mimeType = document.getMimeType();
            // Only process if it's an image
            if (mimeType != null && mimeType.startsWith("image/")) {
                return document.getFileId();
            }
        }
        return null;
    }

    private static String getFilePath(String fileId) {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        org.telegram.telegrambots.meta.api.objects.File file;
        try {
            file = botInstance.execute(getFile);
        } catch (TelegramApiException e) {
            // TODO Auto-generated catch block
            log.error("Failed to get file path for fileId {}: {}", fileId, e.getMessage());
            return null; // Return null or handle the error appropriately
        }
        return file.getFilePath();
    }

    public static String downloadImageFromUrl(String fileUrl, String mimeType) {
        try {
            // Create images directory if it doesn't exist
            File directory = new File("src/main/resources/images");
            if (!directory.exists()) {
                boolean created = directory.mkdirs();
                log.info("Directory created: {}", created);
            }

            // Generate random filename with UUID
            String fileName = UUID.randomUUID().toString();
            URI uri = URI.create(fileUrl);
            URL url = uri.toURL();

            // Set up a trust manager that accepts all certificates for ipfs.io
            if (url.getHost().equals("ipfs.io")) {
                TrustManager[] trustAllCerts = new TrustManager[] {
                        new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }

                            public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                                    String authType) {
                            }

                            public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                                    String authType) {
                            }
                        }
                };

                try {
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, trustAllCerts, new java.security.SecureRandom());
                    HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                } catch (KeyManagementException | NoSuchAlgorithmException e) {
                    log.error("Error setting up SSL context: {}", e.getMessage());
                }
            }

            // Open connection
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            // Disable hostname verification for ipfs.io
            if (url.getHost().equals("ipfs.io")) {
                connection.setHostnameVerifier((hostname, session) -> true);
            }

            // Download the image directly
            try (InputStream in = connection.getInputStream()) {
                File outputFile = new File(directory, fileName + getFileExtension(mimeType));
                log.info("Saving image to: {}", outputFile.getPath());
                java.nio.file.Files.copy(in, outputFile.toPath());
                log.info("Image saved successfully");
                return directory.getPath() + "/" + outputFile.getName();
            }

        } catch (IOException e) {
            log.error("Error downloading image: {}", e.getMessage(), e);
            return null;
        }
    }

    private static String getFileExtension(String mimeType) {
        switch (mimeType) {
            case "image/jpeg":
                return ".jpg";
            case "image/png":
                return ".png";
            case "image/gif":
                return ".gif";
            default:
                return "";
        }
    }

    public static void sendFileMessageInlineKeyboard(Update update, String filePath, String text,
            ButtonHandler[][] buttonHandlers) {
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

    public static void sendImageMessageInlineKeyboard(Update update, String imagePath, String text,
            ButtonHandler[][] buttonHandlers) {
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
            // Check if the path is a URL
            if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
                // Use the existing method to download the image
                String downloadedImagePath = downloadImageFromUrl(imagePath, "image/jpeg");
                if (downloadedImagePath == null) {
                    log.error("Failed to download image from URL: {}", imagePath);
                    return;
                }

                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(message.getChatId().toString());
                sendPhoto.setPhoto(new InputFile(new File(downloadedImagePath)));
                sendPhoto.setCaption(text);
                sendPhoto.setParseMode("Markdown");
                if (buttonHandlers != null) {
                    sendPhoto.setReplyMarkup(markup);
                }
                botInstance.execute(sendPhoto);

                // Delete the downloaded image after sending
                new File(downloadedImagePath).delete();
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
        } catch (TelegramApiException e) {
            log.error("Error sending image message: {}", e.getMessage());
        }
    }

    public static void sendMessage(Update update, String text) {
        Long chatId = null;
        if (update.hasMessage()) {
            chatId = update.getMessage().getChatId();
        } else if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
        }
        if (chatId != null) {
            sendMessage(chatId, text);
        }
    }

    public static void editMessageText(Update update, String text) {
        Long chatId = null;
        Integer messageId = null;
        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            messageId = update.getCallbackQuery().getMessage().getMessageId();
        }
        if (chatId != null && messageId != null) {
            // Check if the new text is different from the current message text or caption
            Message message = getMessage(update);
            String currentText = message.getText();
            String currentCaption = message.getCaption();

            // Compare with either text or caption
            if ((currentText == null || !currentText.equals(text)) &&
                    (currentCaption == null || !currentCaption.equals(text))) {
                MessageUtils.editMessage(update, text, null);
            } else {
                log.warn("Edit skipped: new text is the same as the current message text/caption.");
            }
        }
    }

    // Remove the TODO comment and implement proper error handling
    private static void handleImageDownloadError(Exception e) {
        log.error("Error downloading image: {}", e.getMessage());
        throw new RuntimeException("Failed to download image", e);
    }

    public static void editMessageInlineKeyboard(Update update, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null)
            return;

        // TODO: check if the text is the same as the current message text

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

        try {
            botInstance.execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Error editing message: {}", e.getMessage());
        }
    }

    public static void sendCustomKeyboard(Update update, String text, String[][] keyboardButtons,
            boolean oneTimeKeyboard) {
        Message message = getMessage(update);
        if (message == null)
            return;

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        if (keyboardButtons != null) {
            for (String[] buttonRow : keyboardButtons) {
                KeyboardRow row = new KeyboardRow();
                for (String buttonText : buttonRow) {
                    KeyboardButton button = new KeyboardButton(buttonText);
                    row.add(button);
                }
                keyboard.add(row);
            }
        }

        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true); // Optional: resize the keyboard
        keyboardMarkup.setOneTimeKeyboard(oneTimeKeyboard); // Set based on the parameter

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());

        // Set text only if it's not null
        if (text != null) {
            sendMessage.setText(text);
        } else {
            sendMessage.setText(""); // Set to empty string if text is null
        }

        sendMessage.setParseMode("Markdown");
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyMarkup(keyboardMarkup);

        try {
            botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending custom keyboard: {}", e.getMessage());
        }
    }
}