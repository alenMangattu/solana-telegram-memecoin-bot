# LunaX Telegram Bot

## Overview

LunaX is a powerful Telegram bot built with Java and Spring Framework that provides a seamless interface for interacting with a backend service. The bot features a state-based navigation system, rich message formatting, and robust error handling.

## Features

### Core Features
- State-based navigation system
- Rich message formatting with Markdown support
- Inline keyboard management
- Custom keyboard support
- Image and document handling
- Callback query processing
- Error handling and recovery
- Logging and monitoring

### Technical Features
- Modular architecture
- Spring Boot integration
- RESTful API integration
- Secure authentication
- Rate limiting
- Connection pooling
- Health monitoring

## Architecture

### Components
- `MessageUtils`: Core message handling utility
- `ButtonHandler`: Interface for button interactions
- `StateHandler`: State management interface
- Backend service integration (localhost:3000)

### State Management
- Initial state
- Authentication state
- Main menu state
- Processing state
- Error state

## Getting Started

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- Telegram Bot Token
- Backend service running on localhost:3000

### Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd lunax-bot
   ```

2. Configure the application:
   ```properties
   # application.properties
   telegram.bot.token=your_bot_token
   backend.url=http://localhost:3000
   logging.level.root=INFO
   logging.level.com.lunax.telegramBot=DEBUG
   ```

3. Build the project:
   ```bash
   mvn clean install
   ```

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Development Guide

### Project Structure
```
src/main/java/com/lunax/telegramBot/
├── utils/
│   └── components/
│       ├── MessageUtils.java
│       └── ButtonHandler.java
└── component/
    └── navigation/
        └── components/
            └── StateHandler.java
```

### Adding New Features
1. Create a new button handler
2. Implement the state handler
3. Register the handlers
4. Test the feature

### Testing
```bash
# Run tests
mvn test

# Run with debug mode
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
```

## Documentation

### Available Documentation
- [Core Components](./docs/core-components.md)
- [Backend Integration](./docs/backend-integration.md)
- [Architecture](./docs/architecture.md)
- [Setup Guide](./docs/setup-guide.md)
- [Troubleshooting](./docs/troubleshooting.md)

### Quick Examples

#### Sending a Message
```java
MessageUtils.sendMessage(update, "Hello, welcome to LunaX Bot!");
```

#### Creating a Button Handler
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
        MessageUtils.sendMessage(update, "Welcome to the main menu!");
    }
}
```

## Troubleshooting

### Common Issues
1. Bot not responding
   - Check bot token
   - Verify backend connection
   - Check logs

2. Message delivery issues
   - Verify message formatting
   - Check rate limits
   - Monitor logs

3. State management problems
   - Check state transitions
   - Verify state persistence
   - Monitor logs

For detailed troubleshooting steps, see [Troubleshooting Guide](./docs/troubleshooting.md).

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

### Development Guidelines
- Follow Java coding standards
- Add proper documentation
- Include unit tests
- Update relevant documentation

## Support

### Getting Help
- Check the documentation
- Review existing issues
- Contact the development team
- Check the logs

### Logging
- Application logs: `logs/application.log`
- Backend logs: `backend/logs/server.log`
- System logs: `/var/log/syslog`

## License

This project is licensed under the Non-Commercial License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Telegram Bot API team
- Spring Framework community
- All contributors to this project
