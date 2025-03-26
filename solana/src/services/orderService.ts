import { Pool } from 'pg';
import { type LimitOrder, type CopyTrading } from '../types/database';
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

export class OrderService {
    private pool: Pool;
    private priceService: PriceService;
    private readonly TELEGRAM_API_URL = 'http://localhost:8888/api/telegram/send';
    private connection: Connection;
    private solanaUtils: SolanaUtils;
    private swapService: SwapService;
    private copyTradingWatches: Map<number, ReturnType<typeof setInterval>>;
    private readonly walletsPath: string;
    private readonly encryptionKeysPath: string;
    private readonly GCM_IV_LENGTH = 12;
    private readonly GCM_TAG_LENGTH = 128;

    constructor(pool: Pool) {
        this.pool = pool;
        this.priceService = new PriceService();
        
        // Ensure RPC_URL is defined
        const rpcUrl = process.env.RPC_URL;
        if (!rpcUrl) {
            throw new Error('RPC_URL environment variable is required');
        }
        
        this.connection = new Connection(rpcUrl);
        this.solanaUtils = new SolanaUtils(rpcUrl);
        this.swapService = new SwapService(this.connection, this.solanaUtils);
        this.copyTradingWatches = new Map();
        
        // Set up paths with proper defaults
        const defaultConfigPath = path.join(os.homedir(), '.config', 'solana');
        this.walletsPath = process.env.WALLETS_PATH ?? path.join(defaultConfigPath, 'wallets');
        this.encryptionKeysPath = process.env.ENCRYPTION_KEYS_PATH ?? path.join(defaultConfigPath, 'encryption_keys');
        
        // Ensure directories exist
        fs.mkdirSync(this.walletsPath, { recursive: true });
        fs.mkdirSync(this.encryptionKeysPath, { recursive: true });
        
        this.startMonitoring();
    }

    private async startMonitoring() {
        // Get a client connection for notifications
        const client = await this.pool.connect();
        
        // Subscribe to database changes
        (client as any).on('notification', async (msg: { channel: string; payload: string }) => {
            if (msg.channel === 'limit_orders_changes') {
                await this.handleLimitOrderChange(JSON.parse(msg.payload));
            } else if (msg.channel === 'copy_trading_changes') {
                await this.handleCopyTradingChange(JSON.parse(msg.payload));
            }
        });

        // Subscribe to all channels
        await client.query('LISTEN limit_orders_changes');
        await client.query('LISTEN copy_trading_changes');

        // Start monitoring active orders
        setInterval(() => this.checkActiveOrders(), 1000);
    }

    private async handleLimitOrderChange(payload: any) {
        const { operation, order } = payload;
        
        if (operation === 'INSERT') {
            await this.processNewLimitOrder(order);
        } else if (operation === 'DELETE') {
            this.priceService.unsubscribeFromToken(order.tokenMint);
        }
    }

    private async handleCopyTradingChange(payload: any) {
        const { operation, copyTrading } = payload;
        
        if (operation === 'INSERT' && copyTrading.enabled) {
            await this.startCopyTrading(copyTrading);
        } else if (operation === 'UPDATE' && !copyTrading.enabled) {
            await this.stopCopyTrading(copyTrading.id);
        }
    }

    private async processNewLimitOrder(order: LimitOrder) {
        // Subscribe to price updates for this token
        this.priceService.subscribeToToken(order.tokenMint);

        // Check if order can be executed immediately
        const currentPrice = this.priceService.getTokenPrice(order.tokenMint);
        if (currentPrice) {
            await this.checkAndExecuteOrder(order, currentPrice.price);
        }
    }

    private async checkActiveOrders() {
        const activeOrders = await this.getActiveOrders();
        for (const order of activeOrders) {
            const currentPrice = this.priceService.getTokenPrice(order.tokenMint);
            if (currentPrice) {
                await this.checkAndExecuteOrder(order, currentPrice.price);
            }
        }
    }

    private async checkAndExecuteOrder(order: LimitOrder, currentPrice: number) {
        const shouldExecute = order.action === 'BUY' 
            ? currentPrice <= order.limitPrice 
            : currentPrice >= order.limitPrice;

        if (shouldExecute) {
            await this.executeOrder(order, currentPrice);
        }
    }

    private async executeOrder(order: LimitOrder, executedPrice: number) {
        try {
            // Execute the trade on Solana
            const transactionSignature = await this.executeSolanaTrade(order);
            
            // Update order status
            await this.updateOrderStatus(order.orderId, 'executed', transactionSignature, executedPrice);
            
            // Send notification to user
            await this.sendTelegramNotification(
                order.userId,
                `✅ Limit order executed!\n\n` +
                `Order ID: ${order.orderId}\n` +
                `Action: ${order.action}\n` +
                `Amount: ${order.solAmount} SOL\n` +
                `Executed Price: $${executedPrice}\n` +
                `Transaction: ${transactionSignature}`
            );
        } catch (error: any) {
            console.error('Error executing order:', error);
            await this.updateOrderStatus(order.orderId, 'failed');
            
            await this.sendTelegramNotification(
                order.userId,
                `❌ Limit order failed!\n\n` +
                `Order ID: ${order.orderId}\n` +
                `Action: ${order.action}\n` +
                `Amount: ${order.solAmount} SOL\n` +
                `Error: ${error.message}`
            );
        }
    }

