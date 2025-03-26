import axios from 'axios';
import type { TokenPrice } from '../types/database';

interface CoinGeckoResponse {
    [key: string]: {
        usd: number;
    };
}

export class PriceService {
    private tokenPrices: Map<string, TokenPrice> = new Map();
    private readonly COINGECKO_API_URL = 'https://api.coingecko.com/api/v3';
    private readonly UPDATE_INTERVAL = 10000; // 10 seconds

    constructor() {
        this.startPriceUpdates();
    }

    private async startPriceUpdates() {
        setInterval(() => this.updatePrices(), this.UPDATE_INTERVAL);
    }

    private async updatePrices() {
        const tokens = Array.from(this.tokenPrices.keys());
        if (tokens.length === 0) return;

        try {
            const response = await axios.get<CoinGeckoResponse>(`${this.COINGECKO_API_URL}/simple/token_price/solana`, {
                params: {
                    contract_addresses: tokens.join(','),
                    vs_currencies: 'usd'
                }
            });

            for (const [tokenMint, priceData] of Object.entries(response.data)) {
                this.tokenPrices.set(tokenMint, {
                    tokenMint,
                    price: priceData.usd,
                    lastUpdated: new Date()
                });
            }
        } catch (error) {
            console.error('Error updating token prices:', error);
        }
    }

    public subscribeToToken(tokenMint: string) {
        if (!this.tokenPrices.has(tokenMint)) {
            this.tokenPrices.set(tokenMint, {
                tokenMint,
                price: 0,
                lastUpdated: new Date()
            });
            // Trigger immediate price update for new token
            this.updatePrices();
        }
    }

    public unsubscribeFromToken(tokenMint: string) {
        this.tokenPrices.delete(tokenMint);
    }

    public getTokenPrice(tokenMint: string): TokenPrice | undefined {
        return this.tokenPrices.get(tokenMint);
    }

    public getAllTokenPrices(): TokenPrice[] {
        return Array.from(this.tokenPrices.values());
    }
} 