package com.lunax.telegramBot.component.navigation.components.settings;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.lunax.telegramBot.component.handler.BacktoHome;
import com.lunax.telegramBot.component.handler.SettingsButtons.HouseOfRules;
import com.lunax.telegramBot.component.handler.SettingsButtons.NotificationHub;
import com.lunax.telegramBot.component.handler.SettingsButtons.TraidingPerfernces;
import com.lunax.telegramBot.component.handler.SettingsButtons.Wallets;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import jakarta.annotation.PostConstruct;


@Component
public class SettingsImpl implements StateHandler {
   
   @Autowired
   private BacktoHome backtoHome;

   @Autowired
   private NotificationHub notificationHub;

   @Autowired
   private TraidingPerfernces traidingPerfernces;

   @Autowired
   private Wallets wallets;

   @Autowired
   private HouseOfRules houseOfRules;

   private String settingsText = """
Settings

Choose an option below:

━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 Frequently Asked Questions
━━━━━━━━━━━━━━━━━━━━━━━━━━━

💰 Balance Requirements
Q: What's the minimum SOL balance needed?
A: • Creation: `1.000 SOL`
   • Trading: `0.100 SOL`
Note: Keep extra for gas fees

💸 Transaction Fees
Q: What are the associated costs?
A: • Network: `0.000005 SOL`
   • Platform: `1.00%`
Note: Fees are per transaction

👛 Wallet Management
Q: How many wallets can I have?
A: • Maximum: `5 wallets`
   • Types:
     ▫️ Main: `Token Creation`
     ▫️ Trading: `Market Operations`

📊 Trading Limits
Q: What are the trading restrictions?
A: • Pool Max: `3.00% per trade`
   • Daily Max: `10.00% per wallet`
Note: Limits help prevent manipulation

⚠️ Risk Management
Q: What triggers liquidation?
A: • Price Drop: `95% in 24h`
   • Pattern: `Suspicious trading`
Note: Automatic protection system
━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """;

   private ButtonHandler[][] buttonHandlers;
   @PostConstruct
   private void init() {
      this.buttonHandlers = new ButtonHandler[][]{
            {wallets},
            {traidingPerfernces},
            {notificationHub},
            {houseOfRules},
            {backtoHome}
         };
   }

   @Override
    public void executeVoid(Update update) {
      MessageUtils.sendInlineKeyboard(update, settingsText, buttonHandlers);
   }
   
   public String getText() {
      return settingsText;
   }

   public ButtonHandler[][] getButtonHandlers() {
      return buttonHandlers;
   }
}
