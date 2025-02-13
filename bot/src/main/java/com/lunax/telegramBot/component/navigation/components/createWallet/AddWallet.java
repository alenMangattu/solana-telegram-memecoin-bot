package com.lunax.telegramBot.component.navigation.components.createWallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.lunax.telegramBot.component.handler.wallet.CreateNewWallet;
import com.lunax.telegramBot.component.handler.wallet.ImportExistingWallet;
import com.lunax.telegramBot.component.navigation.components.StateHandler;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

@Component
public class AddWallet implements StateHandler {

    @Autowired
    private CreateNewWallet createNewWallet;

    @Autowired
    private ImportExistingWallet importExistingWallet;

    String firstPrompt = """
*Wallet Setup*

Please select your preferred option below:
• Create a new wallet
• Import an existing wallet
            """;

    @Override
    public void executeVoid(Update update) {
        ButtonHandler[][] buttonHandlers = {{
            createNewWallet,
            importExistingWallet
        }};
        MessageUtils.sendInlineKeyboard(update, firstPrompt, buttonHandlers);
    }
}