    private async executeSolanaTrade(order: LimitOrder): Promise<string> {
        try {
            // Get the wallet private key
            const privateKey = await this.getPrivateKey(order.walletId);

            // Execute the swap using SwapService
            const result = await this.swapService.executeSwap(
                order.tokenMint,
                order.solAmount,
                order.slippage,
                privateKey
            );

            if (!result || !result.signature) {
                throw new Error('Swap failed: No transaction signature returned');
            }

            return result.signature;
        } catch (error) {
            logger.error('Error executing Solana trade:', error);
            throw error;
        }
    }

    private async getPrivateKey(walletId: string): Promise<string> {
        try {
            // Get the active encryption key metadata
            const metadataQuery = `
                SELECT version 
                FROM encryption_key_metadata 
                WHERE wallet_id = $1 AND active = true
            `;
            const metadataResult = await this.pool.query(metadataQuery, [walletId]);
            
            if (metadataResult.rows.length === 0) {
                throw new Error('No active encryption key found for wallet');
            }

            const version = metadataResult.rows[0].version;

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

    private async updateOrderStatus(
        orderId: string, 
        status: LimitOrder['status'], 
        transactionSignature?: string,
        executedPrice?: number
    ) {
        const query = `
            UPDATE limit_orders 
            SET status = $1, 
                executed_at = CASE WHEN $1 = 'executed' THEN NOW() ELSE executed_at END,
                transaction_signature = COALESCE($2, transaction_signature)
            WHERE order_id = $3
        `;
        await this.pool.query(query, [status, transactionSignature, orderId]);
    }

    private async getActiveOrders(): Promise<LimitOrder[]> {
        const query = `
            SELECT * FROM limit_orders 
            WHERE status IN ('active', 'pending')
        `;
        const result = await this.pool.query(query);
        return result.rows;
    }

    private async startCopyTrading(copyTrading: CopyTrading) {
        try {
            // Get source wallet address
            const sourceWalletQuery = 'SELECT wallet_address FROM wallets WHERE wallet_id = $1';
            const sourceWalletResult = await this.pool.query(sourceWalletQuery, [copyTrading.sourceWallet]);
            
            if (sourceWalletResult.rows.length === 0) {
                throw new Error('Source wallet not found');
            }

            const sourceWalletAddress = sourceWalletResult.rows[0].wallet_address;

            // Set up monitoring for the source wallet's transactions
            const watchInterval = setInterval(async () => {
                try {
                    // Get recent transactions for the source wallet
                    const transactions = await this.connection.getSignaturesForAddress(
                        new PublicKey(sourceWalletAddress),
                        { limit: 10 } // Only get the 10 most recent transactions
                    );

                    for (const tx of transactions) {
                        // Check if transaction is a token swap
                        if (await this.isTokenSwapTransaction(tx.signature)) {
                            await this.copySwapTransaction(tx.signature, copyTrading);
                        }
                    }
                } catch (error) {
                    logger.error('Error monitoring copy trading:', error);
                }
            }, 5000); // Check every 5 seconds

            // Store the interval ID
            this.copyTradingWatches.set(copyTrading.id, watchInterval);
        } catch (error) {
            logger.error('Error starting copy trading:', error);
            throw error;
        }
    }

    private async stopCopyTrading(id: number) {
        const watchInterval = this.copyTradingWatches.get(id);
        if (watchInterval) {
            clearInterval(watchInterval);
            this.copyTradingWatches.delete(id);
        }
    }

    private async isTokenSwapTransaction(signature: string): Promise<boolean> {
        try {
            const tx = await this.connection.getParsedTransaction(signature, {
                maxSupportedTransactionVersion: 0,
                commitment: 'confirmed'
            });

            if (!tx || !tx.meta) return false;

            // Check if transaction involves Raydium swap program
            const RAYDIUM_SWAP_PROGRAM = '675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8';
            
            // Check program IDs in the transaction
            const programIds = tx.transaction.message.instructions.map(ix => 
                'programId' in ix ? ix.programId.toString() : ''
            );

            return programIds.includes(RAYDIUM_SWAP_PROGRAM);
        } catch (error) {
            logger.error('Error checking transaction type:', error);
            return false;
        }
    }

    private async copySwapTransaction(signature: string, copyTrading: CopyTrading) {
        try {
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

            // Apply the multiplier to the amount
            swapParams.amount = Math.floor(swapParams.amount * copyTrading.amountMultiplier);

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

            // Send notification about copied trade
            await this.sendTelegramNotification(
                copyTrading.userId,
                `🔄 Copied trade executed!\n\n` +
                `Source TX: ${signature}\n` +
                `Copy TX: ${result.signature}\n` +
                `Amount Multiplier: ${copyTrading.amountMultiplier}x`
            );

            logger.info(`Successfully copied trade from ${signature} to ${result.signature}`);
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
} 