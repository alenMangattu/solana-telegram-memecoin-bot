package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.InputStreamResource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenInfo implements StateHandler {

    @Autowired
    private SolanaUtils solanaUtils;

    @Override
    public void executeVoid(Update update) {
        String tokenAddress = update.getMessage().getText(); // Extract the token address from the update
        if (tokenAddress == null || tokenAddress.isEmpty()) {
            MessageUtils.sendMessage(update, "Invalid token address provided."); // Send error message
            return;
        }

        try {
            // Fetch token information
            Map<String, Object> tokenInfo = solanaUtils.getTokenInformation(tokenAddress);
            if (tokenInfo == null || tokenInfo.isEmpty()) {
                MessageUtils.sendMessage(update, "Token not found. Please check the token address and try again.");
                return; // Stop further processing if token is not found
            }

            log.info("Token Info: {}", tokenInfo);
            // Construct a response message for token information
            StringBuilder responseMessage = new StringBuilder();
            responseMessage.append("Token Information:\n");
            responseMessage.append("Name: ").append(tokenInfo.get("name")).append("\n");
            responseMessage.append("Symbol: ").append(tokenInfo.get("symbol")).append("\n");
            responseMessage.append("Decimals: ").append(tokenInfo.get("decimals")).append("\n");
            responseMessage.append("Total Supply: ").append(tokenInfo.get("totalSupply")).append("\n");
            responseMessage.append("Price (USD): ").append(tokenInfo.get("priceUSD")).append("\n");

            // Define time frequency and time before for historical data
            String timeFrequency = "15m"; // Example: 12 hours
            String timeBefore = "1D"; // Example: 3 months

            // Fetch historical data
            JsonNode historicalData = solanaUtils.getHistoricalData(tokenAddress, timeFrequency, timeBefore);
            if (historicalData == null) {
                MessageUtils.sendMessage(update, "Failed to retrieve historical data for the token.");
                return; // Stop further processing if historical data retrieval fails
            }

            // Send the historical data to the chart endpoint and get the image
            String tokenTicker = (String) tokenInfo.get("symbol"); // Extract the token symbol

            log.info("Token Ticker: {}", tokenTicker);

            InputStreamResource imageResource = solanaUtils.sendHistoricalDataToChart(tokenAddress, historicalData, timeFrequency, tokenTicker);
            if (imageResource == null) {
                MessageUtils.sendMessage(update, "Failed to retrieve the chart image.");
                return; // Stop further processing if image retrieval fails
            }

            // Save the image to a temp file
            if (imageResource != null) {
                // Create a temporary file in resources/images/chart directory
                String tempFileName = "chart_" + UUID.randomUUID().toString() + ".png";
                File tempFile = new File("src/main/resources/images/chart/" + tempFileName);
                try (InputStream inputStream = imageResource.getInputStream();
                        FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    MessageUtils.sendMessage(update, "Failed to save the chart image.");
                    return;
                }

                // Send the image to the user
                MessageUtils.sendImageMessageInlineKeyboard(update, tempFile.getAbsolutePath(), "Historical Data Chart",
                        null);

                // Delete the temp file after sending
                tempFile.deleteOnExit();
            } else {
                MessageUtils.sendMessage(update, "Failed to retrieve the chart image.");
            }
        } catch (RuntimeException e) {
            // Handle the case where the token is not found
            MessageUtils.sendMessage(update, "An error occurred while fetching token information: " + e.getMessage());
        }
    }
}
