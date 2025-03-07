import { Connection, PublicKey, Keypair, Transaction, VersionedTransaction, ComputeBudgetProgram } from '@solana/web3.js';
import { SolanaUtils } from '../solanaUtils';
import logger from '../logger';
import bs58 from 'bs58';
import { 
    WSOLMint,
    API_URLS
} from '@raydium-io/raydium-sdk-v2';
import axios from 'axios';
import type { LiquidityPoolKeys } from '@raydium-io/raydium-sdk-v2';
import { TOKEN_PROGRAM_ID } from '@solana/spl-token';

// Custom URL for Raydium liquidity pools
const LIQUIDITY_URL = '/v2/ammV3/pools';

// Interface for the swap computation response
interface SwapCompute {
    id: string;
    success: true;
    version: 'V0' | 'V1' | 'LEGACY';
    openTime?: undefined;
    msg: undefined;
    data: {
        swapType: 'BaseIn' | 'BaseOut';
        inputMint: string;
        inputAmount: string;
        outputMint: string;
        outputAmount: string;
        otherAmountThreshold: string;
        slippageBps: number;
        priceImpactPct: number;
        routePlan: {
            poolId: string;
            inputMint: string;
            outputMint: string;
            feeMint: string;
            feeRate: number;
            feeAmount: string;
        }[];
    };
}

// Add custom error types
export class InsufficientFundsError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'InsufficientFundsError';
    }
}

export class InvalidAddressError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'InvalidAddressError';
    }
}

export class SwapService {
    private connection: Connection;
    private solanaUtils: SolanaUtils;

    constructor(connection: Connection, solanaUtils: SolanaUtils) {
        this.connection = connection;
        this.solanaUtils = solanaUtils;
    }

    /**
     * Validates if a string is a proper Solana public key
     */
    private async isValidSolanaAddress(address: string): Promise<boolean> {
        try {
            const pubKey = new PublicKey(address);
            // For token mints, verify it's actually a token mint account
            const accountInfo = await this.connection.getAccountInfo(pubKey);
            return accountInfo !== null && accountInfo.owner.equals(TOKEN_PROGRAM_ID);
        } catch {
            return false;
        }
    }

    /**
     * Validates a private key
     */
    private isValidPrivateKey(privateKey: string): boolean {
        try {
            const decoded = bs58.decode(privateKey);
            return decoded.length === 64; // Valid private keys are 64 bytes
        } catch {
            return false;
        }
    }

    /**
     * Validates wallet has sufficient SOL balance
     */
    private async checkSufficientBalance(
        walletPubkey: PublicKey, 
        requiredAmount: number
    ): Promise<void> {
        const balance = await this.connection.getBalance(walletPubkey);
        if (balance < requiredAmount) {
            throw new InsufficientFundsError(
                `Insufficient SOL balance. Required: ${requiredAmount / 1e9} SOL, Available: ${balance / 1e9} SOL`
            );
        }
    }

    /**
     * Get token decimals
     */
    private async getTokenDecimals(tokenMint: string): Promise<number> {
        try {
            const mintInfo = await this.connection.getParsedAccountInfo(new PublicKey(tokenMint));
            if (!mintInfo.value?.data || typeof mintInfo.value.data !== 'object') {
                throw new Error('Failed to fetch token info');
            }
            return (mintInfo.value.data as any).parsed.info.decimals;
        } catch (error) {
            logger.error(`Failed to get token decimals: ${error}`);
            throw error;
        }
    }

