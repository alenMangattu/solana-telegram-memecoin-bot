package com.lunax.telegramBot.controller;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.InitialLiquidity.AddLiquidity;
import com.lunax.telegramBot.component.Buttons.wallet.ImportExistingWallet;
import com.lunax.telegramBot.component.navigation.HandlerRegistry;
import com.lunax.telegramBot.component.navigation.components.Home;
import com.lunax.telegramBot.component.navigation.components.settings.SettingsImpl;
import com.lunax.telegramBot.component.navigation.components.settings.TraidingPerferences;
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
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.UserRepository;
import com.lunax.telegramBot.model.repository.WalletRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.component.navigation.components.trading.TradeMemeCoin;

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

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ImportExistingWallet importWallet;

    @Autowired
    private TraidingPerferences tradingPreferences;

    @Autowired
    private TradeMemeCoin tradeMemeCoin;

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
                
                // First try to process with MessageUtils pattern-based handlers
                boolean handled = MessageUtils.processCallbackQuery(update);
                
                // If not handled by MessageUtils, try the registry
                if (!handled) {
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
                            // Create a dummy callback query if we're processing a message
                            // This ensures Home.executeVoid works properly regardless of update type
                            if (update.hasMessage() && !update.hasCallbackQuery()) {
                                try {
                                    // Create a new Update with a CallbackQuery to make it compatible with Home.executeVoid
                                    Update callbackUpdate = new Update();
                                    CallbackQuery callbackQuery = new CallbackQuery();
                                    callbackQuery.setFrom(update.getMessage().getFrom());
                                    callbackQuery.setMessage(update.getMessage());
                                    callbackQuery.setData("home"); // Dummy data to indicate home navigation
                                    callbackUpdate.setCallbackQuery(callbackQuery);
                                    
                                    // Call home.executeVoid with the properly formatted update
                                    log.info("Converting message to callback for home navigation");
                                    home.executeVoid(callbackUpdate);
                                } catch (Exception e) {
                                    log.error("Error displaying home page: {}", e.getMessage(), e);
                                    MessageUtils.sendMessage(update, "Error displaying home page. Please try again.");
                                }
                            } else {
                                // If we already have a callback query, just proceed normally
                                home.executeVoid(update);
                            }
                            return;
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
                        // Check if it's for trade or for token info
                        ObjectNode stateData = stateService.getStateData(telegramId);
                        
                        if (stateData != null && stateData.has("tradeType")) {
                            // This is for trade (buy/sell)
                            tradeMemeCoin.processTokenAddressInput(update);
                        } else {
                            // Regular token info
                            tokenInfo.executeVoid(update);
                        }
                    } else if (currentState == ChatState.TRADE_MEMECOIN_START) {
                        // User is entering a token address directly in the trade page
                        // Set up default state data for trading
                        ObjectNode stateData = stateService.getStateData(telegramId);
                        if (stateData == null) {
                            stateData = stateService.initState(telegramId);
                        }
                        
                        // Default to "buy" if no trade type specified
                        stateData.put("tradeType", "buy");
                        stateService.setStateData(telegramId, stateData);
                        
                        // Process the token address for trading
                        tradeMemeCoin.processTokenAddressInput(update);
                    } else if (currentState == ChatState.TOKEN_INFO_PAGE) {
                        // Handle any specific actions for the TOKEN_INFO_PAGE state if needed
                        log.info(messageText);
                        log.info("User is viewing token information.");
                    } else if (currentState == ChatState.AWAITING_BUY_AMOUNT || currentState == ChatState.AWAITING_BUY_SLIPPAGE || currentState == ChatState.AWAITING_LIMIT_PRICE) {
                        buy.executeVoid(update);
                    } else if (currentState == ChatState.AWAITING_WALLET_LABEL) {
                        // Handle wallet label update
                        handleWalletLabelUpdate(update);
                    } else if (currentState == ChatState.AWAITING_WALLET_IMPORT) {
                        importWallet.processPrivateKey(update, messageText);
                    } else if (currentState.isTradingPreferences()) {
                        // Handle all trading preferences states with a single method
                        tradingPreferences.handleMessage(update);
                    } else if (currentState == ChatState.AWAITING_SOURCE_WALLET_ADDRESS) {
                        // Process source wallet input for copy trading
                        tradeMemeCoin.processSourceWalletInput(update);
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

    /**
     * Handle the wallet label update process
     */
    private void handleWalletLabelUpdate(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        String newLabel = update.getMessage().getText();
        
        try {
            // Get the wallet ID from state data
            ObjectNode stateData = stateService.getStateData(telegramId);
            if (stateData == null || !stateData.has("walletId")) {
                MessageUtils.sendMessage(update, "❌ Error updating wallet label. Missing wallet information.");
                stateService.setState(telegramId, ChatState.IDLE);
                return;
            }
            
            String walletId = stateData.get("walletId").asText();
            log.info("Updating label for wallet {} to '{}'", walletId, newLabel);
            
            // Get the wallet and update the label
            User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Find the wallet by ID using UUID.fromString
            Wallet wallet = walletRepository.findById(UUID.fromString(walletId))
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
            
            // Ensure the wallet belongs to the current user
            if (!wallet.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("This wallet does not belong to you");
            }
            
            // Update the label
            wallet.setWalletLabel(newLabel);
            walletRepository.save(wallet);
            
            // Reset the state
            stateService.setState(telegramId, ChatState.IDLE);
            stateService.clearStateData(telegramId);
            
            // Confirm and return to wallet management
            MessageUtils.sendMessage(update, "✅ Wallet label updated successfully to: " + newLabel);
            
            // Create a dummy callback update to trigger the wallet listing
            // Update callbackUpdate = new Update();
            // CallbackQuery callbackQuery = new CallbackQuery();
            // callbackQuery.setFrom(update.getMessage().getFrom());
            // callbackQuery.setData("wallets"); // This should match the callback data for the Wallets handler
            // callbackUpdate.setCallbackQuery(callbackQuery);
            
            // // Get the wallets button handler from the registry and call it
            // ButtonHandler walletsHandler = handlerRegistry.getHandler("wallets");
            // if (walletsHandler != null) {
            //     walletsHandler.handleCallback(callbackUpdate);
            // }
            // wallets.executeVoid(update);
            
        } catch (Exception e) {
            log.error("Error updating wallet label: {}", e.getMessage());
            MessageUtils.sendMessage(update, "❌ Error updating wallet label: " + e.getMessage());
            stateService.setState(telegramId, ChatState.IDLE);
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