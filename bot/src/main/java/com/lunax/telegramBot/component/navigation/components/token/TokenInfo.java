package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.Buttons.Token.TopHoldersButton;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.service.StateService;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.component.Buttons.Token.BuyButton;
import com.lunax.telegramBot.component.Buttons.Token.SellButton;
import com.lunax.telegramBot.component.Buttons.Token.TimeFrequencyButton;
import com.lunax.telegramBot.component.Buttons.Token.TimePeriodButton;

// New button autowiring for time periods and frequencies
@Slf4j
@Component
public class TokenInfo implements StateHandler {

        @Autowired
        private SolanaUtils solanaUtils;

        @Autowired
        private StateService stateService;

        @Lazy
        @Autowired
        private TimePeriodButton timePeriod1D;

        @Lazy
        @Autowired
        private TimePeriodButton timePeriod15D;

        @Lazy
        @Autowired
        private TimePeriodButton timePeriod6M;

        @Lazy
        @Autowired
        private TimeFrequencyButton timeFrequency15M;

        @Lazy
        @Autowired
        private TimeFrequencyButton timeFrequency6H;

        @Lazy
        @Autowired
        private TimeFrequencyButton timeFrequency1D;

        @Lazy
        @Autowired
        private BuyButton buyButton;

        @Lazy
        @Autowired
        private SellButton sellButton;

        @Lazy
        @Autowired
        private TopHoldersButton topHoldersButton;

