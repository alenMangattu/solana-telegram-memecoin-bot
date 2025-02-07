package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.config.SolanaConfig;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.IpfsUtils;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CreateMemeCoin implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private SolanaConfig solanaConfig;

    public CreateMemeCoin(@Value("✅ Confirm Creation") String buttonText, 
                         @Value("confirm_memecoin_creation") String callbackData) {
        this.buttonText = buttonText;
        this.callbackData = callbackData;
    }

    @Override
    public String getButtonText() {
        return buttonText;
    }

    @Override
    public String getCallbackData() {
        return callbackData;
    }

    @Override
    public void handleCallback(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        
        // Verify user is in correct state
        if (stateService.getState(telegramId) != ChatState.AWAITING_MEMECOIN_CONFIRMATION) {
            MessageUtils.sendMessage(update, "❌ Invalid state. Please start over.");
            return;
        }

        ObjectNode stateData = stateService.getStateData(telegramId);
        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getCurrentWallet() == null) {
            MessageUtils.sendMessage(update, "❌ Please create or select a wallet first!");
            return;
        }

        try {
            // Extract memecoin data from state
            String name = stateData.get("name").asText();
            String symbol = stateData.get("symbol").asText();
            int decimals = stateData.get("decimals").asInt();
            double supply = stateData.get("supply").asDouble();
            
            String description = stateData.get("description").asText();
            String image = stateData.get("imageUrl").asText();
            String createOn = "Luanx telegram bot";

            MessageUtils.sendMessage(update, "🏗️ Memecoin building process started...");
           
            // Create metadata
            String imageUri = createImageUri(image);
            new File(image).delete();
            String metadataUri = createMetadataJson(name, symbol, description, imageUri, createOn);
            MessageUtils.sendMessage(update, "⏳ Uploaded metadata to file server...");

            // Allow extra time for IPFS gateway propagation
            Thread.sleep(5000); // wait 5 seconds before initializing metadata

            // Create the memecoin using the instance method
            String tokenAddress = solanaUtils.createMemecoin(
                user.getCurrentWallet(),  // This is a UUID
                name,
                symbol,
                decimals,
                supply,
                metadataUri,
                update
            );

            String successMessage = String.format(
                "✅ Memecoin created successfully!\n\n" +
                "Name: %s\n" +
                "Symbol: %s\n" +
                "Decimals: %d\n" +
                "Supply: %s\n" + // Changed from %.2f to %s to match the type of supply
                "Token Address: `%s` (Tap to copy)\n",
                name, symbol, decimals, String.format("%,.0f", supply), tokenAddress // Correctly format supply as a string
            );
            MessageUtils.sendMessage(update, successMessage);

        } catch (Exception e) {
            MessageUtils.sendMessage(update, "❌ Internal error. Please try again later. (The memcoin might have been created, but the bot failed to send the message so check your memecoin page)");
        } finally {
            stateService.setState(telegramId, ChatState.IDLE);
            stateService.clearStateData(telegramId);
        }
    }

    private static String createImageUri(String filename) {
        // Upload the image file to IPFS
        String ipfsHash = IpfsUtils.addFile(filename);
        
        // Check if the upload was successful
        if (ipfsHash != null) {
            // Return the HTTP URL for the uploaded image
            return IpfsUtils.getIpfsUrl(ipfsHash);
        } else {
            // Handle the case where the upload failed
            log.error("Failed to upload image to IPFS: {}", filename);
            return null; // or throw an exception based on your error handling strategy
        }
    }

    private static String createMetadataJson(String name, String symbol, String description, String image, String createOn) {
        // Create the metadata JSON string
        String metadataJson = String.format(
            "{"
            + "\"name\": \"%s\","
            + "\"symbol\": \"%s\","
            + "\"description\": \"%s\","
            + "\"image\": \"%s\","
            + "\"createdOn\": \"%s\""
            + "}",
            name,
            symbol,
            description,
            image,
            createOn
        );

        // Upload the metadata JSON directly to IPFS as a byte array
        byte[] metadataBytes = metadataJson.getBytes(StandardCharsets.UTF_8);
        String ipfsHash = IpfsUtils.addByteArray("metadata.json", metadataBytes);
        
        // Check if the upload was successful
        if (ipfsHash != null) {
            // Return the HTTP URL for the uploaded metadata
            return IpfsUtils.getIpfsUrl(ipfsHash);
        } else {
            log.error("Failed to upload metadata to IPFS");
            return null; // or throw an exception based on your error handling strategy
        }
    }

}
