package com.lunax.telegramBot.service;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.utils.IpfsUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.ApiRequestUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MemecoinCreationService {

    @Autowired
    private WalletKeyManager walletKeyManager;


    @Autowired
    private ApiRequestUtils apiRequestUtils;

    public String createMemecoin(Update update, User user, ObjectNode stateData) throws Exception {
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

        // Create metadata
        String imageUri = createImageUri(image);
        if (imageUri == null) {
            throw new RuntimeException("Failed to upload image to IPFS");
        }
        new File(image).delete();

        String metadataUri = createMetadataJson(name, symbol, description, imageUri);
        if (metadataUri == null) {
            throw new RuntimeException("Failed to create metadata JSON");
        }
        MessageUtils.sendMessage(update, "⏳ Uploading metadata to IPFS...");

        // Allow extra time for IPFS gateway propagation
        Thread.sleep(5000);

        // Get the wallet private key
        String walletPrivateKey = walletKeyManager.getPrivateKey(user.getCurrentWallet());
        if (walletPrivateKey == null) {
            throw new RuntimeException("Wallet private key not found");
        }

        // Create the token directly using the API
        CreateMemecoinRequest request = new CreateMemecoinRequest();
        request.setName(name);
        request.setSymbol(symbol);
        request.setDecimals(decimals);
        request.setSupply(supply);
        request.setMetadataUri(metadataUri);
        request.setRevokeMintAuthority(revokeMintAuthority);
        request.setRevokeFreezeAuthority(revokeFreezeAuthority);
        request.setWalletPrivateKey(walletPrivateKey);
        request.setChatId(getChatId(update));

        // Make API request
        ResponseEntity<CreateMemecoinResponse> response = apiRequestUtils.makeRequest(
            "/api/memecoin/create",
            HttpMethod.POST,
            request,
            CreateMemecoinResponse.class
        );

        String tokenAddress = response.getBody().getTokenAddress();

        // Create and save Memecoin entity
        // Account account = new Account(Base58.decode(walletPrivateKey));
        // String publicKey = account.getPublicKey().toString();
        

        String successMessage = String.format(
                "✅ Memecoin created successfully!\n\n" +
                        "Name: %s\n" +
                        "Symbol: %s\n" +
                        "Decimals: %d\n" +
                        "Supply: %s\n" +
                        "Token Address: `%s` (Tap to copy)\n",
                name, symbol, decimals, String.format("%,.0f", supply), tokenAddress);
        MessageUtils.sendMessage(update, successMessage);

        return tokenAddress;
    }

    private static String createImageUri(String filename) {
        String ipfsHash = IpfsUtils.addFile(filename);
        if (ipfsHash != null) {
            return IpfsUtils.getIpfsUrl(ipfsHash);
        } else {
            log.error("Failed to upload image to IPFS: {}", filename);
            return null;
        }
    }

    private static String createMetadataJson(String name, String symbol, String description, String image) {
        String metadataJson = String.format(
                "{"
                        + "\"name\": \"%s\","
                        + "\"symbol\": \"%s\","
                        + "\"description\": \"%s\","
                        + "\"image\": \"%s\""
                        + "}",
                name,
                symbol,
                description,
                image
        );

        byte[] metadataBytes = metadataJson.getBytes(StandardCharsets.UTF_8);
        String ipfsHash = IpfsUtils.addByteArray("metadata.json", metadataBytes);

        if (ipfsHash != null) {
            return IpfsUtils.getIpfsUrl(ipfsHash);
        } else {
            log.error("Failed to upload metadata to IPFS");
            return null;
        }
    }

    // public void addMemecoin(Memecoin memecoin) {
    //     memecoinRepo.save(memecoin);
    // }

    private Long getChatId(Update update) {
        if (update.getMessage() != null) {
            return update.getMessage().getChatId();
        } else if (update.getCallbackQuery() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        throw new RuntimeException("Could not determine chat ID from update");
    }

    @Data
    private static class CreateMemecoinRequest {
        private String name;
        private String symbol;
        private int decimals;
        private double supply;
        private String metadataUri;
        private boolean revokeMintAuthority;
        private boolean revokeFreezeAuthority;
        private String walletPrivateKey;
        private Long chatId;
    }

    @Data
    private static class CreateMemecoinResponse {
        private String tokenAddress;
    }
} 