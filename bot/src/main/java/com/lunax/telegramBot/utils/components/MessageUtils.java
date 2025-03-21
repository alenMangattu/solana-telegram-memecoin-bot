package com.lunax.telegramBot.utils.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

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
import java.util.HashMap;
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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;

public class MessageUtils {

    private static TelegramLongPollingBot botInstance;
    private static String botToken;
    private static final Logger log = LoggerFactory.getLogger(MessageUtils.class);
    private static final Map<String, BiConsumer<Update, java.util.regex.Matcher>> callbackHandlers = new HashMap<>();
    private static final Map<String, java.util.function.Consumer<Update>> callbackQueryHandlers = new HashMap<>();

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

    /**
     * Sends an image message with inline keyboard built from a ButtonHandler[][] format
     */
    public static Message sendImageMessageInlineKeyboard(Update update, String imagePath, String text, ButtonHandler[][] buttonHandlers) {
        Message message = getMessage(update);
        if (message == null) return null;

        // Create the SendPhoto object
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(message.getChatId().toString());
        sendPhoto.setPhoto(new InputFile(new File(imagePath)));
        sendPhoto.setCaption(text);
        
        // Create the inline keyboard markup
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
        sendPhoto.setReplyMarkup(markup);
        sendPhoto.setParseMode("Markdown");

        try {
            return botInstance.execute(sendPhoto); // Return the sent message
        } catch (TelegramApiException e) {
            log.error("Error sending image message with inline keyboard: {}", e.getMessage());
            return null;
        }
    }

