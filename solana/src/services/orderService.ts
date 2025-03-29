import { type CopyTrading } from '../types/database';
import { PriceService } from './priceService';
import axios from 'axios';
import { Connection, PublicKey, Keypair } from '@solana/web3.js';
import { initSdk } from '../config';
import { SolanaUtils } from '../utils/solanaUtils';
import { SwapService } from '../utils/trading/swapService';
import logger from '../utils/logger';
import bs58 from 'bs58';
import fs from 'fs';
import path from 'path';
import os from 'os';
import crypto from 'crypto';
import dotenv from 'dotenv';
import { db } from '../db';
import { wallets, encryptionKeyMetadata, limitOrders, copyTrading } from '../db/schema';
import { eq, and, inArray, desc } from 'drizzle-orm';
import { Pool } from 'pg';

// Load environment variables
dotenv.config();

// Validate required environment variables
const requiredEnvVars = [
    'RPC_URL',
    'WALLETS_PATH',
    'ENCRYPTION_KEYS_PATH',
    'HELIUS_API_KEY',
    'DB_USER',
    'DB_HOST',
    'DB_NAME',
    'DB_PASSWORD',
    'DB_PORT'
];

for (const envVar of requiredEnvVars) {
    if (!process.env[envVar]) {
        throw new Error(`Missing required environment variable: ${envVar}`);
    }
}

interface TokenAccount {
    mint: PublicKey;
    pubkey: PublicKey;
}

interface RaydiumAccount {
    owner: PublicKey;
    tokenAccounts: TokenAccount[];
}

interface SwapParams {
    inputMint: PublicKey;
    outputMint: PublicKey;
    amount: number;
    slippage: number;
    userKeys: {
        tokenAccounts: TokenAccount[];
        owner: PublicKey;
        ownerTokenAccount: PublicKey | null;
    };
}

// Define Raydium type
interface Raydium {
    account: RaydiumAccount;
    swap: (params: SwapParams) => Promise<{
        swapTransaction: any;
        signers: Keypair[];
    }>;
}

interface ActiveLimitOrder {
    tokenMint: string;
    walletId: string;
}

interface ActiveCopyTrading {
    id: number;
    sourceWallet: string;
    targetWallet: string;
    enabled: boolean;
}

type OrderAction = 'BUY' | 'SELL';

type DBLimitOrder = {
    orderId: string;
    userId: number;
    walletId: string;
    tokenMint: string;
    action: OrderAction;
    solAmount: string;
    limitPrice: string;
    slippage: string;
    status: string;
    transactionSignature: string | null;
    executedAt: Date | null;
    createdAt: Date;
    updatedAt: Date;
};

export interface LimitOrder {
    orderId: string;
    userId: number;
    walletId: string;
    tokenMint: string;
    action: OrderAction;
    solAmount: number;
    limitPrice: number;
    slippage: number;
    status: string;
    transactionSignature: string | null;
    executedAt: Date | null;
    createdAt: Date;
    updatedAt: Date;
}

export class OrderService {
    private priceService: PriceService;
    private readonly TELEGRAM_API_URL = 'http://localhost:8888/api/telegram/send';
    private connection: Connection;
    private solanaUtils: SolanaUtils;
    private swapService: SwapService;
    private copyTradingWatches: Map<number, ReturnType<typeof setInterval>>;
    private walletsPath: string;
    private encryptionKeysPath: string;
    private readonly GCM_IV_LENGTH = 12;
    private readonly GCM_TAG_LENGTH = 16;
    private priceUpdateInterval: ReturnType<typeof setInterval> | null = null;
    private pool: Pool;

    constructor(pool: Pool) {
        logger.info('Initializing OrderService...');
        this.pool = pool;
        this.priceService = new PriceService();
        
        // Use environment variables for paths
        this.walletsPath = process.env.WALLETS_PATH!;
        this.encryptionKeysPath = process.env.ENCRYPTION_KEYS_PATH!;
        
        // Ensure directories exist with proper permissions
        fs.mkdirSync(this.walletsPath, { recursive: true, mode: 0o700 });
        fs.mkdirSync(this.encryptionKeysPath, { recursive: true, mode: 0o700 });
        
        // Initialize Solana connection
        this.connection = new Connection(process.env.RPC_URL!);
        this.solanaUtils = new SolanaUtils(process.env.RPC_URL!);
        this.swapService = new SwapService(this.connection, this.solanaUtils);
        this.copyTradingWatches = new Map();
        
        logger.info('OrderService initialized successfully');
        logger.info(`Using RPC URL: ${process.env.RPC_URL}`);
        logger.info(`Wallets path: ${this.walletsPath}`);
        logger.info(`Encryption keys path: ${this.encryptionKeysPath}`);
        
        // Start monitoring
        this.startMonitoring();
        this.startPriceMonitoring();
        this.logActiveTokensAndWallets();
    }