    /**
     * Execute a swap of SOL for a specific token
     * @param tokenMint The token address to buy
     * @param solAmount Amount of SOL to spend in lamports
     * @param slippage Slippage tolerance percentage
     * @param walletPrivateKey Private key of the wallet making the purchase
     * @param priorityFee Transaction priority fee in microlamports
     */
    async executeSwap(
        tokenMint: string,
        solAmount: number,
        slippage: number,
        walletPrivateKey: string,
        priorityFee: number = 100000
    ) {
        try {
            // Enhanced input validation
            if (!await this.isValidSolanaAddress(tokenMint)) {
                throw new InvalidAddressError(`Invalid token mint address: ${tokenMint}`);
            }

            if (!this.isValidPrivateKey(walletPrivateKey)) {
                throw new Error("Invalid wallet private key format");
            }

            // Validate slippage
            if (slippage < 0 || slippage > 100) {
                throw new Error("Slippage must be between 0 and 100");
            }

            const keypair = Keypair.fromSecretKey(bs58.decode(walletPrivateKey));
            const amountInLamports = Math.floor(solAmount * 1e9);

            // Check if wallet has sufficient SOL
            await this.checkSufficientBalance(keypair.publicKey, amountInLamports);

            // Get token decimals for accurate amount calculation
            const tokenDecimals = await this.getTokenDecimals(tokenMint);

            // Get latest blockhash once for all transactions
            const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash();

            logger.debug(`Starting swap execution...`);
            logger.debug(`Parameters: tokenMint=${tokenMint}, solAmount=${solAmount}, slippage=${slippage}, priorityFee=${priorityFee}`);
            
            // Step 1: Get the swap computation data
            const { data: swapResponse } = await axios.get<SwapCompute>(
                `${API_URLS.SWAP_HOST}/compute/swap-base-in?inputMint=${WSOLMint.toBase58()}&outputMint=${tokenMint}&amount=${amountInLamports}&slippageBps=${slippage * 100}&txVersion=V0`
            );
            logger.debug(`Swap computation response: ${JSON.stringify(swapResponse)}`);
            
            if (!swapResponse.success) {
                throw new Error(`Swap computation failed: ${swapResponse.msg}`);
            }
            
            logger.debug(`Swap computation result: Expected output amount: ${swapResponse.data.outputAmount}`);
            
            // Step 2: Get transaction for the swap
            const { data: swapTransactions } = await axios.post<{
                id: string;
                version: string;
                success: boolean;
                data: { transaction: string }[];
            }>(`${API_URLS.SWAP_HOST}/transaction/swap-base-in`, {
                computeUnitPriceMicroLamports: String(priorityFee),
                swapResponse,
                txVersion: 'V0', // Use versioned transactions
                wallet: keypair.publicKey.toBase58(),
                wrapSol: true, // We're starting with SOL, so we need to wrap it
                unwrapSol: false, // We're receiving a token, not SOL
            });
            logger.debug(`Swap transaction response: ${JSON.stringify(swapTransactions)}`);
            
            if (!swapTransactions.success) {
                throw new Error('Failed to generate swap transaction');
            }
            
            // Step 3: Execute all transactions in the sequence
            const txids: string[] = [];
            const allTxBuf = swapTransactions.data.map(tx => Buffer.from(tx.transaction, 'base64'));
            logger.debug(`Total transactions to execute: ${allTxBuf.length}`);
            
            for (let i = 0; i < allTxBuf.length; i++) {
                const txBuf = allTxBuf[i];
                const transaction = VersionedTransaction.deserialize(txBuf);
                
                // Sign the transaction
                transaction.sign([keypair]);
                logger.debug(`Transaction ${i + 1} signed`);
                
                // Send the transaction
                const txid = await this.connection.sendTransaction(transaction, {
                    skipPreflight: false,
                    preflightCommitment: 'confirmed',
                    maxRetries: 3
                });
                
                // Wait for confirmation
                await this.connection.confirmTransaction({
                    blockhash,
                    lastValidBlockHeight,
                    signature: txid,
                });
                
                logger.debug(`Transaction ${i + 1} confirmed`);
                txids.push(txid);
            }
            
            // Calculate accurate output amount using correct decimals
            const outputAmount = parseFloat(swapResponse.data.outputAmount) / Math.pow(10, tokenDecimals);
            const price = (outputAmount * Math.pow(10, 9)) / amountInLamports;

            return {
                success: true,
                signature: txids[txids.length - 1],
                amountOut: outputAmount,
                price,
                priceInSol: 1 / price,
                txids
            };
        } catch (error) {
            if (error instanceof InsufficientFundsError) {
                return {
                    success: false,
                    error: error.message,
                    errorType: 'INSUFFICIENT_FUNDS'
                };
            }

            if (error instanceof InvalidAddressError) {
                return {
                    success: false,
                    error: error.message,
                    errorType: 'INVALID_ADDRESS'
                };
            }

            logger.error('Swap execution error:', error);
            return {
                success: false,
                error: error instanceof Error ? error.message : String(error),
                errorType: 'UNKNOWN'
            };
        }
    }

