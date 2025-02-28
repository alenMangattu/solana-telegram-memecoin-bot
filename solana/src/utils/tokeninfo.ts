import { Connection, PublicKey, type ParsedAccountData } from '@solana/web3.js';
import { Liquidity } from '@raydium-io/raydium-sdk';
import axios from 'axios';
import dotenv from 'dotenv';
import { SolanaUtils } from './solanaUtils';

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
    private solanaUtils: SolanaUtils;

    constructor(solanaUtils: SolanaUtils) {
        this.jupiterApiUrl = 'https://price.jup.ag/v4';
        this.birdeyeApiUrl = 'https://public-api.birdeye.so';
        this.birdeyeApiKey = process.env.BIRDEYE_API_KEY || '';
        this.connection = new Connection(process.env.SOLANA_CLUSTER_URL || '');
        this.solanaUtils = solanaUtils;
    }

    async getTokenInformation(tokenAddress: string): Promise<TokenInfo> {
        try {
            const heliusApiUrl = this.solanaUtils.getHeliusApiUrl();
            const [heliusData, basicInfo, marketData, poolInfo, holderStats] = await Promise.all([
                this.getHeliusTokenInfo(tokenAddress, heliusApiUrl),
                this.getBasicTokenInfo(tokenAddress),
                this.getTokenMarketData(tokenAddress),
                this.getLiquidityPoolInfo(tokenAddress),
                this.getTokenHolderStats(tokenAddress)
            ]);

            return {
                address: tokenAddress,
                ...basicInfo,
                ...marketData,
                ...poolInfo,
                holderStats
            };
        } catch (error) {
            console.error('Error getting token information:', error);
            throw error;
        }
    }

    private async getHeliusTokenInfo(tokenAddress: string, heliusApiUrl: string | undefined) {
        if (!heliusApiUrl) {
            throw new Error('Helius API URL is not defined');
        }

        try {
            const payload = {
                jsonrpc: "2.0",
                id: 1,
                method: "getAsset",
                params: {
                    id: tokenAddress // Use the token address as the ID
                }
            };

            const response = await axios.post(heliusApiUrl, payload, {
                headers: {
                    'X-API-KEY': process.env.HELIUS_API_KEY || '',
                    'Content-Type': 'application/json'
                }
            });

            const tokenData = response.data.result;

            return {
                name: tokenData.content.metadata.name,
                symbol: tokenData.content.metadata.symbol,
                decimals: tokenData.token_info.decimals,
                totalSupply: tokenData.token_info.supply,
                priceUSD: tokenData.token_info.price_info?.price_per_token,
                marketCap: tokenData.marketCap,
                volume24h: tokenData.volume24h,
                priceChange24h: tokenData.priceChange24h,
                liquidityUSD: tokenData.liquidityUSD,
                poolAddress: tokenData.poolAddress,
                poolTokens: tokenData.poolTokens,
                poolSOL: tokenData.poolSOL,
            };
        } catch (error) {
            console.error('Error fetching token info from Helius:', error);
            throw new Error('Failed to fetch token info from Helius');
        }
    }

    async getBasicTokenInfo(tokenAddress: string) {
        try {
            const tokenPublicKey = new PublicKey(tokenAddress);
            const tokenInfo = await this.connection.getParsedAccountInfo(tokenPublicKey);
            const parsedData = tokenInfo.value?.data as { info?: { name?: string; symbol?: string; decimals?: number; supply?: number; } };
            return {
                name: parsedData.info?.name || '',
                symbol: parsedData.info?.symbol || '',
                decimals: parsedData.info?.decimals || 0,
                totalSupply: parsedData.info?.supply || 0,
            };
        } catch (error) {
            console.error('Error getting basic token info:', error);
            throw new Error('Invalid token address or unable to fetch token info');
        }
    }

    private async getTokenMarketData(tokenAddress: string) {
        try {
            // Get price from Jupiter
            const jupiterResponse = await axios.get(
                `${this.jupiterApiUrl}/price?ids=${tokenAddress}`            );

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