    private async startPriceMonitoring() {
        logger.info('Starting price monitoring service...');
        
        // Start a continuous monitoring loop
        const monitorLoop = async () => {
            while (true) {
                try {
                    // Get active orders from database
                const activeOrders = await this.getActiveOrders();
                    if (activeOrders.length > 0) {
                logger.debug(`Checking ${activeOrders.length} active orders for price updates`);
                
                        // Group orders by token to avoid duplicate price checks
                        const ordersByToken = new Map<string, LimitOrder[]>();
                for (const order of activeOrders) {
                            if (!ordersByToken.has(order.tokenMint)) {
                                ordersByToken.set(order.tokenMint, []);
                            }
                            ordersByToken.get(order.tokenMint)!.push(order);
                        }
                        
                        // Check each token's price once and process all related orders
                        for (const [tokenMint, orders] of ordersByToken) {
                            try {
                                const currentPrice = this.priceService.getTokenPrice(tokenMint);
                    if (currentPrice) {
                                    logger.debug(`Price update for ${tokenMint}: $${currentPrice.price}`);
                                    // Process all orders for this token
                                    for (const order of orders) {
                        await this.checkAndExecuteOrder(order, currentPrice.price);
                                    }
                                }
                            } catch (tokenError) {
                                logger.error(`Error processing token ${tokenMint}:`, tokenError);
                                // Continue with next token even if one fails
                                continue;
                            }
                        }
                    }
                    
                    // Small delay to prevent CPU overload
                    await new Promise(resolve => setTimeout(resolve, 1000));
                } catch (error) {
                    logger.error('Error in price monitoring loop:', error);
                    // Wait a bit longer on error before retrying
                    await new Promise(resolve => setTimeout(resolve, 5000));
                }
            }
        };

        // Start the monitoring loop immediately
        monitorLoop().catch(error => {
            logger.error('Fatal error in price monitoring loop:', error);
        });

        // Also set up an interval to ensure the loop keeps running
        this.priceUpdateInterval = setInterval(() => {
            monitorLoop().catch(error => {
                logger.error('Fatal error in price monitoring loop:', error);
            });
        }, 1000);

        logger.info('Price monitoring service started');
    }

    private async startMonitoring() {
        logger.info('Starting database monitoring service...');
        
        // Get a client connection for notifications
        const client = await this.pool.connect();
        
        // Subscribe to database changes
        (client as any).on('notification', async (msg: { channel: string; payload: string }) => {
            logger.debug(`Received database notification: ${msg.channel}`);
            try {
                const payload = JSON.parse(msg.payload);
                
            if (msg.channel === 'limit_orders_changes') {
                    await this.handleLimitOrderChange(payload);
            } else if (msg.channel === 'copy_trading_changes') {
                    await this.handleCopyTradingChange(payload);
                }
            } catch (error) {
                logger.error('Error handling database notification:', error);
            }
        });

        // Subscribe to all channels
        await client.query('LISTEN limit_orders_changes');
        await client.query('LISTEN copy_trading_changes');

        // Start a continuous monitoring loop for active orders
        const monitorLoop = async () => {
            while (true) {
                try {
                    await this.checkActiveOrders();
                    await this.checkTokenUsage();
                    // Small delay to prevent CPU overload
                    await new Promise(resolve => setTimeout(resolve, 1000));
                } catch (error) {
                    logger.error('Error in database monitoring loop:', error);
                    // Wait a bit longer on error before retrying
                    await new Promise(resolve => setTimeout(resolve, 5000));
                }
            }
        };

        // Start the monitoring loop immediately
        monitorLoop().catch(error => {
            logger.error('Fatal error in database monitoring loop:', error);
        });

        // Also set up an interval to ensure the loop keeps running
        setInterval(() => {
            monitorLoop().catch(error => {
                logger.error('Fatal error in database monitoring loop:', error);
            });
        }, 1000);
        
        logger.info('Database monitoring service started');
    }

