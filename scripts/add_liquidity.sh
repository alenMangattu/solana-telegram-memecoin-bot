#!/bin/bash

# Check if required arguments are provided
if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <keypair_path> <token_address> <sol_amount> <token_amount>"
    echo "Example: $0 ~/.config/solana/id.json TokenAddress123 1.5 1000000"
    exit 1
fi

KEYPAIR_PATH=$1
TOKEN_ADDRESS=$2
SOL_AMOUNT=$3
TOKEN_AMOUNT=$4

# Set environment to devnet
solana config set --url devnet

# Create token account for the token if it doesn't exist
echo "Creating token account..."
TOKEN_ACCOUNT=$(spl-token create-account $TOKEN_ADDRESS -p TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb --url devnet --fee-payer $KEYPAIR_PATH)

# Get token balance
BALANCE=$(spl-token balance $TOKEN_ADDRESS --url devnet)

if (( $(echo "$BALANCE < $TOKEN_AMOUNT" | bc -l) )); then
    echo "Error: Not enough tokens. Balance: $BALANCE, Required: $TOKEN_AMOUNT"
    exit 1
fi

# Get SOL balance
SOL_BALANCE=$(solana balance --url devnet | awk '{print $1}')

if (( $(echo "$SOL_BALANCE < $SOL_AMOUNT" | bc -l) )); then
    echo "Error: Not enough SOL. Balance: $SOL_BALANCE SOL, Required: $SOL_AMOUNT SOL"
    exit 1
fi

echo "Creating liquidity pool..."
# Create liquidity pool instructions would go here
# This is a placeholder as Raydium's CLI tools are not publicly available
# Users should use Raydium's UI to create liquidity pools

echo """
To add liquidity to your token:
1. Visit https://raydium.io/liquidity/create/
2. Connect your wallet
3. Select your token: $TOKEN_ADDRESS
4. Enter the amounts:
   - SOL: $SOL_AMOUNT
   - Token: $TOKEN_AMOUNT
5. Click 'Create Pool' and approve the transaction

Note: Make sure you have enough SOL for transaction fees
"""
