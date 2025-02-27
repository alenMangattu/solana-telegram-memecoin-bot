import { Connection, PublicKey, type ParsedAccountData } from '@solana/web3.js';
import { Liquidity } from '@raydium-io/raydium-sdk';
import axios from 'axios';
import dotenv from 'dotenv';

dotenv.config();

interface TokenInfo {
    address: string;
    name: string;
    symbol: string;
    decimals: number;
    totalSupply: number;
    priceUSD?: number;
    marketCap?: number;
    volume24h?: number;
    priceChange24h?: number;
    liquidityUSD?: number;
    poolAddress?: string;
    poolTokens?: number;
    poolSOL?: number;
    holderStats?: {
        totalHolders: number;
        uniqueHolders: number;
        averageHolding: number;
    };
}

export class TokenInfoService {
    private jupiterApiUrl: string;
    private birdeyeApiUrl: string;
    private birdeyeApiKey: string;
    private connection: Connection;

    constructor() {
        this.jupiterApiUrl = 'https://price.jup.ag/v4';
        this.birdeyeApiUrl = 'https://public-api.birdeye.so';
        this.birdeyeApiKey = process.env.BIRDEYE_API_KEY || '';
        this.connection = new Connection(process.env.SOLANA_RPC_URL || '');
    }

    async getTokenInformation(tokenAddress: string): Promise<TokenInfo> {
        try {
            const [marketData, poolInfo, holderStats] = await Promise.all([
                this.getTokenMarketData(tokenAddress),
                this.getLiquidityPoolInfo(tokenAddress),
                this.getTokenHolderStats(tokenAddress)
            ]);

            return {
                address: tokenAddress,
                ...(await this.getBasicTokenInfo(tokenAddress)),
                ...marketData,
                ...poolInfo,
                holderStats
            };
        } catch (error) {
            console.error('Error getting token information:', error);
            throw error;
        }
    }

    private async getBasicTokenInfo(tokenAddress: string) {
        const tokenPublicKey = new PublicKey(tokenAddress);
        const tokenInfo = await this.connection.getParsedAccountInfo(tokenPublicKey);
        const parsedData = tokenInfo.value?.data as { info?: { name?: string; symbol?: string; decimals?: number; supply?: number; } }; // Adjusted type to match expected structure
        return {
            name: parsedData.info?.name || '',
            symbol: parsedData.info?.symbol || '',
            decimals: parsedData.info?.decimals || 0,
            totalSupply: parsedData.info?.supply || 0,
        };
    }

    private async getTokenMarketData(tokenAddress: string) {
        try {
            // Get price from Jupiter
            const jupiterResponse = await axios.get(
                `${this.jupiterApiUrl}/price?ids=${tokenAddress}`
            );

            // Get market data from Birdeye
            const birdeyeResponse = await axios.get(
                `${this.birdeyeApiUrl}/public/token_list?address=${tokenAddress}`,
                {
                    headers: { 'X-API-KEY': this.birdeyeApiKey }
                }
            );

            return {
                priceUSD: jupiterResponse.data?.data?.[tokenAddress]?.price,
                marketCap: birdeyeResponse.data?.tokens?.[0]?.marketCap,
                volume24h: birdeyeResponse.data?.tokens?.[0]?.volume24h,
                priceChange24h: birdeyeResponse.data?.tokens?.[0]?.priceChange24h
            };
        } catch (error) {
            console.error('Error getting market data:', error);
            return {};
        }
    }

    private async getLiquidityPoolInfo(tokenAddress: string) {
        try {
            const response = await axios.get(
                `https://api.raydium.io/v2/main/pool/${tokenAddress}`
            );

            return {
                liquidityUSD: response.data?.liquidity?.usd,
                poolAddress: response.data?.id,
                poolTokens: response.data?.tokenAmount,
                poolSOL: response.data?.solAmount
            };
        } catch (error) {
            console.error('Error getting liquidity pool info:', error);
            return {};
        }
    }

    private async getTokenHolderStats(tokenAddress: string) {
        try {
            const response = await axios.get(
                `${this.birdeyeApiUrl}/public/token_holders?address=${tokenAddress}`,
                {
                    headers: { 'X-API-KEY': this.birdeyeApiKey }
                }
            );

            const totalHolders = response.data?.total || 0;
            const uniqueHolders = response.data?.uniqueHolders || 0;
            const totalSupply = response.data?.totalSupply || 0;

            return {
                totalHolders,
                uniqueHolders,
                averageHolding: uniqueHolders > 0 ? totalSupply / uniqueHolders : 0
            };
        } catch (error) {
            console.error('Error getting holder stats:', error);
            return {
                totalHolders: 0,
                uniqueHolders: 0,
                averageHolding: 0
            };
        }
    }
} 