    private async logActiveTokensAndWallets() {
        try {
            // Get active limit orders
            const activeLimitOrders = await db
                .select({
                    tokenMint: limitOrders.tokenMint,
                    walletId: limitOrders.walletId
                })
                .from(limitOrders)
                .where(inArray(limitOrders.status, ['active', 'pending'])) as ActiveLimitOrder[];

            // Get active copy trading
            const activeCopyTrading = await db
                .select({
                    id: copyTrading.id,
                    sourceWallet: copyTrading.sourceWallet,
                    targetWallet: copyTrading.targetWallet,
                    enabled: copyTrading.enabled
                })
                .from(copyTrading)
                .where(eq(copyTrading.enabled, true)) as ActiveCopyTrading[];

            // Log active tokens
            logger.info('=== Active Tokens Being Monitored ===');
            const activeTokens = new Set(activeLimitOrders.map(order => order.tokenMint));
            for (const token of activeTokens) {
                const orders = activeLimitOrders.filter(order => order.tokenMint === token);
                const currentPrice = this.priceService.getTokenPrice(token);
                logger.info(`Token: ${token}`);
                logger.info(`  Current Price: $${currentPrice?.price || 'N/A'}`);
                logger.info(`  Active Orders: ${orders.length}`);
                logger.info(`  Wallet IDs: ${orders.map(o => o.walletId).join(', ')}`);
            }

            // Log copy trading wallets
            logger.info('\n=== Active Copy Trading Pairs ===');
            for (const ct of activeCopyTrading) {
                logger.info(`Copy Trading ID: ${ct.id}`);
                logger.info(`  Source Wallet: ${ct.sourceWallet}`);
                logger.info(`  Target Wallet: ${ct.targetWallet}`);
                logger.info(`  Status: ${ct.enabled ? 'Active' : 'Inactive'}`);
            }
            logger.info('=====================================\n');
        } catch (error) {
            logger.error('Error logging active tokens and wallets:', error);
        }
    }

    private async checkTokenUsage() {
        try {
            logger.debug('Checking token usage...');
            // Get all tokens currently being monitored
            const monitoredTokens = this.priceService.getActiveTokens();
            logger.debug(`Currently monitoring ${monitoredTokens.length} tokens`);
            
            // Get all tokens that are actually in use from the database
            const activeTokens = await db
                .select({ tokenMint: limitOrders.tokenMint })
                .from(limitOrders)
                .where(inArray(limitOrders.status, ['active', 'pending']));

            const activeTokenMints = activeTokens.map(t => t.tokenMint);
            logger.debug(`Found ${activeTokenMints.length} active tokens in database`);

            // Create sets for efficient lookup
            const monitoredSet = new Set(monitoredTokens);
            const activeSet = new Set(activeTokenMints);

            // Find tokens to unsubscribe (in monitored but not in active)
            const tokensToUnsubscribe = monitoredTokens.filter(token => !activeSet.has(token));
            for (const token of tokensToUnsubscribe) {
                    logger.info(`Token ${token} no longer in use, unsubscribing...`);
                    this.priceService.unsubscribeFromToken(token);
            }

            // Find tokens to subscribe (in active but not in monitored)
            const tokensToSubscribe = activeTokenMints.filter(token => !monitoredSet.has(token));
            for (const token of tokensToSubscribe) {
                    logger.info(`New token ${token} in use, subscribing...`);
                    this.priceService.subscribeToToken(token);
            }

            // Log current state after changes
            await this.logActiveTokensAndWallets();
        } catch (error) {
            logger.error('Error checking token usage:', error);
        }
    }

