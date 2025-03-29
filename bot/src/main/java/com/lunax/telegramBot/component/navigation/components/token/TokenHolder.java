package com.lunax.telegramBot.component.navigation.components.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.Buttons.Token.ReturnToTokenInfoButton;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.NumberUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TokenHolder implements StateHandler {

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private StateService stateService;
    
    // Create a factory method for buttons instead of autowiring them
    private ButtonHandler createReturnButton() {
        return new ReturnToTokenInfoButton();
    }

    @Override
    public void executeVoid(Update update) {
        Long userId = update.getCallbackQuery().getFrom().getId();
        
        try {
            // Get token information from state
            ObjectNode stateData = stateService.getStateData(userId);
            if (stateData == null) {
                MessageUtils.sendMessage(update, "❌ Error: No state data found.");
                return;
            }
            
            if (!stateData.has("tokenInfo")) {
                MessageUtils.sendMessage(update, "❌ Error: Token information not found.");
                return;
            }
            
            ObjectNode tokenInfo = (ObjectNode) stateData.get("tokenInfo");
            String tokenName = tokenInfo.has("tokenName") ? tokenInfo.get("tokenName").asText() : "Unknown";
            String tokenSymbol = tokenInfo.has("tokenSymbol") ? tokenInfo.get("tokenSymbol").asText() : "Unknown";
            
            // Fetch token price
            Double priceUSD = tokenInfo.has("priceUSD") ? tokenInfo.get("priceUSD").asDouble() : 0.0;
            
            // Get token address from the root of state data, not from tokenInfo
            String tokenAddress = stateData.has("tokenAddress") ? stateData.get("tokenAddress").asText() : null;
            
            if (tokenAddress == null) {
                MessageUtils.sendMessage(update, "❌ Error: Token address not found in state.");
                return;
            }
            
            // Validate token address format
            if (!solanaUtils.isValidSolanaAddress(tokenAddress)) {
                MessageUtils.sendMessage(update, "❌ Error: Invalid token address format.");
                return;
            }
            
            // Fetch top 10 holders
            List<Map<String, Object>> topHolders = solanaUtils.getTopTokenHolders(tokenAddress, 10);
            if (topHolders == null || topHolders.isEmpty()) {
                // Delete previous message and send a new text message
                MessageUtils.deleteMessage(update);
                MessageUtils.sendMessage(update, "No holder information found for this token.");
                return;
            }
            
            // Build the message with top holders information
            StringBuilder message = new StringBuilder();
            message.append(String.format("👥 *Top Holders for %s (%s)*\n\n", tokenName, tokenSymbol));
            
            int rank = 1;
            for (Map<String, Object> holder : topHolders) {
                // Check if this is a disclaimer entry
                if (holder.containsKey("isDisclaimer") && (Boolean)holder.get("isDisclaimer")) {
                    message.append("⚠️ *").append(holder.get("address")).append("*\n");
                    message.append("_Results are approximate based on sampling_\n\n");
                    continue;
                }

                String address = (String) holder.get("address");
                Long amount = (Long) holder.get("amount");
                Double percentage = (Double) holder.get("percentage");
                
                // Calculate the value in USD
                Double valueInUSD = (amount / 1000000.0) * priceUSD;
                String formattedValue = NumberUtils.formatUSD(valueInUSD);
                
                // Debug logging
                log.info("Holder Address: {}, Amount: {}, Price USD: {}, Value in USD: {}", address, amount, priceUSD, valueInUSD);
                
                // Shorten address for display
                String shortAddress = address.substring(0, 4) + "..." + address.substring(address.length() - 4);
                
                // Format the message to include the amount held and its value in USD
                message.append(String.format(
                    "%d. `%s`: %,d tokens (%.2f%%) - %s\n", 
                    rank++, 
                    shortAddress, 
                    amount,
                    percentage,
                    formattedValue
                ));
            }
            
            // If we have the image path from state
            if (stateData.has("tokenChartImagePath")) {
                // Delete the old message
                MessageUtils.deleteMessage(update);
                
                // Create keyboard with Return button - using factory method to avoid circular dependency
                ButtonHandler[][] keyboard = {
                    { createReturnButton() }
                };
                
                // Send a new message with the same image
                String imagePath = stateData.get("tokenChartImagePath").asText();
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    MessageUtils.sendImageMessageInlineKeyboard(update, imagePath, message.toString(), keyboard);
                } else {
                    // If image doesn't exist anymore, just send text
                    MessageUtils.sendInlineKeyboard(update, message.toString(), keyboard);
                }
                
                // Update state to indicate we're viewing top holders
                stateData.put("viewingTopHolders", true);
                stateService.setStateData(userId, stateData);
            } else {
                // If we don't have the image path, just send a text message
                MessageUtils.deleteMessage(update);
                MessageUtils.sendMessage(update, message.toString());
            }
            
        } catch (Exception e) {
            log.error("Error displaying top holders: {}", e.getMessage());
            MessageUtils.sendMessage(update, "An error occurred while fetching top holders: " + e.getMessage());
        }
    }
}
