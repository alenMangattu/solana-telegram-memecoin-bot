package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.InitialLiquidity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ReEnterLiquidity implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private StateService stateService;

    public ReEnterLiquidity(@Value("Re-Enter Liquidity") String buttonText,
            @Value("reenter_liquidity") String callbackData) {
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
        
        // Get current state data
        ObjectNode stateData = stateService.getStateData(telegramId);
        
        // Keep only memecoin-related data and remove liquidity-related data
        ObjectNode newStateData = stateData.objectNode();
        if (stateData != null) {
            // Preserve memecoin data
            if (stateData.has("name")) newStateData.set("name", stateData.get("name"));
            if (stateData.has("symbol")) newStateData.set("symbol", stateData.get("symbol"));
            if (stateData.has("decimals")) newStateData.set("decimals", stateData.get("decimals"));
            if (stateData.has("supply")) newStateData.set("supply", stateData.get("supply"));
            if (stateData.has("description")) newStateData.set("description", stateData.get("description"));
            if (stateData.has("imageUrl")) newStateData.set("imageUrl", stateData.get("imageUrl"));
            if (stateData.has("revokeMintAuthority")) newStateData.set("revokeMintAuthority", stateData.get("revokeMintAuthority"));
            if (stateData.has("revokeFreezeAuthority")) newStateData.set("revokeFreezeAuthority", stateData.get("revokeFreezeAuthority"));
        }
        
        // Update state with cleaned data
        stateService.setStateData(telegramId, newStateData);
        
        // Restart liquidity process
        String message = """
                💱 *Add Liquidity* (1/4)
                
                Please enter the token symbol or address you want to swap from:
                
                _Example: SOL or Enter token address_
                """;
                
        MessageUtils.sendMessage(update, "⚠️ Liquidity process restarted.");
        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_FROM_TOKEN);
    }
}