    public static Message sendMessage(Update update, String text) {
        Message message = getMessage(update);
        if (message == null) return null;

        SendMessage sendMessage = SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(text)
                .parseMode(ParseMode.MARKDOWN) // Optional: set parse mode if needed
                .build();

        try {
            return botInstance.execute(sendMessage); // Return the sent message
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage());
            return null;
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
    /**
     * Edit message text with inline keyboard built from ButtonHandler[][] format
     */
    public static Message editMessageText(Long chatId, Integer messageId, String text, ButtonHandler[][] buttonHandlers) {
        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setText(text);
        editMessage.setParseMode("Markdown");
        
        // Set reply markup if buttons are provided
        if (buttonHandlers != null) {
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

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
            editMessage.setReplyMarkup(markup);
        }
        
        try {
            // Cast the result to Message, or handle the boolean return value
            Object result = botInstance.execute(editMessage);
            if (result instanceof Message) {
                return (Message) result;
            } else {
                // For EditMessageText, a boolean might be returned instead
                log.debug("EditMessageText returned: {}", result);
                return null;
            }
        } catch (TelegramApiException e) {
            log.error("Error editing message: {}", e.getMessage());
            return null;
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

    public static void sendMessage(Update update, String text, InlineKeyboardMarkup replyMarkup) {
        Message message = getMessage(update);
        if (message == null) return;

        SendMessage sendMessage = SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text(text)
                .replyMarkup(replyMarkup)
                .parseMode("Markdown") // Optional: set parse mode if needed
                .build();

        try {
            botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    public static TelegramLongPollingBot getBotInstance() {
        return botInstance;
    }

    public static Message sendLoadingMessage(Update update) {
        Message message = getMessage(update);
        if (message == null) return null;

        SendMessage sendMessage = SendMessage.builder()
                .chatId(message.getChatId().toString())
                .text("⟳ Loading...")
                .build();

        try {
            return botInstance.execute(sendMessage); // Return the sent message
        } catch (TelegramApiException e) {
            log.error("Error sending loading message: {}", e.getMessage());
            return null;
        }
    }

    public static void editImageOnlyTextInlineKeyboard(Update update, String text) {
        Message message = getMessage(update);
        if (message == null) return;

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(message.getChatId().toString());
        editMessage.setMessageId(message.getMessageId());
        editMessage.setText(text);
        editMessage.setParseMode("Markdown");

        // Preserve the existing reply markup (inline keyboard)
        if (message.getReplyMarkup() != null) {
            editMessage.setReplyMarkup(message.getReplyMarkup());
        }

        try {
            botInstance.execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Error editing message: {}", e.getMessage());
        }
    }

    public static void sendAnswerCallbackQuery(Update update, String text, boolean showAlert) {
        try {
            if (update.hasCallbackQuery()) {
                AnswerCallbackQuery answer = new AnswerCallbackQuery();
                answer.setCallbackQueryId(update.getCallbackQuery().getId());
                answer.setText(text);
                answer.setShowAlert(showAlert);
                botInstance.execute(answer);
            }
        } catch (Exception e) {
            log.error("Error sending callback query answer: {}", e.getMessage());
        }
    }

    public static void deleteMessage(Update update, Long messageId) {
        if (update.hasCallbackQuery()) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId.toString());
            deleteMessage.setMessageId(messageId.intValue());
            try {
                botInstance.execute(deleteMessage);
            } catch (TelegramApiException e) {
                log.error("Error deleting message: {}", e.getMessage());
            }
        }
    }

    public static void deleteMessage(Long chatId, Integer messageId) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(messageId);
        
        try {
            botInstance.execute(deleteMessage);
            log.info("Message with ID {} deleted successfully from chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.error("Error deleting message: {}", e.getMessage());
        }
    }

    /**
     * Edits a message by deleting it and sending a new message with an image and inline keyboard.
     * Since Telegram doesn't directly support editing a message to include an image,
     * this method deletes the original message and sends a new one.
     * 
     * @param update The update containing the message to edit
     * @param imagePath The path to the image file to be sent
     * @param text The text to include with the image
     * @param buttonHandlers The button handlers to create the inline keyboard
     * @return The newly sent message
     */
    public static Message editMessageWithImage(Update update, String imagePath, String text, ButtonHandler[][] buttonHandlers) {
        // First, delete the existing message
        Message message = getMessage(update);
        if (message == null) return null;
        
        deleteMessage(message.getChatId(), message.getMessageId());
        
        // Then send a new message with the image and keyboard
        return sendImageMessageInlineKeyboard(update, imagePath, text, buttonHandlers);
    }

    /**
     * Deletes a message based on the provided Message object.
     * 
     * @param message The Message object to delete.
     */
    public static void deleteMessage(Message message) {
        if (message == null) {
            log.warn("Attempted to delete a null message.");
            return;
        }
        
        Long chatId = message.getChatId();
        Integer messageId = message.getMessageId();
        
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId.toString());
        deleteMessage.setMessageId(messageId);
        
        try {
            botInstance.execute(deleteMessage);
            log.info("Message with ID {} deleted successfully from chat {}", messageId, chatId);
        } catch (TelegramApiException e) {
            log.error("Error deleting message: {}", e.getMessage());
        }
    }

    /**
     * Register a callback handler for a pattern
     * @param pattern The regex pattern to match against callback data
     * @param handler The handler to execute when the pattern matches
     */
    public static void registerCallbackHandler(String pattern, BiConsumer<Update, java.util.regex.Matcher> handler) {
        callbackHandlers.put(pattern, handler);
    }

    /**
     * Register a callback query handler for an exact match
     * @param callbackData The exact callback data string to match
     * @param handler The handler to execute when matched
     */
    public static void registerCallbackQueryHandler(String callbackData, java.util.function.Consumer<Update> handler) {
        callbackQueryHandlers.put(callbackData, handler);
    }

    /**
     * Process a callback query by checking for exact matches
     * @param update The update containing the callback query
     * @return true if a handler was found and executed, false otherwise
     */
    public static boolean processCallbackQuery(Update update) {
        if (!update.hasCallbackQuery()) {
            return false;
        }
        
        String callbackData = update.getCallbackQuery().getData();
        
        // First check exact matches in callbackQueryHandlers
        if (callbackQueryHandlers.containsKey(callbackData)) {
            callbackQueryHandlers.get(callbackData).accept(update);
            return true;
        }
        
        // Then check pattern matches in callbackHandlers
        for (Map.Entry<String, BiConsumer<Update, java.util.regex.Matcher>> entry : callbackHandlers.entrySet()) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(entry.getKey());
            java.util.regex.Matcher matcher = pattern.matcher(callbackData);
            
            if (matcher.matches()) {
                entry.getValue().accept(update, matcher);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Edit a message by chat ID and message ID
     */
    public static Message editMessage(Long chatId, Integer messageId, String text, ButtonHandler[][] buttonHandlers) {
        return editMessageText(chatId, messageId, text, buttonHandlers);
    }

    /**
     * Convert ButtonHandler array to InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup convertButtonHandlersToMarkup(ButtonHandler[][] buttonHandlers) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        
        for (ButtonHandler[] row : buttonHandlers) {
            List<InlineKeyboardButton> buttons = new ArrayList<>();
            for (ButtonHandler handler : row) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(handler.getButtonText());
                button.setCallbackData(handler.getCallbackData());
                buttons.add(button);
            }
            rows.add(buttons);
        }
        
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Send an error message with standardized formatting
     */
    public static void sendErrorMessage(Update update, String errorMessage) {
        String formattedMessage = "❌ Error: " + errorMessage;
        sendMessage(update, formattedMessage);
    }

    /**
     * Send a success message with standardized formatting
     */
    public static void sendSuccessMessage(Update update, String successMessage) {
        String formattedMessage = "✅ Success: " + successMessage;
        sendMessage(update, formattedMessage);
    }

    /**
     * Send a message with inline keyboard buttons
     * @param chatId The chat ID
     * @param text The message text
     * @param buttons The inline keyboard buttons
     * @return The sent message
     */
    public static Message sendMessage(Long chatId, String text, List<List<InlineKeyboardButton>> buttons) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(text);
            sendMessage.setParseMode("Markdown");
            
            if (buttons != null && !buttons.isEmpty()) {
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                markup.setKeyboard(buttons);
                sendMessage.setReplyMarkup(markup);
            }
            
            return botInstance.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error sending message: {}", e.getMessage());
            return null;
        }
    }
}