    private async handleLimitOrderChange(payload: any) {
        const { operation, order } = payload;
        logger.info(`Handling limit order change: ${operation} for order ${order.orderId}`);
        
        try {
        if (operation === 'INSERT') {
                logger.debug(`New limit order created:
                    Order ID: ${order.orderId}
                    User ID: ${order.userId}
                    Wallet ID: ${order.walletId}
                    Token: ${order.tokenMint}
                    Amount: ${order.solAmount} SOL
                    Limit Price: $${order.limitPrice}
                    Slippage: ${order.slippage}%
                    Status: ${order.status}
                    Created At: ${order.createdAt}`);
                
            await this.processNewLimitOrder(order);
            } else if (operation === 'UPDATE') {
                logger.debug(`Limit order updated:
                    Order ID: ${order.orderId}
                    New Status: ${order.status}
                    Updated At: ${order.updatedAt}
                    ${order.transactionSignature ? `Transaction: ${order.transactionSignature}` : ''}
                    ${order.executedAt ? `Executed At: ${order.executedAt}` : ''}`);
                
                // If order was executed or failed, check if we need to unsubscribe from token
                if (['executed', 'failed', 'cancelled'].includes(order.status)) {
                    await this.checkTokenUsage();
                }
        } else if (operation === 'DELETE') {
                logger.debug(`Limit order deleted:
                    Order ID: ${order.orderId}
                    User ID: ${order.userId}
                    Token: ${order.tokenMint}
                    Final Status: ${order.status}`);
                
                // Check if token is still in use by other orders
            await this.checkTokenUsage();
            }
        } catch (error) {
            logger.error(`Error handling limit order change for order ${order.orderId}:`, error);
        }
    }

    private async handleCopyTradingChange(payload: any) {
        const { operation, copyTrading } = payload;
        logger.info(`Handling copy trading change: ${operation} for copy trading ID ${copyTrading.id}`);
        
        try {
        if (operation === 'INSERT' && copyTrading.enabled) {
                logger.debug(`New copy trading created:
                    ID: ${copyTrading.id}
                    Source Wallet: ${copyTrading.sourceWallet}
                    Target Wallet: ${copyTrading.targetWallet}
                    Amount Multiplier: ${copyTrading.amountMultiplier}
                    Enabled: ${copyTrading.enabled}`);
                
                await this.startCopyTrading(copyTrading);
            } else if (operation === 'UPDATE') {
                logger.debug(`Copy trading updated:
                    ID: ${copyTrading.id}
                    Enabled: ${copyTrading.enabled}
                    Amount Multiplier: ${copyTrading.amountMultiplier}`);
                
                if (!copyTrading.enabled) {
                    await this.stopCopyTrading(copyTrading.id);
                } else {
            await this.startCopyTrading(copyTrading);
                }
            } else if (operation === 'DELETE') {
                logger.debug(`Copy trading deleted:
                    ID: ${copyTrading.id}
                    Source Wallet: ${copyTrading.sourceWallet}
                    Target Wallet: ${copyTrading.targetWallet}`);
                
            await this.stopCopyTrading(copyTrading.id);
            }
        } catch (error) {
            logger.error(`Error handling copy trading change for ID ${copyTrading.id}:`, error);
        }
    }

    private async processNewLimitOrder(order: LimitOrder) {
        try {
            logger.info(`Processing new limit order: ${order.orderId}`);
            
            // Subscribe to price updates for this token if not already subscribed
            if (!this.priceService.isTokenSubscribed(order.tokenMint)) {
        this.priceService.subscribeToToken(order.tokenMint);
                logger.debug(`Subscribed to price updates for token: ${order.tokenMint}`);
            } else {
                logger.debug(`Already subscribed to price updates for token: ${order.tokenMint}`);
            }

        // Check if order can be executed immediately
        const currentPrice = this.priceService.getTokenPrice(order.tokenMint);
        if (currentPrice) {
                logger.debug(`Initial price check for ${order.tokenMint}: $${currentPrice.price}`);
            await this.checkAndExecuteOrder(order, currentPrice.price);
            } else {
                logger.debug(`No initial price available for token ${order.tokenMint}, will be checked in next monitoring cycle`);
            }
        } catch (error) {
            logger.error('Error processing new limit order:', error);
            throw error;
        }
    }

    private async checkActiveOrders() {
        try {
            const activeOrders = await db
                .select()
                .from(limitOrders)
                .where(inArray(limitOrders.status, ['active', 'pending'])) as DBLimitOrder[];

            for (const dbOrder of activeOrders) {
                const order = this.convertToLimitOrder(dbOrder);
            const currentPrice = this.priceService.getTokenPrice(order.tokenMint);
                
                if (!currentPrice) {
                    logger.warn(`No price available for token ${order.tokenMint}`);
                    continue;
                }

                logger.debug(`Checking order ${order.orderId} for token: ${order.tokenMint}`);
                logger.debug(`Current price: $${currentPrice.price}, Limit price: $${order.limitPrice}`);

                const currentPriceValue = parseFloat(currentPrice.price.toString());

                if (order.action === 'BUY' && currentPriceValue <= order.limitPrice) {
                    logger.info(`Executing BUY order ${order.orderId} at price $${currentPriceValue}`);
                    await this.executeOrder(order);
                } else if (order.action === 'SELL' && currentPriceValue >= order.limitPrice) {
                    logger.info(`Executing SELL order ${order.orderId} at price $${currentPriceValue}`);
                    await this.executeOrder(order);
                }
            }
        } catch (error) {
            logger.error('Error checking active orders:', error);
            throw error;
        }
    }

