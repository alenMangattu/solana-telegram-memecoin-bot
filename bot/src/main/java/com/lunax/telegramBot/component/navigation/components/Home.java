package com.lunax.telegramBot.component.navigation.components;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.Buttons.AcceptTosHandler;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MyMemeCoins;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.RefreshHome;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.Settings;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.TradeMemeCoin;
import com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.AskMemeCoinData;
import com.lunax.telegramBot.event.NavigationEvent;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.WalletService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import jakarta.annotation.PostConstruct;

@Component
public class Home implements StateHandler {

    private static final Logger log = LoggerFactory.getLogger(Home.class);

    @Autowired
    private UserService userService;

    @Autowired
    private AcceptTosHandler acceptTosHandler;

    @Autowired
    private WalletService walletService;

    @Autowired
    private AskMemeCoinData createMemeCoin;

    @Autowired
    private TradeMemeCoin tradeMemeCoin;

    @Autowired
    private MyMemeCoins myMemeCoins;

    @Autowired
    private Settings settings;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    @Autowired
    private RefreshHome refreshHome;

    private ButtonHandler[][] buttonHandlers2;

    @PostConstruct
    private void init() {
        this.buttonHandlers2 = new ButtonHandler[][] {
                { myMemeCoins },
                { createMemeCoin, tradeMemeCoin },
                { settings, refreshHome }
        };
    }

    public static String tos = """
            Welcome to Solana Meme Coin Bot

            Before proceeding, please review and accept our Terms of Service:

            1. This bot is for creating and managing Solana-based meme coins
            2. You are responsible for keeping your private keys secure
            3. Use this bot at your own risk

            Do you accept these terms?
            """;

    public void executeVoid(Update update, boolean edit) {
        Long telegramId;
        String username = null;

        // Check if the update is a message or a callback query
        if (update.hasMessage()) {
            telegramId = update.getMessage().getFrom().getId();
            username = update.getMessage().getFrom().getUserName();
        } else if (update.hasCallbackQuery()) {
            telegramId = update.getCallbackQuery().getFrom().getId();
            // You can also retrieve the username if needed
            // username = update.getCallbackQuery().getFrom().getUserName();
        } else {
            // Handle unexpected update types
            log.warn("Received an update that is neither a message nor a callback query.");
            return; // Exit if the update type is not supported
        }

        System.out.println("From ID: " + telegramId);

        // Check if user exists, create if not
        if (!userService.isValidUser(telegramId)) {
            log.info("Creating new user with telegram ID: {}", telegramId);
            userService.createUser(telegramId, username);
        }

        // Only clear state if we're coming from a different state
        ChatState currentState = stateService.getState(telegramId);
        if (currentState == null || currentState != ChatState.IDLE) {
            stateService.clearStateData(telegramId);
            stateService.setState(telegramId, null);
        }

        ButtonHandler[][] buttonHandlers = { {
                acceptTosHandler
        } };

        if (!userService.hasAcceptedTos(telegramId)) {
            System.out.println("User has not yet accepted the TOS");
            userService.updateTosAcceptance(telegramId, false);
            MessageUtils.sendInlineKeyboard(update, tos, buttonHandlers);
            return;
        }
        String text = this.generateHomeText(telegramId);
        ButtonHandler[][] button = this.getButtonHandlers();
        if (edit)
            MessageUtils.editMessageInlineKeyboard(update, text, button);
        else
            MessageUtils.sendInlineKeyboard(update, text, button);

    }

    @Override
    public void executeVoid(Update update) {
        this.executeVoid(update, false);
    }

    public String generateHomeText(Long telegramId) {
        String text = "*Welcome to LunaxBot home page*\n\n";

        User user = userService.findByTelegramId(telegramId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        text += "*Your Wallets:*\n";
        List<Wallet> wallets = walletService.findByUser(user);

        if (wallets.isEmpty()) {
            text += "No wallets found. Create or import a wallet to get started.\n";
        } else {
            int i = 0;
            for (Wallet wallet : wallets) {
                text += "Wallet " + (i + 1) + ":";
                if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                    text += " (current)";
                }
                text += "\nBalance: `" + solanaUtils.getBalance(wallet.getWalletAddress()) + " SOL`\n";
                text += "Address: `" + wallet.getWalletAddress() + "`\n";
                i++;
            }
        }

        text += "\n\nSelect an option below:";

        return text;
    }

    public ButtonHandler[][] getButtonHandlers() {
        return this.buttonHandlers2;
    }

    @EventListener
    public void handleNavigationEvent(NavigationEvent event) {
        if ("HOME".equals(event.getDestination())) {
            executeVoid(event.getUpdate(), true);
        }
    }
}