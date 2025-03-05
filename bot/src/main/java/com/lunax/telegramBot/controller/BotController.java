package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.component.navigation.components.settings.SettingsImpl;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.IpfsUtils;
import com.lunax.telegramBot.utils.WalletKeyManager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.lunax.telegramBot.component.navigation.components.token.Buy;
import com.lunax.telegramBot.component.navigation.components.token.TokenInfo;
import com.lunax.telegramBot.model.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Slf4j
@Component
public class BotController extends TelegramLongPollingBot {

    private final String botUsername;

    @Autowired
    private Home home;

    @Autowired
    private SettingsImpl settings;

    @Autowired
    private HandlerRegistry handlerRegistry;

    @Autowired
    private AskMemeCoinData createMemeCoin;

    @Autowired
    private StateService stateService;

    @Autowired
    private UserService userService;

    @Autowired
    private AskMemeCoinData askMemeCoinData;

    @Autowired
    private TokenInfo tokenInfo;

    @Autowired
    private AddLiquidity addLiquidity;

    @Autowired
    private WalletKeyManager walletKeyManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Buy buy;

    public BotController(String botUsername, String botToken) {
        super(botToken);
        this.botUsername = botUsername;
        MessageUtils.setBotInstance(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Process each update asynchronously
        CompletableFuture.runAsync(() -> {
            if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                log.info("Received callback data: {}", callbackData);
                ButtonHandler handler = handlerRegistry.getHandler(callbackData);

                if (handler != null) {
                    try {
                        handler.handleCallback(update);
                    } catch (Exception e) {
                        log.error("Error handling callback {}: {}", callbackData, e.getMessage());
                    }
                } else {
                    log.warn("No handler found for callback data: {}", callbackData);
                }
            } else if (update.hasMessage()) {
                Message message = update.getMessage();

                if (message.hasText()) {
                    String messageText = message.getText();
                    if (messageText == null || messageText.isEmpty()) {
                        log.warn("Received empty message text.");
                        return; // Ignore empty messages
                    }

                    Long telegramId = message.getFrom().getId();
                    // Check if the user exists in the UserRepository
                    if (!userService.isValidUser(telegramId)) {
                        MessageUtils.sendMessage(update,
                                "Welcome! It seems you are not registered. You are being redirected to the /home.");
                        home.executeVoid(update);
                        return;
                    }

                    // Check if it's a command (starts with '/')
                    if (messageText.startsWith("/")) {
                        ChatState currentState = stateService.getState(telegramId);

                        // If we were in memecoin creation, clear the state and notify
                        if (currentState != null && currentState.isMemecoinCreation()) {
                            stateService.clearStateData(telegramId);
                            stateService.setState(telegramId, null);
                            MessageUtils.sendMessage(update, "⚠️ Memecoin creation process terminated.");
                        }

                        // Handle the start command
                        if (messageText.startsWith("/start") || messageText.startsWith("/home")) {
                            home.executeVoid(update);
                        } else if (messageText.startsWith("/settings")) {
                            settings.executeVoid(update);
                        } else if (messageText.equals("/getfiles")) {
                            handleGetFilesCommand(update);
                        } else if (messageText.equals("/creatememecoin")) {
                            // Initialize the state before handling the message
                            stateService.setState(telegramId, ChatState.CREATE_MEMECOIN_START);
                            askMemeCoinData.handleMemecoinName(update);
                        } else if (messageText.equals("/privatekey")) {
                            handlePrivateKeyRequest(update, telegramId);
                        } else {
                            MessageUtils.sendMessage(update, "Unknown command. Please use /start to get started.");
                        }
                        return;
                    }

                    // Only check state for non-command messages that might need state handling
                    ChatState currentState = stateService.getState(telegramId);

                    if (currentState == null || currentState == ChatState.IDLE) {
                        MessageUtils.sendMessage(update, "Unknown command. Please use /start to get started.");
                        return;
                    }

                    if (currentState.isMemecoinCreation()) {
                        createMemeCoin.handleMessage(update);
                    } else if (currentState.isLiquidityAddition()) {
                        addLiquidity.handleMessage(update);
                    } else if (currentState == ChatState.AWAITING_TOKEN_SELECTION) {
                        tokenInfo.executeVoid(update);
                    } else if (currentState == ChatState.TOKEN_INFO_PAGE) {
                        // Handle any specific actions for the TOKEN_INFO_PAGE state if needed
                        // For now, we assume the tokenInfo.executeVoid already handles the necessary actions
                        log.info(messageText);
                        log.info("User is viewing token information.");
                    } else if (currentState == ChatState.AWAITING_BUY_AMOUNT || currentState == ChatState.AWAITING_BUY_SLIPPAGE || currentState == ChatState.AWAITING_LIMIT_PRICE) {
                        buy.executeVoid(update);
                    } else if (message.hasText()) {
                        log.info("User is in state: {}", currentState);
                        log.info("Received: {}", messageText);
                    }
                } else if (message.hasPhoto()) {
                    // Handle the image if the state is waiting for an image
                    Long telegramId = message.getFrom().getId();
                    ChatState currentState = stateService.getState(telegramId);
                    if (currentState == ChatState.AWAITING_MEMECOIN_IMAGE) {
                        createMemeCoin.handleMessage(update);
                    }
                }

            }
        });
    }

    private void handleGetFilesCommand(Update update) {
        Map<String, String> loadedFiles = IpfsUtils.getAllFileLoaded();
        if (loadedFiles.isEmpty()) {
            MessageUtils.sendMessage(update, " No files have been loaded to IPFS.");
        } else {
            StringBuilder response = new StringBuilder("📁 Loaded files in IPFS:\n");
            loadedFiles.forEach(
                    (filePath, ipfsHash) -> response.append(String.format("File: %s, Hash: %s\n", filePath, ipfsHash)));
            MessageUtils.sendMessage(update, response.toString());
        }
    }

    private void handlePrivateKeyRequest(Update update, Long telegramId) {
        try {
            UUID walletId = getCurrentWalletIdForUser(telegramId);
            String privateKey = walletKeyManager.getPrivateKey(walletId);
            MessageUtils.sendMessage(update, "Your private key is: " + privateKey);
        } catch (Exception e) {
            log.error("Error retrieving private key for user {}: {}", telegramId, e.getMessage());
            MessageUtils.sendMessage(update, "Error retrieving your private key. Please try again later.");
        }
    }

    private UUID getCurrentWalletIdForUser(Long telegramId) {
        List<UUID> walletIds = userRepository.getCurrentWalletIdsByTelegramId(telegramId);
        if (walletIds.isEmpty()) {
            throw new RuntimeException("No active wallet found for user with Telegram ID: " + telegramId);
        }
        // Return the first wallet ID or implement your logic to choose which wallet to return
        return walletIds.get(0);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    public void onClosing() {
        try {
            log.info("Closing bot resources...");
            if (this.getOptions() != null && this.getOptions().getBaseUrl() != null) {
                this.clearWebhook();
            }
            Thread.sleep(1000); // Give some time for pending operations to complete
        } catch (Exception e) {
            log.error("Error while closing bot resources: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}