    // Public method for checking and executing orders
    public async checkAndExecuteOrder(order: LimitOrder, currentPrice: number) {
        const shouldExecute = order.action === 'BUY' 
            ? currentPrice <= order.limitPrice 
            : currentPrice >= order.limitPrice;

        logger.debug(`Order ${order.orderId}: ${order.action} order at $${order.limitPrice}, current price $${currentPrice}, should execute: ${shouldExecute}`);

        if (shouldExecute) {
            await this.executeOrder(order);
        }
    }

    private async executeOrder(order: LimitOrder) {
        try {
            const privateKey = await this.getPrivateKey(order.walletId);
            if (!privateKey) {
                throw new Error(`No private key found for wallet ${order.walletId}`);
            }

            const transaction = await this.swapService.executeSwap(
                order.tokenMint,
                order.solAmount,
                order.slippage,
                privateKey
            );

            if (typeof transaction === 'string') {
                await this.updateOrderStatus(order.orderId, 'executed', transaction);
                logger.info(`Order ${order.orderId} executed successfully`);
            } else {
                throw new Error('Invalid transaction signature');
            }
        } catch (error) {
            logger.error(`Error executing order ${order.orderId}:`, error);
            await this.updateOrderStatus(order.orderId, 'failed');
        }
    }

    private async getPrivateKey(walletId: string): Promise<string> {
        try {
            // Get the active encryption key metadata
            const metadata = await db
                .select({ version: encryptionKeyMetadata.version })
                .from(encryptionKeyMetadata)
                .where(
                    and(
                        eq(encryptionKeyMetadata.walletId, walletId),
                        eq(encryptionKeyMetadata.active, true)
                    )
                )
                .limit(1);

            if (metadata.length === 0) {
                throw new Error('No active encryption key found for wallet');
            }

            const version = metadata[0].version;

            // Construct file paths
            const walletFileName = `${walletId}.v${version}.encrypted`;
            const keyFileName = `${walletId}.v${version}.key`;
            const walletPath = path.join(this.walletsPath, walletFileName);
            const keyPath = path.join(this.encryptionKeysPath, keyFileName);

            // Read the encrypted private key and encryption key
            const encryptedPrivateKey = await fs.promises.readFile(walletPath, 'utf-8');
            const encryptionKey = await fs.promises.readFile(keyPath, 'utf-8');

            // Decrypt the private key
            const privateKey = await this.decryptPrivateKey(encryptedPrivateKey, encryptionKey);

            return privateKey;
        } catch (error) {
            logger.error('Error retrieving private key:', error);
            throw new Error('Failed to retrieve private key');
        }
    }

    private async decryptPrivateKey(encryptedData: string, serializedKey: string): Promise<string> {
        try {
            // Decode the base64 encrypted data
            const decoded = Buffer.from(encryptedData, 'base64');
            
            // Extract IV (first 12 bytes)
            const iv = decoded.slice(0, this.GCM_IV_LENGTH);
            
            // Extract encrypted data (remaining bytes)
            const encrypted = decoded.slice(this.GCM_IV_LENGTH);
            
            // Deserialize the key
            const keyBuffer = Buffer.from(serializedKey, 'base64');
            const key = crypto.createSecretKey(keyBuffer);
            
            // Create decipher
            const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
            
            // Get the auth tag (last 16 bytes of encrypted data)
            const authTag = encrypted.slice(-16);
            decipher.setAuthTag(authTag);
            
            // Decrypt
            const decrypted = Buffer.concat([
                decipher.update(encrypted.slice(0, -16)),
                decipher.final()
            ]);
            
            return decrypted.toString();
        } catch (error) {
            logger.error('Error decrypting private key:', error);
            throw new Error('Failed to decrypt private key');
        }
    }

