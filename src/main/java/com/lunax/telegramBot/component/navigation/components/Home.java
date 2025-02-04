package com.lunax.telegramBot.component.navigation.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.handler.AcceptTosHandler;
import com.lunax.telegramBot.component.handler.HomePageButtons.CreateMemeCoin;
import com.lunax.telegramBot.component.handler.HomePageButtons.MyMemeCoins;
import com.lunax.telegramBot.component.handler.HomePageButtons.Settings;
import com.lunax.telegramBot.component.handler.HomePageButtons.TradeMemeCoin;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.utils.SolanaUtils;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import java.util.List;

@Component
public class Home implements StateHandler {

    @Autowired
    private UserService userService;

    @Autowired
    private AcceptTosHandler acceptTosHandler;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private CreateMemeCoin createMemeCoin;

    @Autowired
    private TradeMemeCoin tradeMemeCoin;

    @Autowired
    private MyMemeCoins myMemeCoins;

    @Autowired
    private Settings settings;

    public static String tos = """
            Welcome to Solana Meme Coin Bot

            Before proceeding, please review and accept our Terms of Service:

            1. This bot is for creating and managing Solana-based meme coins
            2. You are responsible for keeping your private keys secure
            3. Use this bot at your own risk

            Do you accept these terms?
            """;

    @Override
    public void executeVoid(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        System.out.println("From ID: " + telegramId);

        ButtonHandler[][] buttonHandlers = {{
            acceptTosHandler
        }};
        
        if (!userService.hasAcceptedTos(telegramId)) {
            System.out.println("User has not yet accepted the TOS");
            userService.updateTosAcceptance(telegramId, false);
            MessageUtils.sendInlineKeyboard(update, tos, buttonHandlers); 
            return;
        }
       
        String text = "*Welcome to LunaxBot home page*\n\n";

        User user = userService.findByTelegramId(telegramId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        text += "*Your Wallets:*\n";
        List<Wallet> wallets = walletRepository.findByUser(user);
        
        if (wallets.isEmpty()) {
            text += "No wallets found. Create or import a wallet to get started.\n";
        } else {
            int i = 0;
            for (Wallet wallet : wallets) {
                text += "Wallet " + (i + 1) + ":";
                if (wallet.getWalletId().equals(user.getCurrentWallet())) {
                    text += " (current)";
                }
                text += "\nBalance: `" + SolanaUtils.getBalance(wallet.getWalletAddress()) + " SOL`\n";
                text += "Address: `" + wallet.getWalletAddress() + "`\n";
            }
        }

        text += "\n\nSelect an option below:";

        ButtonHandler[][] buttonHandlers2 = {{createMemeCoin, tradeMemeCoin}, 
                                            {myMemeCoins}, 
                                            {settings}};

        MessageUtils.sendInlineKeyboard(update, text, buttonHandlers2);
    }
}