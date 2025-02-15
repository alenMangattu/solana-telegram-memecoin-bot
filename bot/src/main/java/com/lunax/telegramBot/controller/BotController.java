package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.component.navigation.components.settings.SettingsImpl;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.utils.IpfsUtils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

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
    private AddLiquidity addLiquidity;

    public BotController(String botUsername, String botToken) {
        super(botToken);
        this.botUsername = botUsername;
        MessageUtils.setBotInstance(this);
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            ButtonHandler handler = handlerRegistry.getHandler(callbackData);

            if (handler != null) {
                handler.handleCallback(update);
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
                            "Welcome! It seems you are not registered or have no wallets. You are being redirected to the /start command. LOL");
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
                        return;
                    } else if (messageText.equals("/getfiles")) {
                        handleGetFilesCommand(update);
                    } else if (messageText.equals("/creatememecoin")) {
                        // Initialize the state before handling the message
                        stateService.setState(telegramId, ChatState.CREATE_MEMECOIN_START);
                        askMemeCoinData.handleMemecoinName(update);
                    } else {
                        MessageUtils.sendMessage(update, "Unknown command. Please use /start to get started.");
                    }
                    return;
                }

                // Only check state for non-command messages that might need state handling
                ChatState currentState = stateService.getState(telegramId);

                if (currentState == null || currentState == ChatState.IDLE) {
                    // home.executeVoid(update);
                    MessageUtils.sendMessage(update, "Unknown command. Please use /start to get started.");
                    return;
                }

                if (currentState.isMemecoinCreation()) {
                    createMemeCoin.handleMessage(update);
                } else if (currentState.isLiquidityAddition()) {
                    addLiquidity.handleMessage(update);
                } else if (message.hasText()) {
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
    }

    private void handleGetFilesCommand(Update update) {
        Map<String, String> loadedFiles = IpfsUtils.getAllFileLoaded();
        if (loadedFiles.isEmpty()) {
            MessageUtils.sendMessage(update, "�� No files have been loaded to IPFS.");
        } else {
            StringBuilder response = new StringBuilder("📁 Loaded files in IPFS:\n");
            loadedFiles.forEach(
                    (filePath, ipfsHash) -> response.append(String.format("File: %s, Hash: %s\n", filePath, ipfsHash)));
            MessageUtils.sendMessage(update, response.toString());
        }
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