package com.lunax.telegramBot.model.session;

/**
 * Enum representing different states of a chat session in the Telegram bot.
 * This helps in managing the conversation flow and determining appropriate responses.
 */
public enum ChatState {
    IDLE,               // Initial state, waiting for user input
    AWAITING_WALLET,    // Waiting for wallet address input
    CREATING_WALLET,    // In process of creating a new wallet
    CHECKING_BALANCE,   // Checking wallet balance
    SENDING_SOL,        // In process of sending SOL
    
    // Memecoin Creation States
    CREATE_MEMECOIN_START,          // Starting memecoin creation process
    AWAITING_MEMECOIN_NAME,         // Waiting for memecoin name
    AWAITING_MEMECOIN_SYMBOL,       // Waiting for ticker symbol
    AWAITING_MEMECOIN_DECIMALS,     // Waiting for token decimals
    AWAITING_MEMECOIN_SUPPLY,       // Waiting for total supply
    AWAITING_MEMECOIN_IMAGE,        // Waiting for logo/gif upload
    AWAITING_MEMECOIN_DESCRIPTION,  // Waiting for memecoin description
    AWAITING_MEMECOIN_CONFIRMATION, // Waiting for confirmation
    CREATING_MEMECOIN,              // Processing memecoin creation
    
    // Memecoin Trading States
    TRADE_MEMECOIN_START,           // Starting trading process
    AWAITING_TRADE_SYMBOL,          // Waiting for memecoin ticker to trade
    AWAITING_TRADE_AMOUNT,          // Waiting for amount to buy/sell
    AWAITING_TRADE_TYPE,            // Waiting for trade type (buy/sell)
    CONFIRMING_TRADE,               // Confirming trade details
    PROCESSING_TRADE,               // Processing the trade
    
    // Updated Liquidity Addition States
    ADD_LIQUIDITY_START,           // Starting liquidity addition process
    AWAITING_FROM_TOKEN,           // Waiting for source token (ticker/address)
    AWAITING_FROM_AMOUNT,          // Waiting for source token amount
    AWAITING_TO_TOKEN,            // Waiting for destination token (ticker/address)
    AWAITING_TO_AMOUNT,           // Waiting for destination token amount
    AWAITING_LP_BURN_AMOUNT,      // Waiting for amount of LP tokens to burn
    AWAITING_LIQUIDITY_CONFIRMATION, // Waiting for final confirmation
    PROCESSING_LIQUIDITY,         // Processing liquidity addition
    
    // Revoke Authority States
    AWAITING_REVOKE_MINT_AUTHORITY_CONFIRMATION, // New state for revoking mint authority
    AWAITING_REVOKE_FREEZE_AUTHORITY_CONFIRMATION, // New state for revoking freeze authority
    
    ERROR_STATE;        // Error occurred during processing

    /**
     * Check if the current state is a terminal state
     * @return true if the state is terminal, false otherwise
     */
    public boolean isTerminal() {
        return this == IDLE || this == ERROR_STATE;
    }

    /**
     * Reset the state to IDLE
     * @return IDLE state
     */
    public ChatState reset() {
        return IDLE;
    }

    /**
     * Check if the state is related to memecoin creation
     * @return true if in memecoin creation flow
     */
    public boolean isMemecoinCreation() {
        return this != null && (this == CREATE_MEMECOIN_START ||
               this == AWAITING_MEMECOIN_NAME ||
               this == AWAITING_MEMECOIN_SYMBOL ||
               this == AWAITING_MEMECOIN_DECIMALS ||
               this == AWAITING_MEMECOIN_SUPPLY ||
               this == AWAITING_MEMECOIN_IMAGE ||
               this == AWAITING_MEMECOIN_DESCRIPTION ||
               this == AWAITING_MEMECOIN_CONFIRMATION ||
               this == CREATING_MEMECOIN ||
               this == AWAITING_REVOKE_MINT_AUTHORITY_CONFIRMATION ||
               this == AWAITING_REVOKE_FREEZE_AUTHORITY_CONFIRMATION);
    }

    /**
     * Check if the state is related to memecoin trading
     * @return true if in trading flow
     */
    public boolean isMemecoinTrading() {
        return this != null && (this == TRADE_MEMECOIN_START ||
               this == AWAITING_TRADE_SYMBOL ||
               this == AWAITING_TRADE_AMOUNT ||
               this == AWAITING_TRADE_TYPE ||
               this == CONFIRMING_TRADE ||
               this == PROCESSING_TRADE);
    }

    /**
     * Check if the state is related to liquidity addition
     * @return true if in liquidity addition flow
     */
    public boolean isLiquidityAddition() {
        return this != null && (this == ADD_LIQUIDITY_START ||
               this == AWAITING_FROM_TOKEN ||
               this == AWAITING_FROM_AMOUNT ||
               this == AWAITING_TO_TOKEN ||
               this == AWAITING_TO_AMOUNT ||
               this == AWAITING_LP_BURN_AMOUNT ||
               this == AWAITING_LIQUIDITY_CONFIRMATION ||
               this == PROCESSING_LIQUIDITY);
    }
}