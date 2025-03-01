package com.lunax.telegramBot.component.Buttons.HomePageButtons.MemeCoinCreations.InitialLiquidity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lunax.telegramBot.model.session.ChatState;
import com.lunax.telegramBot.service.StateService;
import com.lunax.telegramBot.utils.components.ButtonHandler;
import com.lunax.telegramBot.utils.components.MessageUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component("initialLiquidityAddLiquidity")
public class AddLiquidity implements ButtonHandler {

    private final String buttonText;
    private final String callbackData;

    @Autowired
    private StateService stateService;

    @Autowired
    private ConfirmLiquidity confirmLiquidity;

    @Autowired
    private ReEnterLiquidity cancelLiquidity;

    // Add a new state for fee tier selection
    private static final ChatState AWAITING_FEE_TIER = ChatState.AWAITING_FEE_TIER;

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
                💱 *Add Liquidity* (1/4)

                Please enter the token symbol or address you want to swap from:

                _Example: SOL or Enter token address_
                """;

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_FROM_TOKEN);
    }

    @Override
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
            case AWAITING_SLIPPAGE_PERCENTAGE:
                handleSlippageInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_FEE_TIER:
                handleFeeTierInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_TO_TOKEN:
                handleToTokenInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_TO_AMOUNT:
                handleToAmountInput(update, telegramId, update.getMessage().getText(), stateData);
                break;
            case AWAITING_LP_BURN_AMOUNT:
                handleLpBurnAmount(update, telegramId, update.getMessage().getText(), stateData);
                break;
            default:
                log.warn("Unexpected state for user {}: {}", telegramId, currentState);
                MessageUtils.sendMessage(update,
                        "❌ Unexpected state. Please start the liquidity addition process again.");
        }
    }

    private void handleFromTokenInput(Update update, Long telegramId, String token, ObjectNode stateData) {
        // Validate token/address here
        stateData.put("fromToken", token);
        stateService.setStateData(telegramId, stateData);

        String message = """
                💱 *Add Liquidity* (2/4)

