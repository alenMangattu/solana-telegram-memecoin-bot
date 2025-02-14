package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;
import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.service.MemecoinService;

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

    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private MemecoinService memecoinService;

    public CreateMemeCoin(@Value("✅ Confirm Token Creation") String buttonText,
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
            // Validate required fields
            if (!stateData.has("name") || !stateData.has("symbol") ||
                    !stateData.has("decimals") || !stateData.has("supply")) {
                throw new RuntimeException("Missing required memecoin data");
            }

            MessageUtils.editMessageText(update, "🏗️ Starting memecoin creation process...");
            // Extract memecoin data from state
            String name = stateData.get("name").asText();
            String symbol = stateData.get("symbol").asText();
            int decimals = stateData.get("decimals").asInt();
            double supply = stateData.get("supply").asDouble();
            String description = stateData.get("description").asText();
            String image = stateData.get("imageUrl").asText();
            boolean revokeMintAuthority = stateData.get("revokeMintAuthority").asBoolean();
            boolean revokeFreezeAuthority = stateData.get("revokeFreezeAuthority").asBoolean();

            // Validate values
            if (decimals < 0 || decimals > 9) {
                throw new RuntimeException("Invalid decimals value: must be between 0 and 9");
            }
            if (supply <= 0) {
                throw new RuntimeException("Invalid supply: must be greater than 0");
            }

            String confirmationmessage = String.format("""
                    📝 *please confirm your memecoin details:*

                    **name**: %s
                    **symbol**: %s
                    **decimals**: %d
                    **supply**: %s
                    **description**: %s
                    **revoke mint authority**: %s
                    **revoke freeze authority**: %s

                    are you sure you want to create this memecoin?

                    note: this action cannot be undone.
                    """,
                    name,
                    symbol,
                    decimals,
                    String.format("%,.0f", supply),
                    description,
                    revokeMintAuthority ? "yes" : "no",
                    revokeFreezeAuthority ? "yes" : "no");

            MessageUtils.editMessage(update, confirmationmessage, null);
            MessageUtils.sendMessage(update, "🏗️ Starting memecoin creation process...");

            // Create metadata
            String imageUri = createImageUri(image);
            if (imageUri == null) {
                throw new RuntimeException("Failed to upload image to IPFS");
            }
            new File(image).delete();

            String metadataUri = createMetadataJson(name, symbol, description, imageUri, "Lunax telegram bot");
            if (metadataUri == null) {
                throw new RuntimeException("Failed to create metadata JSON");
            }
            MessageUtils.sendMessage(update, "⏳ Uploaded metadata to IPFS...");

            // Allow extra time for IPFS gateway propagation
            Thread.sleep(5000);

            // Get the wallet private key
            String walletPrivateKey = walletKeyManager.getPrivateKey(user.getCurrentWallet());
            if (walletPrivateKey == null) {
                throw new RuntimeException("Wallet private key not found");
            }

            // Create the memecoin
            String tokenAddress = solanaUtils.createMemecoin(
                    walletPrivateKey,
                    name,
                    symbol,
                    decimals,
                    supply,
                    metadataUri,
                    revokeMintAuthority,
                    revokeFreezeAuthority,
                    update);

            String successMessage = String.format(
                    "✅ Memecoin created successfully!\n\n" +
                            "Name: %s\n" +
                            "Symbol: %s\n" +
                            "Decimals: %d\n" +
                            "Supply: %s\n" +
                            "Token Address: `%s` (Tap to copy)\n",
                    name, symbol, decimals, String.format("%,.0f", supply), tokenAddress);
            MessageUtils.sendMessage(update, successMessage);

        } catch (Exception e) {
            log.error("Error creating memecoin: ", e);
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
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

    private static String createMetadataJson(String name, String symbol, String description, String image,
            String createOn) {
        // Create the metadata JSON string
        String metadataJson = String.format(
                "{"
                        + "\"name\": \"%s\","
                        + "\"symbol\": \"%s\","
                        + "\"description\": \"%s\","
                        + "\"image\": \"%s\""
                        // + "\"createdOn\": \"%s\""
                        + "}",
                name,
                symbol,
                description,
                image
        // createOn
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
