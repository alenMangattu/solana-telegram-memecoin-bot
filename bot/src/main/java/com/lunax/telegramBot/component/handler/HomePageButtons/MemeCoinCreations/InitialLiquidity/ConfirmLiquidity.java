package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.InitialLiquidity;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.MemecoinCreationService;
import com.lunax.telegramBot.utils.IpfsUtils;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ConfirmLiquidity implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private MemecoinCreationService memecoinService;

    public ConfirmLiquidity(@Value("Launch") String buttonText,
            @Value("confirm_liquidity") String callbackData) {
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
        if (stateService.getState(telegramId) != ChatState.AWAITING_LIQUIDITY_CONFIRMATION) {
            MessageUtils.sendMessage(update, "❌ Invalid state. Please start over.");
            return;
        }

        Message message = MessageUtils.getMessage(update);
        if (message == null) {
            MessageUtils.sendMessage(update, "❌ Error: Callback message is missing.");
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
            // Step 1: Create memecoin
            MessageUtils.sendMessage(update, "🏗️ Creating your memecoin...");
            String tokenAddress = memecoinService.createMemecoin(update, user, stateData);
           
            String fromToken = stateData.get("fromToken").asText();
            String toToken = stateData.get("toToken").asText();
            // Get liquidity amounts
            double toAmount = stateData.get("toAmount").asDouble();
            double fromAmount = stateData.get("fromAmount").asDouble();
            int decimals = stateData.get("decimals").asInt();
            
            String walletPrivateKey = walletKeyManager.getPrivateKey(user.getCurrentWallet());

            // Step 2: Create AMM pool using the actual token address
            MessageUtils.sendMessage(update, "🏊 Creating AMM pool...");
            String poolId = solanaUtils.createAmmPool(
                fromToken,  // Use the fromToken from state
                tokenAddress, // Use the newly created token address
                fromAmount,
                toAmount,
                walletPrivateKey,
                decimals
            );
            MessageUtils.sendMessage(update, "✅ AMM pool created successfully!");

            // Step 3: Add initial liquidity
            MessageUtils.sendMessage(update, "💧 Adding initial liquidity...");
            String liquiditySignature = solanaUtils.addLiquidity(
                tokenAddress,
                fromAmount,
                toAmount,
                walletPrivateKey
            );
            MessageUtils.sendMessage(update, "✅ Liquidity added successfully!");

            // Optional: Handle LP token burning if specified
            if (stateData.has("lpBurnAmount") && stateData.get("lpBurnAmount").asDouble() > 0) {
                double burnPercentage = stateData.get("lpBurnAmount").asDouble();
                if (burnPercentage > 0) {
                    MessageUtils.sendMessage(update, "🔥 Burning LP tokens...");
                    solanaUtils.burnLiquidity(poolId, burnPercentage, walletPrivateKey);
                    MessageUtils.sendMessage(update, String.format("✅ Successfully burned %.1f%% of LP tokens!", burnPercentage));
                }
            }

            // Final success message without market creation
            String successMessage = String.format("""
                    🎉 *Memecoin Launch Complete!*
                    
                    ✅ Token Created
                    ✅ AMM Pool Created
                    ✅ Liquidity Added
                    
                    *Details:*
                    Token Address: `%s`
                    Pool ID: `%s`
                    
                    Your memecoin is now ready! 🚀
                    Use /create_market to enable trading on Raydium later.
                    """, 
                    tokenAddress,
                    poolId
            );
            
            MessageUtils.sendMessage(update, successMessage);

        } catch (Exception e) {
            log.error("Error in liquidity confirmation process: ", e);
            MessageUtils.sendMessage(update, "❌ Error: " + e.getMessage());
        } finally {
            stateService.setState(telegramId, ChatState.IDLE);
            stateService.clearStateData(telegramId);
        }
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

}