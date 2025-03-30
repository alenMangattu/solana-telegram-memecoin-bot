import {
    Connection,
    PublicKey,
    Transaction,
    sendAndConfirmTransaction,
    Keypair,
    ComputeBudgetProgram
} from "@solana/web3.js";
import { burnChecked, createBurnCheckedInstruction } from "@solana/spl-token";
import { initSdk } from '../../config'
import bs58 from 'bs58';
import logger from '../logger';

export const burnLiquidity = async (
    poolId: string,
    burnPercentage: number, // 0-100 percentage of tokens to burn
    walletPrivateKey: string,
    priorityFee: number = 46591500 // Default priority fee
) => {
    try {
        const raydium = await initSdk()
        const connection = raydium.connection;

        logger.info('Burning liquidity with params:', { poolId, burnPercentage })

        // Get pool info to get the LP token mint and user's LP token account
        const poolInfo = await raydium.liquidity.getPoolInfoFromRpc({ poolId });
        
        if (!poolInfo || !poolInfo.poolInfo) {
            throw new Error("Pool info not found");
        }

        // Convert private key from base58 to Keypair
        const walletKeypair = Keypair.fromSecretKey(bs58.decode(walletPrivateKey));
        
        // Get user's LP token account
        const lpTokenAccount = await connection.getTokenAccountsByOwner(
            walletKeypair.publicKey,
            { mint: new PublicKey(poolInfo.poolInfo.lpMint.toString()) }
        );

        if (lpTokenAccount.value.length === 0) {
            throw new Error("No LP token account found for this wallet");
        }

        // Get current balance
        const tokenBalance = await connection.getTokenAccountBalance(
            lpTokenAccount.value[0].pubkey
        );

        if (!tokenBalance.value.uiAmount || tokenBalance.value.uiAmount <= 0) {
            throw new Error("No LP tokens available to burn");
        }

        // Calculate amount to burn based on percentage
        const amountToBurn = Math.floor(
            (tokenBalance.value.uiAmount || 0) * (burnPercentage / 100) * Math.pow(10, tokenBalance.value.decimals)
        );

        if (amountToBurn <= 0) {
            throw new Error("Invalid burn amount calculated");
        }

        logger.info('Calculated burn amount:', {
            currentBalance: tokenBalance.value.uiAmount,
            burnPercentage,
            amountToBurn
        });

        // Create compute budget instructions
        const computeBudgetIx = ComputeBudgetProgram.setComputeUnitPrice({
            microLamports: priorityFee
        });
        const computeBudgetLimitIx = ComputeBudgetProgram.setComputeUnitLimit({
            units: 600000
        });

        // Create burn instruction
        const burnInstruction = createBurnCheckedInstruction(
            lpTokenAccount.value[0].pubkey,
            new PublicKey(poolInfo.poolInfo.lpMint.toString()),
            walletKeypair.publicKey,
            amountToBurn,
            tokenBalance.value.decimals
        );

        // Create and send transaction
        const transaction = new Transaction()
            .add(computeBudgetIx)
            .add(computeBudgetLimitIx)
            .add(burnInstruction);
        
        const txId = await sendAndConfirmTransaction(
            connection,
            transaction,
            [walletKeypair],
            {
                skipPreflight: false,
                preflightCommitment: 'confirmed',
                maxRetries: 5
            }
        );

        logger.info('Liquidity burned successfully:', { txId });
        return txId;
    } catch (error) {
        logger.error('Error burning LP tokens:', error);
        throw error;
    }
} 