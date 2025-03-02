package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
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

import java.util.Arrays;
import java.util.List;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.model.session.ChatState;

@Slf4j
@Component
public class TokenInfo implements StateHandler {

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private StateService stateService;

    @Autowired
    @Lazy
    private TopHoldersButton topHoldersButton;

    @Override
    public void executeVoid(Update update) {
        String tokenAddress = update.getMessage().getText();
        if (tokenAddress == null || tokenAddress.isEmpty()) {
            MessageUtils.sendMessage(update, "Invalid token address provided.");
            return;
        }

        try {
            Long userId = update.getMessage().getFrom().getId();
            
            // Initialize user state if it doesn't exist
            if (stateService.getStateData(userId) == null) {
                stateService.initState(userId);
            }
            
            // Store token address in user state
            ObjectNode stateData = stateService.getStateData(userId);
            stateData.put("tokenAddress", tokenAddress);
            stateService.setStateData(userId, stateData);
            
            // Fetch token information
            Map<String, Object> tokenInfo = solanaUtils.getTokenInformation(tokenAddress);
            if (tokenInfo == null || tokenInfo.isEmpty()) {
                MessageUtils.sendMessage(update, "Token not found. Please check the token address and try again.");
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
            tokenInfoJson.put("priceUSD", (Double) tokenInfo.getOrDefault("priceUSD", 0.0));

            // Store the structured JSON object in the state
            stateData.set("tokenInfo", tokenInfoJson);
            stateService.setStateData(userId, stateData);
            
            // Handle all possible values with type checking and defaults
            String name = tokenInfo.getOrDefault("name", "Unknown").toString();
            String symbol = tokenInfo.getOrDefault("symbol", "Unknown").toString();
            Integer decimals = tokenInfo.containsKey("decimals") ? 
                    (tokenInfo.get("decimals") instanceof Number ? ((Number)tokenInfo.get("decimals")).intValue() : 0) : 0;
            
            // Handle numeric values safely
            Long totalSupply = tokenInfo.containsKey("totalSupply") ? 
                    (tokenInfo.get("totalSupply") instanceof Number ? ((Number)tokenInfo.get("totalSupply")).longValue() : 0L) : 0L;
            
            Double priceUSD = tokenInfo.containsKey("priceUSD") ? 
                    (tokenInfo.get("priceUSD") instanceof Number ? ((Number)tokenInfo.get("priceUSD")).doubleValue() : 0.0) : 
                    (tokenInfo.containsKey("price") ? 
                            (tokenInfo.get("price") instanceof Number ? ((Number)tokenInfo.get("price")).doubleValue() : 0.0) : 0.0);
            
            // Raydium pool info
            Double tvl = tokenInfo.containsKey("tvl") ? 
                    (tokenInfo.get("tvl") instanceof Number ? ((Number)tokenInfo.get("tvl")).doubleValue() : 0.0) : 0.0;
            
            Double volume24h = tokenInfo.containsKey("24hVolume") ? 
                    (tokenInfo.get("24hVolume") instanceof Number ? ((Number)tokenInfo.get("24hVolume")).doubleValue() : 0.0) : 0.0;
            
            Double volume7d = tokenInfo.containsKey("7dVolume") ? 
                    (tokenInfo.get("7dVolume") instanceof Number ? ((Number)tokenInfo.get("7dVolume")).doubleValue() : 0.0) : 0.0;
            
            Double volume30d = tokenInfo.containsKey("30dVolume") ? 
                    (tokenInfo.get("30dVolume") instanceof Number ? ((Number)tokenInfo.get("30dVolume")).doubleValue() : 0.0) : 0.0;
            
            Double apr24h = tokenInfo.containsKey("24hApr") ? 
                    (tokenInfo.get("24hApr") instanceof Number ? ((Number)tokenInfo.get("24hApr")).doubleValue() : 0.0) : 0.0;
            
            Double feeRate = tokenInfo.containsKey("feeRate") ? 
                    (tokenInfo.get("feeRate") instanceof Number ? ((Number)tokenInfo.get("feeRate")).doubleValue() : 0.0) : 0.0;
            
            Double priceMin24h = tokenInfo.containsKey("24hPriceMin") ? 
                    (tokenInfo.get("24hPriceMin") instanceof Number ? ((Number)tokenInfo.get("24hPriceMin")).doubleValue() : 0.0) : 0.0;
            
            Double priceMax24h = tokenInfo.containsKey("24hPriceMax") ? 
                    (tokenInfo.get("24hPriceMax") instanceof Number ? ((Number)tokenInfo.get("24hPriceMax")).doubleValue() : 0.0) : 0.0;
            
            String poolId = tokenInfo.containsKey("poolId") ? tokenInfo.get("poolId").toString() : "N/A";
            String pairedWith = tokenInfo.containsKey("pairedWithSymbol") ? tokenInfo.get("pairedWithSymbol").toString() : "N/A";
            Double burnPercent = tokenInfo.containsKey("burnPercent") ? 
                    (tokenInfo.get("burnPercent") instanceof Number ? ((Number)tokenInfo.get("burnPercent")).doubleValue() : 0.0) : 0.0;

            // Define time frequency and time before for historical data
            String timeFrequency = "15m"; // Example: 15 minutes
            String timeBefore = "1D"; // Example: 1 day

            // Store in state data
            stateData.put("timeFrequency", timeFrequency);
            stateData.put("timeBefore", timeBefore);

            // Fetch historical data
            JsonNode historicalData = solanaUtils.getHistoricalData(tokenAddress, timeFrequency, timeBefore);
            if (historicalData == null) {
                MessageUtils.sendMessage(update, "Failed to retrieve historical data for the token.");
                return; // Stop further processing if historical data retrieval fails
            }

            // Send the historical data to the chart endpoint and get the image
            String tokenTicker = symbol; // Use the retrieved symbol

            log.info("Token Ticker: {}", tokenTicker);

            InputStreamResource imageResource = solanaUtils.sendHistoricalDataToChart(tokenAddress, historicalData, timeFrequency, tokenTicker);
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

            // Send the image to the user with all token information - comprehensive version
            String messageWithTokenInfo = String.format("""
                    📊 *Token Information: %s (%s)*
                    
                    💰 *Basic Information*
                    • Decimals: %d
                    • Total Supply: %,d
                    • Current Price: $%.6f
                    
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
                    priceUSD,
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
            
            // Create keyboard with Top Holders button
            ButtonHandler[][] keyboard = {
                { topHoldersButton }
            };

            MessageUtils.sendImageMessageInlineKeyboard(update, tempFile.getAbsolutePath(), messageWithTokenInfo, keyboard);

            // Set the state to TOKEN_INFO_PAGE after sending the message
            stateService.setState(userId, ChatState.TOKEN_INFO_PAGE);
            stateService.setStateData(userId, stateData);
            
            // Delete the temp file after sending
            tempFile.deleteOnExit();
        } catch (RuntimeException e) {
            // Handle the case where the token is not found
            MessageUtils.sendMessage(update, "An error occurred while fetching token information: " + e.getMessage());
        }
    }
}