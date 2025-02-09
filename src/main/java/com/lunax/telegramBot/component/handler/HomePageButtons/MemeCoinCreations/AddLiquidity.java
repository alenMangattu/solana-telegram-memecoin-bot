package com.lunax.telegramBot.component.handler.HomePageButtons.MemeCoinCreations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.model.db.User;
import com.lunax.telegramBot.service.UserService;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.SolanaUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AddLiquidity implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private UserService userService;

    @Autowired
    private StateService stateService;

    @Autowired
    private SolanaUtils solanaUtils;

    public AddLiquidity(@Value("Add Liquidity") String buttonText,
                        @Value("add_liquidity") String callbackData) {
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
        log.debug("User {} initiated liquidity addition.", telegramId);
        MessageUtils.sendMessage(update, "💧 Please enter the token amount to add for liquidity:");
        stateService.setState(telegramId, ChatState.AWAITING_LIQUIDITY_AMOUNT);
    }

    public void handleMessage(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        ChatState currentState = stateService.getState(telegramId);
        ObjectNode stateData = stateService.getStateData(telegramId);

        if (currentState == ChatState.AWAITING_LIQUIDITY_AMOUNT) {
            handleLiquidityAmountInput(update, telegramId, update.getMessage().getText(), stateData);
        } else if (currentState == ChatState.AWAITING_SOL_AMOUNT) {
            handleSolAmountInput(update, telegramId, update.getMessage().getText(), stateData);
        } else {
            log.warn("Unexpected state for user {}: {}", telegramId, currentState);
            MessageUtils.sendMessage(update, "❌ Unexpected state. Please start the liquidity addition process again.");
        }
    }

    public void handleLiquidityAmountInput(Update update, Long telegramId, String amountStr, ObjectNode stateData) {
        log.debug("User {} entered liquidity token amount: {}", telegramId, amountStr);
        try {
            double tokenAmount = Double.parseDouble(amountStr);
            if (tokenAmount <= 0 || tokenAmount > 1_000_000_000_000_000.0) {
                throw new NumberFormatException();
            }
            stateData.put("liquidityTokenAmount", tokenAmount);
            stateService.setStateData(telegramId, stateData);
            
            String message = """
                    💧 *Liquidity Addition* (2/3)

                    How much SOL would you like to add to the liquidity pool?

                    _Example: 1.5_

                    Requirements:
                    - Must be a positive number
                    - Maximum 100,000 SOL
                    """;
            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_SOL_AMOUNT);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid token amount for liquidity.");
            log.error("Invalid token amount entered by user {}: {}", telegramId, amountStr);
        }
    }

    public void handleSolAmountInput(Update update, Long telegramId, String amountStr, ObjectNode stateData) {
        log.debug("User {} entered SOL amount: {}", telegramId, amountStr);
        try {
            double solAmount = Double.parseDouble(amountStr);
            if (solAmount <= 0 || solAmount > 100_000.0) {
                throw new NumberFormatException();
            }
            stateData.put("solAmount", solAmount);
            stateService.setStateData(telegramId, stateData);

            // Show confirmation message with details
            String confirmationMessage = String.format("""
                    💧 *Liquidity Addition* (3/3)

                    Please confirm your liquidity addition details:

                    Tokens: %,.0f
                    SOL: %.2f

                    Are you sure you want to add this liquidity?
                    """,
                    stateData.get("liquidityTokenAmount").asDouble(),
                    solAmount
            );

            // Create Yes/No confirmation buttons
            ButtonHandler[][] buttons = {
                {
                    new ButtonHandler() {
                        public String getButtonText() { return "Yes"; }
                        public String getCallbackData() { return "liquidity_confirm"; }
                        public void handleCallback(Update update) {
                            // Call the method to add liquidity
                            boolean success = solanaUtils.addLiquidity(telegramId, stateData.get("liquidityTokenAmount").asDouble(), solAmount);
                            if (success) {
                                MessageUtils.sendMessage(update, "✅ Liquidity added successfully!");
                            } else {
                                MessageUtils.sendMessage(update, "❌ Liquidity addition failed. Please try again.");
                            }
                            stateService.setState(telegramId, ChatState.IDLE);
                            stateService.clearStateData(telegramId);
                        }
                    },
                    new ButtonHandler() {
                        public String getButtonText() { return "No"; }
                        public String getCallbackData() { return "liquidity_cancel"; }
                        public void handleCallback(Update update) {
                            MessageUtils.sendMessage(update, "❌ Liquidity addition cancelled.");
                            stateService.setState(telegramId, ChatState.IDLE);
                            stateService.clearStateData(telegramId);
                        }
                    }
                }
            };

            MessageUtils.sendInlineKeyboard(update, confirmationMessage, buttons);
            stateService.setState(telegramId, ChatState.AWAITING_LIQUIDITY_CONFIRMATION);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid SOL amount for liquidity.");
            log.error("Invalid SOL amount entered by user {}: {}", telegramId, amountStr);
        }
    }

    // Additional methods for handling SOL amount and confirmation can be added here...
} 