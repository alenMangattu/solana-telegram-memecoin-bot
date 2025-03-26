import axios from 'axios';
import logger from '../utils/logger';
import dotenv from 'dotenv';

dotenv.config();

interface TokenPrice {
    price: number;
    timestamp: number;
}

export class PriceService {
    private readonly HELIUS_API_URL = 'https://api.helius.xyz/v0';
    private readonly HELIUS_API_KEY: string;
    private priceCache: Map<string, TokenPrice>;
    private activeTokens: Set<string>;
    private updateInterval: ReturnType<typeof setInterval> | null;

    constructor() {
        if (!process.env.HELIUS_API_KEY) {
            throw new Error('HELIUS_API_KEY is not defined in environment variables');
        }
        this.HELIUS_API_KEY = process.env.HELIUS_API_KEY;
        this.priceCache = new Map();
        this.activeTokens = new Set();
        this.updateInterval = null;
    }

    public subscribeToToken(tokenMint: string) {
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

            // Get prices from Helius API
            const response = await axios.post(
                `${this.HELIUS_API_URL}/token-prices`,
                {
                    tokens: tokens.map(mint => ({ mint })),
                    includeOffChain: true
                },
                {
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': `Bearer ${this.HELIUS_API_KEY}`
                    }
                }
            );

            const prices = response.data;
            
            // Update price cache
            for (const token of tokens) {
                const priceData = prices.find((p: any) => p.mint === token);
                if (priceData) {
                    this.priceCache.set(token, {
                        price: priceData.price,
                        timestamp: Date.now()
                    });
                    logger.debug(`Updated price for ${token}: $${priceData.price}`);
                }
            }
        } catch (error) {
            logger.error('Error updating token prices:', error);
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