    private async updateOrderStatus(orderId: string, status: string, transactionSignature?: string) {
        try {
            await db
                .update(limitOrders)
                .set({
                    status,
                    transactionSignature: transactionSignature || null,
                    executedAt: status === 'executed' ? new Date() : null,
                    updatedAt: new Date()
                })
                .where(eq(limitOrders.orderId, orderId));
        } catch (error) {
            logger.error(`Error updating order status for ${orderId}:`, error);
        }
    }

    private async getActiveOrders(): Promise<LimitOrder[]> {
        return await db
            .select()
            .from(limitOrders)
            .where(inArray(limitOrders.status, ['active', 'pending']))
            .then(orders => orders.map(order => this.convertToLimitOrder(order)));
    }

    private convertToLimitOrder(dbOrder: DBLimitOrder): LimitOrder {
        return {
            ...dbOrder,
            solAmount: parseFloat(dbOrder.solAmount),
            limitPrice: parseFloat(dbOrder.limitPrice),
            slippage: parseFloat(dbOrder.slippage)
        };
    }

    private async startCopyTrading(copyTrading: CopyTrading) {
        try {
            logger.info(`Starting copy trading for ID ${copyTrading.id}`);
            // Get source wallet address
            const sourceWallet = await db
                .select({ walletAddress: wallets.walletAddress })
                .from(wallets)
                .where(eq(wallets.walletId, copyTrading.sourceWallet))
                .limit(1);

            if (sourceWallet.length === 0) {
                throw new Error('Source wallet not found');
            }

            const sourceWalletAddress = sourceWallet[0].walletAddress;
            logger.info(`Copy trading source wallet: ${sourceWalletAddress}`);

            // Set up monitoring for the source wallet's transactions
            const watchInterval = setInterval(async () => {
                try {
                    logger.debug(`Checking transactions for copy trading ID ${copyTrading.id}`);
                    // Get recent transactions for the source wallet
                    const transactions = await this.connection.getSignaturesForAddress(
                        new PublicKey(sourceWalletAddress),
                        { limit: 10 } // Only get the 10 most recent transactions
                    );

                    logger.debug(`Found ${transactions.length} recent transactions to check`);
                    for (const tx of transactions) {
                        // Check if transaction is a token swap
                        if (await this.isTokenSwapTransaction(tx.signature)) {
                            logger.info(`Found swap transaction ${tx.signature} to copy`);
                            await this.copySwapTransaction(tx.signature, copyTrading);
                        }
                    }
                } catch (error) {
                    logger.error('Error monitoring copy trading:', error);
                }
            }, 5000); // Check every 5 seconds

            // Store the interval ID
            this.copyTradingWatches.set(copyTrading.id, watchInterval);
            logger.info(`Copy trading monitoring started for ID ${copyTrading.id}`);
        } catch (error) {
            logger.error('Error starting copy trading:', error);
            throw error;
        }
    }

    private async stopCopyTrading(id: number) {
        logger.info(`Stopping copy trading for ID ${id}`);
        const watchInterval = this.copyTradingWatches.get(id);
        if (watchInterval) {
            clearInterval(watchInterval);
            this.copyTradingWatches.delete(id);
            logger.info(`Copy trading stopped for ID ${id}`);
        } else {
            logger.warn(`No active copy trading found for ID ${id}`);
        }
    }

    private async isTokenSwapTransaction(signature: string): Promise<boolean> {
        try {
            logger.debug(`Checking if transaction ${signature} is a token swap`);
            const tx = await this.connection.getParsedTransaction(signature, {
                maxSupportedTransactionVersion: 0,
                commitment: 'confirmed'
            });

            if (!tx || !tx.meta) {
                logger.debug(`Transaction ${signature} not found or invalid`);
                return false;
            }

            // Check if transaction involves Raydium swap program
            const RAYDIUM_SWAP_PROGRAM = '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8';
            
            // Check program IDs in the transaction
            const programIds = tx.transaction.message.instructions.map(ix => 
                'programId' in ix ? ix.programId.toString() : ''
            );

            const isSwap = programIds.includes(RAYDIUM_SWAP_PROGRAM);
            logger.debug(`Transaction ${signature} is ${isSwap ? 'a' : 'not a'} token swap`);
            return isSwap;
        } catch (error) {
            logger.error('Error checking transaction type:', error);
            return false;
        }
    }

