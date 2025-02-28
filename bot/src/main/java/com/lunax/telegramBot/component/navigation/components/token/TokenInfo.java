package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;

import java.util.Map;

@Component
public class TokenInfo implements StateHandler {

    @Autowired
    private SolanaUtils solanaUtils;

    @Override
    public void executeVoid(Update update) {
        String tokenAddress = update.getMessage().getText(); // Implement this method to extract the token address from the update
        if (tokenAddress == null || tokenAddress.isEmpty()) {
            MessageUtils.sendMessage(update, "Invalid token address provided."); // Implement sendMessage to send a message back to the user
            return;
        }

        // Fetch token information
        Map<String, Object> tokenInfo = solanaUtils.getTokenInformation(tokenAddress);

        // Construct a response message
        StringBuilder responseMessage = new StringBuilder();
        responseMessage.append("Token Information:\n");
        responseMessage.append("Name: ").append(tokenInfo.get("name")).append("\n");
        responseMessage.append("Symbol: ").append(tokenInfo.get("symbol")).append("\n");
        responseMessage.append("Decimals: ").append(tokenInfo.get("decimals")).append("\n");
        responseMessage.append("Total Supply: ").append(tokenInfo.get("totalSupply")).append("\n");
        responseMessage.append("Price (USD): ").append(tokenInfo.get("priceUSD")).append("\n");

        // Send the response back to the user
        MessageUtils.sendMessage(update, responseMessage.toString());
    }
}