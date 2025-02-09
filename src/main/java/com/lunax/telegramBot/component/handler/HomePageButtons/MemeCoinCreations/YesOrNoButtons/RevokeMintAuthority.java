package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations.YesOrNoButtons;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;

@Component
public class RevokeMintAuthority implements ButtonHandler {

    @Autowired
    private StateService stateService;

    @Autowired
    private RevokeFreezeAuthority revokeFreezeAuthority;

    private final String buttonText;
    private final String callbackData;

    public RevokeMintAuthority(@Value("Revoke Mint Authority") String buttonText,
                              @Value("revoke_mint_authority_confirm") String callbackData) {
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
        ObjectNode stateData = stateService.getStateData(telegramId);
        stateData.put("revokeMintAuthority", true);
        stateService.setStateData(telegramId, stateData);
        
        // Log the action
        MessageUtils.editMessage(update, "Revoking mint authority set to: " + (stateData.get("revokeMintAuthority").asBoolean() ? "true" : "false") + " (6/8)", null);
        
        // Ask about freeze authority next
        askFreezeAuthority(update);
    }

    public void handleCancel(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        ObjectNode stateData = stateService.getStateData(telegramId);
        stateData.put("revokeMintAuthority", false);
        stateService.setStateData(telegramId, stateData);
       
        MessageUtils.editMessage(update, "Revoking mint authority set to: " + (stateData.get("revokeMintAuthority").asBoolean() ? "true" : "false") + " (6/8)", null);
        // Ask about freeze authority next
        askFreezeAuthority(update);
    }

    private void askFreezeAuthority(Update update) {
        Long telegramId = update.getCallbackQuery().getFrom().getId();
        String message = """
                🔒 *Authority Settings* (7/8)

                Would you like to revoke the freeze authority?
                This means accounts cannot be frozen after creation.

                Note: This action cannot be undone.
                """;

        ButtonHandler[][] buttons = {
            {
                revokeFreezeAuthority,
                new ButtonHandler() {
                    public String getButtonText() { return "No"; }
                    public String getCallbackData() { return "revoke_freeze_authority_cancel"; }
                    public void handleCallback(Update update) {
                        revokeFreezeAuthority.handleCallback(update);
                    }
                }
            }
        };

        MessageUtils.sendInlineKeyboard(update, message, buttons);
        stateService.setState(telegramId, ChatState.AWAITING_REVOKE_FREEZE_AUTHORITY_CONFIRMATION);
    }
} 