    private async copySwapTransaction(signature: string, copyTrading: CopyTrading) {
        try {
            logger.info(`Copying swap transaction ${signature} for copy trading ID ${copyTrading.id}`);
            // Get target wallet private key
            const privateKey = await this.getPrivateKey(copyTrading.targetWallet);

            // Get the source transaction
            const sourceTx = await this.connection.getParsedTransaction(signature, {
                maxSupportedTransactionVersion: 0,
                commitment: 'confirmed'
            });

            if (!sourceTx || !sourceTx.meta) {
                throw new Error('Source transaction not found or invalid');
            }

            // Extract swap parameters from the source transaction
            const swapParams = await this.extractSwapParameters(sourceTx);
            if (!swapParams) {
                throw new Error('Could not extract swap parameters from transaction');
            }

            logger.debug(`Extracted swap parameters: ${JSON.stringify(swapParams)}`);

            // Apply the multiplier to the amount
            swapParams.amount = Math.floor(swapParams.amount * copyTrading.amountMultiplier);
            logger.debug(`Adjusted amount with multiplier ${copyTrading.amountMultiplier}x: ${swapParams.amount}`);

            // Execute the swap using SwapService
            const result = await this.swapService.executeSwap(
                swapParams.outputMint,
                swapParams.amount,
                swapParams.slippage,
                privateKey
            );

            if (!result || !result.signature) {
                throw new Error('Failed to execute copy trade');
            }

            logger.info(`Successfully copied trade from ${signature} to ${result.signature}`);

            // Send notification about copied trade
            await this.sendTelegramNotification(
                copyTrading.userId,
                `🔄 Copied trade executed!\n\n` +
                `Source TX: ${signature}\n` +
                `Copy TX: ${result.signature}\n` +
                `Amount Multiplier: ${copyTrading.amountMultiplier}x`
            );
        } catch (error) {
            logger.error('Error copying swap transaction:', error);
            throw error;
        }
    }

    private async extractSwapParameters(tx: any): Promise<{ 
        inputMint: string;
        outputMint: string;
        amount: number;
        slippage: number;
    } | null> {
        try {
            const instructions = tx.transaction.message.instructions;
            const accounts = tx.transaction.message.accountKeys;

            // Find the Raydium swap instruction
            const swapInstruction = instructions.find((ix: any) => 
                'programId' in ix && 
                ix.programId.toString() === '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8'
            );

            if (!swapInstruction) return null;

            // Parse the swap instruction data
            const inputMint = accounts[swapInstruction.accounts[1]].toString();
            const outputMint = accounts[swapInstruction.accounts[2]].toString();
            
            // Get the amount from the pre-token balances and post-token balances
            const preBalances = tx.meta.preTokenBalances;
            const postBalances = tx.meta.postTokenBalances;
            
            // Calculate the amount based on the difference in balances
            const inputTokenChange = preBalances
                .filter((b: any) => b.mint === inputMint)
                .map((b: any) => Math.abs(
                    Number(postBalances.find((p: any) => p.accountIndex === b.accountIndex)?.uiTokenAmount.uiAmount || 0) -
                    Number(b.uiTokenAmount.uiAmount || 0)
                ))[0] || 0;

            return {
                inputMint,
                outputMint,
                amount: inputTokenChange,
                slippage: 1, // Default slippage of 1%
            };
        } catch (error) {
            logger.error('Error extracting swap parameters:', error);
            return null;
        }
    }

    private async sendTelegramNotification(chatId: number, message: string) {
        try {
            await axios.post(this.TELEGRAM_API_URL, {
                chatId,
                text: message
            });
        } catch (error) {
            console.error('Error sending Telegram notification:', error);
        }
    }

    // Add cleanup method
    public cleanup() {
        if (this.priceUpdateInterval) {
            clearInterval(this.priceUpdateInterval);
            this.priceUpdateInterval = null;
        }
        for (const interval of this.copyTradingWatches.values()) {
            clearInterval(interval);
        }
        this.copyTradingWatches.clear();
    }

    // Public method for getting token price
    public async getTokenPrice(tokenMint: string): Promise<number | null> {
        try {
            const price = this.priceService.getTokenPrice(tokenMint);
            return price?.price || null;
        } catch (error) {
            logger.error('Error getting token price:', error);
            return null;
        }
    }
} 