    /**
     * Create a limit order to buy a token at a specific price
     */
    async createLimitOrder(
        tokenMint: string,
        solAmount: number,
        limitPrice: number,
        slippage: number,
        walletPrivateKey: string
    ) {
        // Validate the tokenMint address
        if (!this.isValidSolanaAddress(tokenMint)) {
            logger.error(`Invalid token address: ${tokenMint}`);
            throw new Error("Invalid token address format. A Solana token address should be a Base58 encoded string typically 32-44 characters long.");
        }

        try {
            logger.info(`Creating limit order: ${solAmount} SOL for token ${tokenMint} at price ${limitPrice} with ${slippage}% slippage`);
            
            // Also validate the wallet private key
            if (!this.isValidSolanaAddress(walletPrivateKey)) {
                throw new Error("Invalid wallet private key format");
            }
            
            // Generate a unique order ID
            const orderId = `order_${Date.now()}_${Math.random().toString(36).substring(2, 15)}`;
            
            // For now, just return the order ID and mock success
            // In a real implementation, you would store this in a database and monitor price
            return {
                success: true,
                orderId,
                status: 'active',
                tokenMint,
                solAmount,
                limitPrice,
                createdAt: new Date().toISOString()
            };
        } catch (error) {
            logger.error('Limit order creation error:', error);
            return {
                success: false,
                error: error instanceof Error ? error.message : String(error)
            };
        }
    }

    /**
     * Find a pool for a token
     */
    private async findPool(tokenMint: string): Promise<LiquidityPoolKeys | null> {
        try {
            logger.info(`Finding pool for token ${tokenMint}`);
            
            // Use Raydium V3 API to find the pool
            const response = await axios.get(
                `https://api-v3.raydium.io/pools/info/mint?mint1=${tokenMint}&poolType=all&poolSortField=default&sortType=desc&pageSize=1&page=1`,
                {
                    headers: {
                        'accept': 'application/json'
                    }
                }
            );
            
            if (response.data && response.data.data && response.data.data.length > 0) {
                const poolData = response.data.data[0];
                logger.info(`Found pool for token ${tokenMint}: ${poolData.id}`);
                
                return {
                    id: new PublicKey(poolData.id),
                    baseMint: new PublicKey(tokenMint),
                    quoteMint: new PublicKey(poolData.mint2), // Usually WSOL
                    lpMint: new PublicKey(poolData.lpMint),
                    baseDecimals: poolData.decimals1,
                    quoteDecimals: poolData.decimals2,
                    // Add other required fields
                } as LiquidityPoolKeys;
            }
            
            logger.warn(`No pool found for token ${tokenMint}`);
            return null;
        } catch (error) {
            logger.error('Error finding pool:', error);
            return null;
        }
    }

    // Method to use birdeye API to find pool
    private async findPoolFromBirdeye(tokenMint: string): Promise<LiquidityPoolKeys | null> {
        try {
            // Use Birdeye API to get pool info
            const response = await fetch(`https://public-api.birdeye.so/defi/raydium_pools?token_address=${tokenMint}`);
            const data = await response.json();
            
            if (data.success && data.data && data.data.length > 0) {
                const poolData = data.data[0];
                logger.info(`Found pool for token ${tokenMint} via Birdeye API`);
                
                // Convert to Raydium pool keys format
                return {
                    id: new PublicKey(poolData.pool_id),
                    baseMint: new PublicKey(tokenMint),
                    quoteMint: new PublicKey(WSOLMint),
                    lpMint: new PublicKey(poolData.lp_mint),
                    baseDecimals: poolData.token_decimals,
                    quoteDecimals: 9, // SOL has 9 decimals
                    // Other required fields
                } as LiquidityPoolKeys;
            }
            
            logger.warn(`No pool found for token ${tokenMint} via Birdeye API`);
            return null;
        } catch (error) {
            logger.error('Error finding pool via Birdeye:', error);
            return null;
        }
    }
} 