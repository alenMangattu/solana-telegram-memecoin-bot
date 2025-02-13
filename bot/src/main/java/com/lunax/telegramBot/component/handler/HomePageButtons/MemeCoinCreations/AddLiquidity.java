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
        
        String message = """
                💱 *Add Liquidity* (1/5)
                
                Please enter the token symbol or address you want to swap from:
                
                _Example: SOL or Enter token address_
                """;
                
        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_FROM_TOKEN);
    }

    public void handleMessage(Update update) {
        Long telegramId = update.getMessage().getFrom().getId();
        ChatState currentState = stateService.getState(telegramId);
        ObjectNode stateData = stateService.getStateData(telegramId);

        switch (currentState) {
            case AWAITING_FROM_TOKEN:
                handleFromTokenInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_FROM_AMOUNT:
                handleFromAmountInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_TO_TOKEN:
                handleToTokenInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_TO_AMOUNT:
                handleToAmountInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            default:
                log.warn("Unexpected state for user {}: {}", telegramId, currentState);
                MessageUtils.sendMessage(update, "❌ Unexpected state. Please start the liquidity addition process again.");
        }
    }

    private void handleFromTokenInput(Update update, Long telegramId, String token, ObjectNode stateData) {
        // Validate token/address here
        stateData.put("fromToken", token);
        stateService.setStateData(telegramId, stateData);

        String message = """
                💱 *Add Liquidity* (2/5)
                
                How much %s would you like to swap?
                
                _Example: 100_
                """.formatted(token);

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_FROM_AMOUNT);
    }

    private void handleFromAmountInput(Update update, Long telegramId, String amountStr, ObjectNode stateData) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            
            stateData.put("fromAmount", amount);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    💱 *Add Liquidity* (3/5)
                    
                    Please enter the token symbol or address you want to swap to:
                    
                    _Example: USDC or Enter token address_
                    """;

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_TO_TOKEN);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid amount.");
        }
    }

    private void handleToTokenInput(Update update, Long telegramId, String token, ObjectNode stateData) {
        // Validate token/address here
        stateData.put("toToken", token);
        stateService.setStateData(telegramId, stateData);

        String message = """
                💱 *Add Liquidity* (4/5)
                
                How much %s would you like to receive?
                
                _Example: 1000_
                """.formatted(token);

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_TO_AMOUNT);
    }

    private void handleToAmountInput(Update update, Long telegramId, String amountStr, ObjectNode stateData) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            
            stateData.put("toAmount", amount);
            stateService.setStateData(telegramId, stateData);

            // Show confirmation message
            String confirmationMessage = String.format("""
                    💱 *Add Liquidity* (5/5)
                    
                    Please confirm your liquidity addition:
                    
                    From: %,.2f %s
                    To: %,.2f %s
                    
                    Are you sure you want to proceed?
                    """,
                    stateData.get("fromAmount").asDouble(),
                    stateData.get("fromToken").asText(),
                    stateData.get("toAmount").asDouble(),
                    stateData.get("toToken").asText()
            );

            // Create Yes/No confirmation buttons
            ButtonHandler[][] buttons = {
                {
                    new ButtonHandler() {
                        public String getButtonText() { return "Yes"; }
                        public String getCallbackData() { return "liquidity_confirm"; }
                        public void handleCallback(Update update) {
                            // Call method to add liquidity
                            // boolean success = solanaUtils.addLiquidity(
                            //     telegramId,
                            //     stateData.get("fromToken").asText(),
                            //     stateData.get("fromAmount").asDouble(),
                            //     stateData.get("toToken").asText(),
                            //     stateData.get("toAmount").asDouble()
                            // );
                            boolean success = false;
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
            MessageUtils.sendMessage(update, "❌ Please enter a valid amount.");
        }
    }
} 