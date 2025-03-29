import axios from 'axios';
import logger from '../utils/logger';
import dotenv from 'dotenv';
import { db } from '../db';
import { limitOrders, copyTrading } from '../db/schema';
import { eq, and, inArray } from 'drizzle-orm';

dotenv.config();

interface TokenPrice {
    price: number;
    timestamp: number;
}

interface BirdeyePriceResponse {
    value: number;
    updateUnixTime: number;
    updateHumanTime: string;
    priceInNative: number;
    priceChange24h: number;
    liquidity: number;
}

export class PriceService {
    private readonly BIRDEYE_API_URL = 'https://public-api.birdeye.so/defi';
    private priceCache: Map<string, TokenPrice>;
    private activeTokens: Set<string>;
    private updateInterval: ReturnType<typeof setInterval> | null;
    private readonly MAX_ERROR_COUNT = 5;

    constructor() {
        this.priceCache = new Map();
        this.activeTokens = new Set();
        this.updateInterval = null;
    }

    public async subscribeToToken(tokenMint: string) {
        // Check if token has errors in any active orders
        const ordersWithErrors = await db
            .select()
            .from(limitOrders)
            .where(
                and(
                    eq(limitOrders.tokenMint, tokenMint),
                    eq(limitOrders.hasError, true),
                    inArray(limitOrders.status, ['active', 'pending'])
                )
            )
            .limit(1);

        if (ordersWithErrors.length > 0) {
            const order = ordersWithErrors[0];
            if (order.errorCount >= this.MAX_ERROR_COUNT) {
                logger.warn(`Token ${tokenMint} has too many errors in active orders, skipping subscription`);
                return;
            }
        }

        logger.info(`Subscribing to price updates for token: ${tokenMint}`);
        this.activeTokens.add(tokenMint);
        this.startPriceUpdates();
    }

    public unsubscribeFromToken(tokenMint: string) {
        logger.info(`Unsubscribing from price updates for token: ${tokenMint}`);
        this.activeTokens.delete(tokenMint);
        this.priceCache.delete(tokenMint);
        
        // Stop updates if no tokens are being monitored
        if (this.activeTokens.size === 0 && this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
    }

    public getActiveTokens(): string[] {
        return Array.from(this.activeTokens);
    }

    public isTokenSubscribed(tokenMint: string): boolean {
        return this.activeTokens.has(tokenMint);
    }

    public getTokenPrice(tokenMint: string): TokenPrice | undefined {
        return this.priceCache.get(tokenMint);
    }

    private async startPriceUpdates() {
        if (this.updateInterval) return;

        // Initial price fetch
        await this.updatePrices();

        // Set up interval for price updates
        this.updateInterval = setInterval(async () => {
            await this.updatePrices();
        }, 5000); // Update every 5 seconds
    }

    private async updatePrices() {
        try {
            const tokens = Array.from(this.activeTokens);
            if (tokens.length === 0) return;

            logger.debug(`Updating prices for ${tokens.length} tokens`);

            // Process tokens in batches of 100
            for (let i = 0; i < tokens.length; i += 100) {
                const batch = tokens.slice(i, i + 100);
                logger.debug(`Processing batch ${Math.floor(i/100) + 1} of ${Math.ceil(tokens.length/100)} (${batch.length} tokens)`);

                try {
                    // Get prices from Birdeye API for this batch
                    const response = await axios.get(
                        `${this.BIRDEYE_API_URL}/multi_price`,
                        {
                            params: {
                                list_address: batch.join(',')
                            },
                            headers: {
                                'accept': 'application/json',
                                'x-chain': 'solana',
                                'x-api-key': process.env.BIRDEYE_API_KEY
                            }
                        }
                    );

                    if (!response.data.success) {
                        throw new Error('Failed to fetch prices from Birdeye API');
                    }

                    const prices = response.data.data as Record<string, BirdeyePriceResponse>;
                    
                    // Update price cache for this batch
                    for (const token of batch) {
                        try {
                            const priceData = prices[token];
                            if (priceData) {
                                this.priceCache.set(token, {
                                    price: priceData.value,
                                    timestamp: priceData.updateUnixTime * 1000
                                });
                                logger.debug(`Updated price for ${token}: $${priceData.value}`);
                                
                                // Clear errors for this token's orders
                                await db
                                    .update(limitOrders)
                                    .set({
                                        hasError: false,
                                        errorCount: 0,
                                        lastErrorAt: null,
                                        errorMessage: null
                                    })
                                    .where(
                                        and(
                                            eq(limitOrders.tokenMint, token),
                                            inArray(limitOrders.status, ['active', 'pending'])
                                        )
                                    );
                            } else {
                                await this.handleTokenError(token, 'PRICE_NOT_FOUND', 'Token price not found in Birdeye response');
                            }
                        } catch (tokenError) {
                            await this.handleTokenError(token, 'PRICE_UPDATE_ERROR', tokenError instanceof Error ? tokenError.message : 'Unknown error');
                        }
                    }

                    // Add a small delay between batches to avoid rate limiting
                    if (i + 100 < tokens.length) {
                        await new Promise(resolve => setTimeout(resolve, 100));
                    }
                } catch (batchError) {
                    logger.error(`Error processing batch ${Math.floor(i/100) + 1}:`, batchError);
                    // Handle errors for all tokens in this batch
                    for (const token of batch) {
                        await this.handleTokenError(token, 'API_ERROR', batchError instanceof Error ? batchError.message : 'Unknown error');
                    }
                }
            }
        } catch (error) {
            logger.error('Error updating token prices:', error);
            // Handle errors for all tokens
            for (const token of this.activeTokens) {
                await this.handleTokenError(token, 'API_ERROR', error instanceof Error ? error.message : 'Unknown error');
            }
        }
    }

    private async handleTokenError(tokenMint: string, errorType: string, errorMessage: string) {
        try {
            // First get current error count
            const ordersWithErrors = await db
                .select()
                .from(limitOrders)
                .where(
                    and(
                        eq(limitOrders.tokenMint, tokenMint),
                        inArray(limitOrders.status, ['active', 'pending'])
                    )
                );

            // Update all active orders for this token
            for (const order of ordersWithErrors) {
                await db
                    .update(limitOrders)
                    .set({
                        hasError: true,
                        errorCount: order.errorCount + 1,
                        lastErrorAt: new Date(),
                        errorMessage: `${errorType}: ${errorMessage}`
                    })
                    .where(eq(limitOrders.orderId, order.orderId));
            }

            // Check if any orders have exceeded error threshold
            const updatedOrders = await db
                .select()
                .from(limitOrders)
                .where(
                    and(
                        eq(limitOrders.tokenMint, tokenMint),
                        eq(limitOrders.hasError, true),
                        inArray(limitOrders.status, ['active', 'pending'])
                    )
                );

            // If any order has exceeded error threshold, unsubscribe from token
            if (updatedOrders.some(order => order.errorCount >= this.MAX_ERROR_COUNT)) {
                this.unsubscribeFromToken(tokenMint);
                logger.warn(`Unsubscribed from token ${tokenMint} due to too many errors`);
            }
        } catch (error) {
            logger.error(`Error handling token error for ${tokenMint}:`, error);
        }
    }

    public cleanup() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
        this.priceCache.clear();
        this.activeTokens.clear();
    }
} 