        @SuppressWarnings("null")
        @Override
        public void executeVoid(Update update) {
                String tokenAddress = null;
                Long userId = null;

                // Check if this is a button callback or direct message
                if (update.hasCallbackQuery()) {
                        userId = update.getCallbackQuery().getFrom().getId();
                } else if (update.hasMessage()) {
                        userId = update.getMessage().getFrom().getId();
                        tokenAddress = update.getMessage().getText();
                } else {
                        // Invalid update
                        return;
                }

                // Get state data
                ObjectNode stateData = stateService.getStateData(userId);

                // If no state data, initialize it
                if (stateData == null) {
                        stateService.initState(userId);
                        stateData = stateService.getStateData(userId);
                }

                // For button callbacks, retrieve token address from state
                if (tokenAddress == null && update.hasCallbackQuery() && stateData != null
                                && stateData.has("tokenAddress")) {
                        tokenAddress = stateData.get("tokenAddress").asText();
                }

                // Validate token address
                if (tokenAddress == null || tokenAddress.isEmpty()) {
                        MessageUtils.sendMessage(update, "Invalid token address provided. Please enter a valid Solana token address.");
                        return;
                }
                
                // Check for valid Solana address format (Base58 encoded, proper length)
                if (!isValidSolanaAddress(tokenAddress)) {
                        MessageUtils.sendMessage(update, "❌ Invalid token address format. A Solana token address should be a Base58 encoded string typically 32-44 characters long.\n\nPlease enter a valid Solana token address.");
                        return;
                }

                // Send a loading message and store its ID
                Message loadingMessage = MessageUtils.sendLoadingMessage(update);

                try {
                        // Store token address in user state
                        stateData.put("tokenAddress", tokenAddress);
                        stateService.setStateData(userId, stateData);

                        // Fetch token information
                        Map<String, Object> tokenInfo = solanaUtils.getTokenInformation(tokenAddress);
                        if (tokenInfo == null || tokenInfo.isEmpty()) {
                                MessageUtils.editMessage(update,
                                                "Token not found. Please check the token address and try again.", null);
                                return;
                        }

                        log.info("Token Info: {}", tokenInfo);

                        // Create a structured JSON object for token information
                        ObjectNode tokenInfoJson = new ObjectMapper().createObjectNode();
                        tokenInfoJson.put("tokenName", tokenInfo.getOrDefault("name", "Unknown").toString());
                        tokenInfoJson.put("tokenSymbol", tokenInfo.getOrDefault("symbol", "Unknown").toString());
                        tokenInfoJson.put("tokenAddress", tokenAddress);
                        tokenInfoJson.put("tokenDecimals", (Integer) tokenInfo.getOrDefault("decimals", 0));
                        tokenInfoJson.put("tokenTotalSupply", (Long) tokenInfo.getOrDefault("totalSupply", 0L));

                        // Retrieve the last price from the BirdEye response
                        Double price = tokenInfo.containsKey("price")
                                        ? (tokenInfo.get("price") instanceof Number
                                            ? ((Number) tokenInfo.get("price")).doubleValue()
                                            : 0.0)
                                        : 0.0;

                        // Store price as a String to maintain exact representation
                        String priceUSD = String.valueOf(price);

                        // Store the structured JSON object in the state
                        tokenInfoJson.put("priceUSD", priceUSD); // Store the price as a String
                        stateData.set("tokenInfo", tokenInfoJson);
                        stateService.setStateData(userId, stateData);

                        // Handle all possible values with type checking and defaults
                        String name = tokenInfo.getOrDefault("name", "Unknown").toString();
                        String symbol = tokenInfo.getOrDefault("symbol", "Unknown").toString();
                        Integer decimals = tokenInfo.containsKey("decimals")
                                        ? (tokenInfo.get("decimals") instanceof Number
                                                        ? ((Number) tokenInfo.get("decimals")).intValue()
                                                        : 0)
                                        : 0;

                        // Handle numeric values safely
                        Long totalSupply = tokenInfo.containsKey("totalSupply")
                                        ? (tokenInfo.get("totalSupply") instanceof Number
                                                        ? ((Number) tokenInfo.get("totalSupply")).longValue()
                                                        : 0L)
                                        : 0L;

                        // Raydium pool info
                        Double tvl = tokenInfo.containsKey("tvl") ? (tokenInfo.get("tvl") instanceof Number
                                        ? ((Number) tokenInfo.get("tvl")).doubleValue()
                                        : 0.0) : 0.0;

                        Double volume24h = tokenInfo.containsKey("24hVolume")
                                        ? (tokenInfo.get("24hVolume") instanceof Number
                                                        ? ((Number) tokenInfo.get("24hVolume")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        Double volume7d = tokenInfo.containsKey("7dVolume")
                                        ? (tokenInfo.get("7dVolume") instanceof Number
                                                        ? ((Number) tokenInfo.get("7dVolume")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        Double volume30d = tokenInfo.containsKey("30dVolume")
                                        ? (tokenInfo.get("30dVolume") instanceof Number
                                                        ? ((Number) tokenInfo.get("30dVolume")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        Double apr24h = tokenInfo.containsKey("24hApr") ? (tokenInfo.get("24hApr") instanceof Number
                                        ? ((Number) tokenInfo.get("24hApr")).doubleValue()
                                        : 0.0) : 0.0;

                        Double feeRate = tokenInfo.containsKey("feeRate") ? (tokenInfo.get("feeRate") instanceof Number
                                        ? ((Number) tokenInfo.get("feeRate")).doubleValue()
                                        : 0.0) : 0.0;

                        Double priceMin24h = tokenInfo.containsKey("24hPriceMin")
                                        ? (tokenInfo.get("24hPriceMin") instanceof Number
                                                        ? ((Number) tokenInfo.get("24hPriceMin")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        Double priceMax24h = tokenInfo.containsKey("24hPriceMax")
                                        ? (tokenInfo.get("24hPriceMax") instanceof Number
                                                        ? ((Number) tokenInfo.get("24hPriceMax")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        String poolId = tokenInfo.containsKey("poolId") ? tokenInfo.get("poolId").toString() : "N/A";
                        String pairedWith = tokenInfo.containsKey("pairedWithSymbol")
                                        ? tokenInfo.get("pairedWithSymbol").toString()
                                        : "N/A";
                        Double burnPercent = tokenInfo.containsKey("burnPercent")
                                        ? (tokenInfo.get("burnPercent") instanceof Number
                                                        ? ((Number) tokenInfo.get("burnPercent")).doubleValue()
                                                        : 0.0)
                                        : 0.0;

                        // Define time frequency and time before for historical data
                        String timeFrequency = "15m"; // Default: 15 minutes
                        String timeBefore = "1D"; // Default: 1 day

                        // Check if we already have time parameters in state
                        if (stateData.has("timeFrequency")) {
                                timeFrequency = stateData.get("timeFrequency").asText();
                        }
                        if (stateData.has("timeBefore")) {
                                timeBefore = stateData.get("timeBefore").asText();
                        }

                        // Read parameters from state, no need to modify them here as the button
                        // handlers do that
                        if (update.hasCallbackQuery() && update.getCallbackQuery().getData().startsWith("time_")) {
                                // For time period and frequency buttons, we've already updated the state in the
                                // button handlers
                                // Just make sure we have the latest state data
                                userId = update.getCallbackQuery().getFrom().getId();
                                stateData = stateService.getStateData(userId);

                                if (stateData != null && stateData.has("tokenAddress")) {
                                        tokenAddress = stateData.get("tokenAddress").asText();
                                        if (stateData.has("timeFrequency")) {
                                                timeFrequency = stateData.get("timeFrequency").asText();
                                        }
                                        if (stateData.has("timeBefore")) {
                                                timeBefore = stateData.get("timeBefore").asText();
                                        }
                                }
                        }

                        // Store in state data
                        stateData.put("timeFrequency", timeFrequency);
                        stateData.put("timeBefore", timeBefore);

                        // Fetch historical data
                        JsonNode historicalData = solanaUtils.getHistoricalData(tokenAddress, timeFrequency,
                                        timeBefore);
                        if (historicalData == null) {
                                MessageUtils.sendMessage(update, "Failed to retrieve historical data for the token.");
                                return; // Stop further processing if historical data retrieval fails
                        }

                        // Extract the most recent price from historical data
                        String updatedPriceUSD = priceUSD; // Default to the previously set price
                        log.info("Historical data: {}", historicalData);
                        
                        // Updated condition to match the actual structure of the JSON response
                        if (historicalData.has("data") && 
                            historicalData.get("data").has("items") && 
                            historicalData.get("data").get("items").isArray() && 
                            historicalData.get("data").get("items").size() > 0) {
                            
                            JsonNode dataArray = historicalData.get("data").get("items");
                            // Get the last element which should be the most recent price
                            JsonNode lastDataPoint = dataArray.get(dataArray.size() - 1);
                            if (lastDataPoint.has("value")) {
                                updatedPriceUSD = lastDataPoint.get("value").asText();
                                
                                // Log the updated price
                                log.info("Extracted last price from BirdEye response: {}", updatedPriceUSD);
                                
                                // Update the tokenInfo in state with the correct price
                                tokenInfoJson.put("priceUSD", updatedPriceUSD);
                                stateData.set("tokenInfo", tokenInfoJson);
                                stateService.setStateData(userId, stateData);
                                
                                // Also update the local variable for use in the message
                                priceUSD = updatedPriceUSD;
                            }
                        } else {
                            log.error("Historical data structure unexpected: {}", historicalData);
                        }

                        // Send the historical data to the chart endpoint and get the image
                        String tokenTicker = symbol; // Use the retrieved symbol

                        log.info("Token Ticker: {}", tokenTicker);
                        log.info("Updated Price USD: {}", priceUSD);

                        InputStreamResource imageResource = solanaUtils.sendHistoricalDataToChart(tokenAddress,
                                        historicalData, timeFrequency, tokenTicker);
                        if (imageResource == null) {
                                MessageUtils.sendMessage(update, "Failed to retrieve the chart image.");
                                return; // Stop further processing if image retrieval fails
                        }

                        // Save the image to a temp file
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

                        // After saving the image to a temp file, store its path in the state
                        stateData.put("tokenChartImagePath", tempFile.getAbsolutePath());

                        // Update the messageWithTokenInfo to remove the creation date
                        String messageWithTokenInfo;
                        
                        // For small numbers (like 0.0003), use scientific notation in the display
                        double priceValue = Double.parseDouble(priceUSD);
                        String formattedPrice;
                        
                        if (priceValue < 0.001) {
                            // For very small numbers, format with 8 decimal places
                            formattedPrice = String.format("%.8f", priceValue);
                        } else if (priceValue < 1.0) {
                            // For numbers between 0.001 and 1.0, format with 6 decimal places
                            formattedPrice = String.format("%.6f", priceValue);
                        } else {
                            // For larger numbers, format with 2 decimal places
                            formattedPrice = String.format("%.2f", priceValue);
                        }
                        
                        messageWithTokenInfo = String.format("""
                                        📊 *Token Information: %s (%s)*

                                        💰 *Basic Information*
                                        • Decimals: %d
                                        • Total Supply: %,d
                                        • Current Price: $%s

                                        🔄 *Liquidity Pool Info*
                                        • Pool ID: %s
                                        • Paired With: %s
                                        • TVL: $%,.2f
                                        • Fee Rate: %.2f%%
                                        • Burn Percent: %.2f%%

                                        📈 *Trading Metrics*
                                        • 24h Volume: $%,.2f
                                        • 7d Volume: $%,.2f
                                        • 30d Volume: $%,.2f
                                        • 24h APR: %.2f%%
                                        • 24h Price Range: $%.6f - $%.6f
                                        """,
                                        name,
                                        symbol,
                                        decimals,
                                        totalSupply,
                                        formattedPrice, // Use the formatted price
                                        poolId,
                                        pairedWith,
                                        tvl,
                                        feeRate * 100, // Convert to percentage
                                        burnPercent,
                                        volume24h,
                                        volume7d,
                                        volume30d,
                                        apr24h,
                                        priceMin24h,
                                        priceMax24h);

                        // Store token info message in user state
                        stateData.put("tokenInfoMessage", messageWithTokenInfo);

                        // Create keyboard with multiple rows
                        ButtonHandler[][] keyboard = {
                                        { timePeriod1D, timePeriod15D, timePeriod6M },
                                        { timeFrequency15M, timeFrequency6H, timeFrequency1D },
                                        { topHoldersButton }, { buyButton, sellButton }
                        };

                        // Add a hint to the message
                        String hint = "Hint: This is the available time periods (e.g., 1D, 15D, 6M) and time frequencies (e.g., 15m, 6H, 1D) you can choose from.";

                        MessageUtils.deleteMessage(loadingMessage);
                        // Use the new method to edit the loading message with the token information,
                        // image, and keyboard
                        MessageUtils.sendImageMessageInlineKeyboard(update, tempFile.getAbsolutePath(),
                                        messageWithTokenInfo + "\n\n" + hint, keyboard);

                        // Set the state to TOKEN_INFO_PAGE after sending the message
                        stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
                        stateService.setStateData(userId, stateData);

                        // Delete the temp file after sending
                        tempFile.deleteOnExit();

                } catch (RuntimeException e) {
                        // Handle the case where the token is not found
                        MessageUtils.editMessage(update,
                                        "An error occurred while fetching token information: " + e.getMessage(), null);
                }
        }

        /**
         * Validates if a string is a proper Solana address
         * @param address The address to validate
         * @return true if valid, false otherwise
         */
        private boolean isValidSolanaAddress(String address) {
                // Basic validation for Solana addresses
                if (address == null || address.isEmpty()) {
                        return false;
                }
                
                // Check if it looks like an @ mention or command
                if (address.contains("@") || address.contains("/")) {
                        return false;
                }
                
                // Check length (typical Solana addresses are 32-44 chars)
                if (address.length() < 32 || address.length() > 44) {
                        return false;
                }
                
                // Check if it uses only Base58 characters
                String base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
                for (char c : address.toCharArray()) {
                        if (base58Chars.indexOf(c) == -1) {
                                return false;
                        }
                }
                
                return true;
        }
}