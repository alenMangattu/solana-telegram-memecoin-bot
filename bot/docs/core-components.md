# Core Components Documentation

## MessageUtils
The `MessageUtils` class is a utility class that handles all Telegram message-related operations. It provides a comprehensive set of methods for sending, editing, and managing messages in the Telegram chat.

### Key Features
- Message sending with various formats (text, images, documents)
- Inline keyboard management
- Message editing and deletion
- Callback query handling
- Custom keyboard support
- Loading message indicators
- Error and success message formatting

### Main Methods

#### Message Sending
```java
public static Message sendMessage(Update update, String text)
public static Message sendMessage(Long chatId, String text)
public static void sendMessage(Update update, String text, InlineKeyboardMarkup replyMarkup)
```

#### Image Handling
```java
public static Message sendImageMessageInlineKeyboard(Update update, String imagePath, String text, ButtonHandler[][] buttonHandlers)
public static String downloadImage(Update update)
```

#### Message Editing
```java
public static void editMessage(Update update, String text, ButtonHandler[][] buttonHandlers)
public static Message editMessageText(Long chatId, Integer messageId, String text, ButtonHandler[][] buttonHandlers)
```

#### Keyboard Management
```java
public static void sendInlineKeyboard(Update update, String text, ButtonHandler[][] buttonHandlers)
public static void sendCustomKeyboard(Update update, String text, String[][] keyboardButtons, boolean oneTimeKeyboard)
```

## ButtonHandler
The `ButtonHandler` interface defines the contract for handling button interactions in the Telegram bot.

### Interface Definition
```java
public interface ButtonHandler {
    String getButtonText();
    String getCallbackData();
    void handleCallback(Update update);
    default void handleMessage(Update update) {}
    default ChatState getNextState() {
        return null;
    }
}
```

### Key Methods
- `getButtonText()`: Returns the text to display on the button
- `getCallbackData()`: Returns the data to be sent when the button is clicked
- `handleCallback(Update update)`: Handles the button click event
- `handleMessage(Update update)`: Optional method to handle message events
- `getNextState()`: Returns the next chat state after button interaction

## StateHandler
The `StateHandler` interface manages the state-based navigation in the bot.

### Interface Definition
```java
public interface StateHandler {
    void executeVoid(Update update);
    
    default void executeVoid(Update update, int page) {
        // Default implementation for pagination
    }
}
```

### Key Methods
- `executeVoid(Update update)`: Executes the state handler for the given update
- `executeVoid(Update update, int page)`: Executes the state handler with pagination support

## Integration with Backend
The components interact with the backend service running on `localhost:3000` through various endpoints. The communication is handled through HTTP requests, and the responses are processed to update the bot's state and user interface.

### Example Flow
1. User interacts with a button
2. `ButtonHandler` processes the interaction
3. `StateHandler` updates the bot's state
4. `MessageUtils` sends appropriate responses
5. Backend service processes the request and returns data
6. Bot updates its UI based on the backend response

## Best Practices
1. Always use `MessageUtils` for sending messages instead of direct bot API calls
2. Implement proper error handling in button callbacks
3. Use state handlers for complex navigation flows
4. Keep button handlers focused and single-purpose
5. Implement proper logging for debugging and monitoring 