                How much %s would you like to add to the liquidity pool?
                Recommended amount: 10 SOL (if you're adding SOL)

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

            // Ask for slippage instead of going directly to fee tier
            askForSlippage(update, telegramId);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid amount.");
        }
    }

    private void askForSlippage(Update update, Long telegramId) {
        String message = """
                💱 *Set Slippage Tolerance*

                Please enter your desired slippage tolerance percentage:

                • 0.5% (Recommended for stable pairs)
                • 1% (Recommended for volatile pairs)
                • 2.5% (High slippage)

                Or enter a custom percentage (0.1-5.0)
                """;

        String[][] keyboardButtons = {
                { "0.5%", "1%" },
                { "2.5%", "Custom" }
        };

        MessageUtils.sendCustomKeyboard(update, message, keyboardButtons, true);
        stateService.setState(telegramId, ChatState.AWAITING_SLIPPAGE_PERCENTAGE);
    }

    private void handleSlippageInput(Update update, Long telegramId, String slippageStr, ObjectNode stateData) {
        try {
            double slippage;
            if (slippageStr.endsWith("%")) {
                slippageStr = slippageStr.substring(0, slippageStr.length() - 1);
            }

            if (slippageStr.equalsIgnoreCase("Custom")) {
                MessageUtils.sendMessage(update, "Please enter a custom slippage percentage (0.1-5.0):");
                return;
            }

            slippage = Double.parseDouble(slippageStr);

            if (slippage < 0.1 || slippage > 5.0) {
                throw new IllegalArgumentException("Slippage must be between 0.1% and 5.0%");
            }

            stateData.put("slippage", slippage);
            stateService.setStateData(telegramId, stateData);

            // Now proceed to fee tier selection
            askForFeeTier(update, telegramId);

        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid percentage.");
        } catch (IllegalArgumentException e) {
            MessageUtils.sendMessage(update, "❌ " + e.getMessage());
        }
    }

    private void askForFeeTier(Update update, Long telegramId) {
        String message = """
                💱 *Select Fee Tier*

                Please select a fee tier for the liquidity addition:

                • 0.25% (Recommended)
                • 1%
                • 2%
                • 4%
                """;

        String[][] keyboardButtons = {
                { "0.25%", "1%" },
                { "2%", "4%" }
        };

        MessageUtils.sendCustomKeyboard(update, message, keyboardButtons, true);
        stateService.setState(telegramId, AWAITING_FEE_TIER);
    }

    private void handleFeeTierInput(Update update, Long telegramId, String feeTier, ObjectNode stateData) {
        // Validate fee tier input
        if (feeTier.equals("0.25%") || feeTier.equals("1%") || feeTier.equals("2%") || feeTier.equals("4%")) {
            stateData.put("feeTier", feeTier);

            // Set the toToken as the symbol from state data
            String symbol = stateData.get("symbol").asText();
            stateData.put("toToken", symbol);

            stateService.setStateData(telegramId, stateData);

            // Move to the next step - asking for the amount of memecoin
            String message = String.format("""
                    💱 *Add Liquidity* (3/4)

                    How much $%s would you like to receive?

                    _Example: 1000_
                    """, symbol);

            MessageUtils.sendMessage(update, message);
            stateService.setState(telegramId, ChatState.AWAITING_TO_AMOUNT);
        } else {
            MessageUtils.sendMessage(update, "❌ Invalid fee tier selected. Please choose again.");
            askForFeeTier(update, telegramId);
        }
    }

    private void handleToTokenInput(Update update, Long telegramId, String token, ObjectNode stateData) {
        // Validate token/address here
        // stateData.put("toToken", token);
        // stateService.setStateData(telegramId, stateData);

        // Retrieve the total supply and decimals from the state data
        double totalSupply = stateService.getStateData(telegramId).get("supply").asDouble();
        int decimals = stateService.getStateData(telegramId).get("decimals").asInt();

        // Format the total supply based on the number of decimals
        String formattedTotalSupply = String.format("%." + decimals + "f", totalSupply)
                .replaceAll("(?<=\\d)(?=(\\d{3})+(?!\\d))", ","); // Add commas as thousands separators

        /*
         * 
         * Regex Pattern: (?<=\\d)(?=(\\d{3})+(?!\\d))
         * (?<=\\d): This is a positive lookbehind assertion that checks for a digit
         * (\\d) before the current position. It ensures that we only add a comma after
         * a digit.
         * (?=(\\d{3})+(?!\\d)): This is a positive lookahead assertion that checks for
         * groups of three digits (\\d{3}) that follow the current position, ensuring
         * that there are no more digits after these groups (i.e., it checks that we are
         * at the end of the number).
         */

        String message = String.format("""
                💱 *Add Liquidity* (3/4)

                How much $%s would you like to receive?
                Total supply: %s

                _Example: 1000_
                """, token, formattedTotalSupply);

        MessageUtils.sendMessage(update, message);
        stateService.setState(telegramId, ChatState.AWAITING_TO_AMOUNT);
    }

    private void handleToAmountInput(Update update, Long telegramId, String amountStr, ObjectNode stateData) {
        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                throw new NumberFormatException();
            }

            // Retrieve the total supply and decimals from the state data
            double totalSupply = stateService.getStateData(telegramId).get("supply").asDouble();
            int decimals = stateService.getStateData(telegramId).get("decimals").asInt();

            // Check if the amount to receive exceeds the total supply
            if (amount > totalSupply) {
                MessageUtils.sendMessage(update, String.format(
                        "❌ The amount you want to receive exceeds the total supply of the memecoin. Total supply: %.0f",
                        totalSupply));
                return; // Exit the method if the check fails
            }

            // Check if the amount is compatible with the token's decimal precision
            double precisionFactor = Math.pow(10, decimals);
            if (amount * precisionFactor != Math.floor(amount * precisionFactor)) {
                MessageUtils.sendMessage(update, String.format(
                        "❌ The amount must be compatible with the token's decimal precision of %d. Please enter a valid amount.",
                        decimals));
                return; // Exit the method if the check fails
            }

            stateData.put("toAmount", amount);
            stateService.setStateData(telegramId, stateData);

            String message = """
                    💱 *Add Liquidity* (4/5)

                    How many LP tokens would you like to burn?

                    • 0: Keep all LP tokens (No burn)
                    • MAX: Burn all LP tokens
                    • Or enter a custom amount

                    _Note: Burning LP tokens means you won't be able to remove that portion of liquidity later._
                    """;

            String[][] keyboardButtons = {
                    { "0", "MAX" }
            };

            MessageUtils.sendCustomKeyboard(update, message, keyboardButtons, true);
            stateService.setState(telegramId, ChatState.AWAITING_LP_BURN_AMOUNT);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid amount.");
        }
    }

    private void handleLpBurnAmount(Update update, Long telegramId, String burnAmountStr, ObjectNode stateData) {
        try {
            double burnAmount;
            if (burnAmountStr.equalsIgnoreCase("MAX")) {
                burnAmount = 100.0; // 100% burn
            } else {
                burnAmount = Double.parseDouble(burnAmountStr);
                if (burnAmount < 0 || burnAmount > 100) {
                    throw new NumberFormatException();
                }
            }

            stateData.put("lpBurnAmount", burnAmount);
            stateService.setStateData(telegramId, stateData);

            // Check if toToken exists in stateData
            if (!stateData.has("toToken")) {
                MessageUtils.sendMessage(update, "❌ The destination token is not set. Please start the process again.");
                return;
            }

            // Get the memecoin symbol from state data
            String toToken = stateData.get("toToken").asText();

            // Show confirmation message with both memecoin and liquidity details
            String confirmationMessage = String.format("""
                    📝 *Memecoin Creation Summary*

                    *Token Details:*
                    Name: %s
                    Symbol: %s
                    Decimals: %d
                    Supply: %,.0f
                    Description: %s
                    Revoke Mint Authority: %s
                    Revoke Freeze Authority: %s

                    *Liquidity Details:*
                    From: %,.2f %s
                    To: %,.2f %s
                    Slippage Tolerance: %.1f%%
                    Fee Tier: %s
                    LP Burn: %s%%

                    Are you sure you want to proceed with the creation and liquidity addition?
                    """,
                    stateData.get("name").asText(),
                    stateData.get("symbol").asText(),
                    stateData.get("decimals").asInt(),
                    stateData.get("supply").asDouble(),
                    stateData.get("description").asText(),
                    stateData.has("revokeMintAuthority")
                            ? (stateData.get("revokeMintAuthority").asBoolean() ? "Yes" : "No")
                            : "No",
                    stateData.has("revokeFreezeAuthority")
                            ? (stateData.get("revokeFreezeAuthority").asBoolean() ? "Yes" : "No")
                            : "No",
                    stateData.get("fromAmount").asDouble(),
                    stateData.get("fromToken").asText(),
                    stateData.get("toAmount").asDouble(),
                    toToken,
                    stateData.get("slippage").asDouble(),
                    stateData.get("feeTier").asText(),
                    burnAmountStr.equalsIgnoreCase("MAX") ? "100" : String.format("%.2f", burnAmount));

            // Check if the image URL exists in state data
            String imageUrl = stateData.has("imageUrl") ? stateData.get("imageUrl").asText() : null;

            // Create Yes/No confirmation buttons
            ButtonHandler[][] buttons = {
                    { confirmLiquidity, cancelLiquidity },
            };

            // Send the image with the confirmation message if the image URL is available
            if (imageUrl != null) {
                MessageUtils.sendImageMessageInlineKeyboard(update, imageUrl, confirmationMessage, buttons);
            } else {
                MessageUtils.sendInlineKeyboard(update, confirmationMessage, buttons);
            }

            stateService.setState(telegramId, ChatState.AWAITING_LIQUIDITY_CONFIRMATION);
        } catch (NumberFormatException e) {
            MessageUtils.sendMessage(update, "❌ Please enter a valid amount between 0 and 100.");
        }
    }
}