import {
    Connection,
    PublicKey,
    Transaction,
    sendAndConfirmTransaction,
    Keypair
} from "@solana/web3.js";
import { burnChecked, createBurnCheckedInstruction } from "@solana/spl-token";
import { initSdk } from '../../config'
import bs58 from 'bs58';

export const burnLiquidity = async (
    poolId: string,
    burnPercentage: number, // 0-100 percentage of tokens to burn
    walletPrivateKey: string
) => {
    const raydium = await initSdk()
    const connection = raydium.connection;

    try {
        // Get pool info to get the LP token mint and user's LP token account
        const poolInfo = await raydium.liquidity.getPoolInfoFromRpc({ poolId });
        
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

        // Calculate amount to burn based on percentage
        const amountToBurn = Math.floor(
            (tokenBalance.value.uiAmount || 0) * (burnPercentage / 100) * Math.pow(10, tokenBalance.value.decimals)
        );

        // Create burn instruction
        const burnInstruction = createBurnCheckedInstruction(
            lpTokenAccount.value[0].pubkey,
            new PublicKey(poolInfo.poolInfo.lpMint.toString()),
            walletKeypair.publicKey,
            amountToBurn,
            tokenBalance.value.decimals
        );

        // Create and send transaction
        const transaction = new Transaction().add(burnInstruction);
        
        const txId = await sendAndConfirmTransaction(
            connection,
            transaction,
            [walletKeypair]
        );

        return txId;
    } catch (error) {
        console.error('Error burning LP tokens:', error);
        throw error;
    }
} 