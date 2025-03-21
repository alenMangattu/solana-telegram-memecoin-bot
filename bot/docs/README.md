# LunaX Telegram Bot Documentation

## Overview
This documentation provides detailed information about the LunaX Telegram Bot implementation, its components, and how it interacts with the backend system.

## Table of Contents
1. [Core Components](./core-components.md)
   - MessageUtils
   - ButtonHandler
   - StateHandler
2. [Backend Integration](./backend-integration.md)
3. [API Endpoints](./api-endpoints.md)
4. [Architecture](./architecture.md)

## Quick Start
The LunaX Telegram Bot is built using the Telegram Bot API and integrates with a backend service running on `localhost:3000`. The bot handles various user interactions through a state-based navigation system and provides a rich set of messaging capabilities.

## Prerequisites
- Java 11 or higher
- Maven for dependency management
- Telegram Bot Token
- Backend service running on localhost:3000

## Configuration
The bot requires the following configuration:
- Telegram Bot Token
- Backend API URL (default: http://localhost:3000)
- Database configuration (if applicable)

## Getting Started
1. Clone the repository
2. Configure your bot token
3. Start the backend service
4. Run the bot application

For detailed setup instructions, please refer to the [Setup Guide](./setup-guide.md). 