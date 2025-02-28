import { Connection, PublicKey } from '@solana/web3.js';
import { SolanaUtils } from '../solanaUtils';
import logger from '../logger';
import { createAmmPool } from '../raydium/CreateStandardAmmPool';
import { addLiquidity } from '../raydium/AddLiquidity';
import { burnLiquidity } from '../raydium/BurnLiquidity';
import { removeLiquidity } from '../raydium/RemoveLiquidity';

export class LiquidityService {
    private connection: Connection;
    private solanaUtils: SolanaUtils;

    constructor(connection: Connection, solanaUtils: SolanaUtils) {
        this.connection = connection;
        this.solanaUtils = solanaUtils;
    }

    async createPool(
        fromToken: string,    // SOL token address or "SOL"
        toToken: string,      // Your token address
        fromAmount: number,   // SOL amount
        toAmount: number,     // Your token amount
        walletPrivateKey: string,
        decimals: number
    ): Promise<string> {
        try {
            logger.info('Creating AMM pool...');
            const poolId = await createAmmPool(
                fromToken,
                toToken,
                fromAmount,
                toAmount,
                walletPrivateKey,
                decimals
            );
            logger.info(`AMM pool created with ID: ${poolId}`);
            return poolId;
        } catch (error) {
            logger.error('Error creating AMM pool:', error);
            throw new Error(`Failed to create AMM pool: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    async addLiquidity(
        tokenMint: string,
        tokenAmount: number,
        solAmount: number,
        walletPrivateKey: string
    ): Promise<string> {
        try {
            logger.info('Adding liquidity...');
            const signature = await addLiquidity(
                tokenMint,
                tokenAmount,
                solAmount,
                walletPrivateKey
            );
            logger.info(`Liquidity added with signature: ${signature}`);
            return signature;
        } catch (error) {
            logger.error('Error adding liquidity:', error);
            throw new Error(`Failed to add liquidity: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    async burnLiquidity(
        poolId: string,
        burnPercentage: number,
        walletPrivateKey: string
    ): Promise<string> {
        try {
            logger.info(`Burning ${burnPercentage}% of liquidity from pool ${poolId}...`);
            const signature = await burnLiquidity(poolId, burnPercentage, walletPrivateKey);
            logger.info(`Liquidity burned with signature: ${signature}`);
            return signature;
        } catch (error) {
            logger.error('Error burning liquidity:', error);
            throw new Error(`Failed to burn liquidity: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    async removeLiquidity(
        poolId: string,
        removePercentage: number,
        walletPrivateKey: string
    ): Promise<string> {
        try {
            logger.info(`Removing ${removePercentage}% of liquidity from pool ${poolId}...`);
            const signature = await removeLiquidity(poolId, removePercentage, walletPrivateKey);
            logger.info(`Liquidity removed with signature: ${signature}`);
            return signature;
        } catch (error) {
            logger.error('Error removing liquidity:', error);
            throw new Error(`Failed to remove liquidity: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
}