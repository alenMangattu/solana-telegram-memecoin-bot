# Setup Guide for New Developers

## Quick Start Example

### 1. Basic Message Sending
```java
// Example of sending a simple message
MessageUtils.sendMessage(update, "Hello, welcome to LunaX Bot!");

// Example with inline keyboard
ButtonHandler[][] buttons = new ButtonHandler[][] {
    { new MenuButtonHandler() },
    { new SettingsButtonHandler() }
};
MessageUtils.sendInlineKeyboard(update, "Please select an option:", buttons);
```

### 2. Creating a Button Handler
```java
public class MenuButtonHandler implements ButtonHandler {
    @Override
    public String getButtonText() {
        return "Main Menu";
    }

    @Override
    public String getCallbackData() {
        return "menu";
    }

    @Override
    public void handleCallback(Update update) {
        // Handle menu button click
        MessageUtils.sendMessage(update, "Welcome to the main menu!");
    }

    @Override
    public ChatState getNextState() {
        return ChatState.MAIN_MENU;
    }
}
```

### 3. Implementing a State Handler
```java
public class MainMenuStateHandler implements StateHandler {
    @Override
    public void executeVoid(Update update) {
        // Show main menu options
        ButtonHandler[][] buttons = new ButtonHandler[][] {
            { new ProfileButtonHandler() },
            { new SettingsButtonHandler() }
        };
        MessageUtils.sendInlineKeyboard(update, "Main Menu Options:", buttons);
    }
}
```

## Common Development Tasks

### 1. Adding a New Feature
1. Create a new button handler
2. Implement the state handler
3. Register the handlers in the appropriate place
4. Test the feature

### 2. Debugging Tips
- Use the logging system:
```java
log.debug("Debug message");
log.info("Info message");
log.error("Error message", exception);
```
- Check the backend logs at `localhost:3000/api/logs`
- Use the health check endpoint to verify backend status

### 3. Testing Your Changes
1. Start the backend service
2. Run the bot locally
3. Test the new feature in Telegram
4. Check the logs for any errors

## Common Pitfalls to Avoid

### 1. Message Handling
- Always use `MessageUtils` instead of direct Telegram API calls
- Handle both text and callback query updates
- Check for null messages

### 2. State Management
- Don't forget to update the chat state
- Handle state transitions properly
- Clean up resources when changing states

### 3. Backend Integration
- Always include proper error handling
- Use the correct headers for authentication
- Handle rate limiting appropriately

## Development Workflow

### 1. Local Development
```bash
# Start the backend service
cd backend
npm install
npm run dev

# Start the bot
cd ../bot
mvn clean install
mvn spring-boot:run
```

### 2. Testing Environment
- Use the test bot token
- Connect to the test backend
- Use test data for development

### 3. Production Deployment
- Use production bot token
- Connect to production backend
- Enable all security features

## Useful Commands

### Git Commands
```bash
# Create a new feature branch
git checkout -b feature/your-feature-name

# Commit changes
git add .
git commit -m "Description of changes"

# Push changes
git push origin feature/your-feature-name
```

### Maven Commands
```bash
# Clean and install
mvn clean install

# Run tests
mvn test

# Run the application
mvn spring-boot:run
```

## Getting Help
- Check the existing documentation
- Review the codebase
- Ask team members
- Check the issue tracker
- Review pull requests

## Next Steps
1. Review the core components documentation
2. Understand the backend integration
3. Study the architecture documentation
4. Start with small tasks
5. Ask questions when needed 