# Telegram Bot for Solana Memecoins

## Overview

This project is a Telegram bot that allows users to create and manage memecoins on the Solana blockchain. It provides functionalities such as wallet management, token creation, and liquidity addition.

## Features

- Create new Solana wallets
- Mint memecoins with customizable parameters
- Manage token authorities (mint and freeze)
- Add liquidity to tokens
- Retrieve wallet balances

## Technologies Used

- Java
- Spring Framework
- Solana Java SDK
- Telegram Bot API
- Jackson for JSON processing

## Installation

### Prerequisites

- Java 11 or higher
- Maven
- A Solana wallet and access to the Solana cluster

### Steps

1. Clone the repository:
   ```bash
   git clone <THIS-REPO-URL> 
   cd telegram-bot-solana-memecoins
   ```

2. Build the project using Maven:
   ```bash
   mvn clean install
   ```

3. Configure your application properties:
   - Create a `application.properties` file in `src/main/resources/` and set the necessary configurations, such as Solana cluster URL and Telegram bot token.

4. Run the application:
   ```bash
   mvn spring-boot:run
   ```

## Usage

- Start a chat with the bot on Telegram.
- Use commands to create wallets, mint memecoins, and manage liquidity.
- Follow the bot's prompts to complete actions.

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request for any improvements or bug fixes.

## License

This project is licensed under the Non-Commercial License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Thanks to the Solana community for their support and resources.
- Special thanks to the Telegram Bot API documentation for guidance on bot development.
