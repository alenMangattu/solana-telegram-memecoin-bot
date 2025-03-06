import { Connection, PublicKey, type ParsedAccountData } from '@solana/web3.js';
import { Liquidity } from '@raydium-io/raydium-sdk';
import axios from 'axios';
import dotenv from 'dotenv';
import { SolanaUtils } from './solanaUtils';
import logger from './logger'; // Import the logger

dotenv.config();

// Add this interface to define the RaydiumInfo structure
export interface RaydiumInfo {
    priceInSol: number;
    poolId?: string;
    lpMint?: string;
    // Add other Raydium-specific properties as needed
}

export interface TokenInfo {
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
    logoURI?: string;
    extensions?: any;
    raydiumInfo?: RaydiumInfo;
}

export class TokenInfoService {
    private birdeyeApiUrl: string;
    private birdeyeApiKey: string;
    private connection: Connection;
    private solanaUtils: SolanaUtils;

    constructor(solanaUtils: SolanaUtils) {
        this.birdeyeApiUrl = 'https://public-api.birdeye.so';
        this.birdeyeApiKey = process.env.BIRDEYE_API_KEY || '';
        this.connection = new Connection(process.env.SOLANA_CLUSTER_URL || '');
        this.solanaUtils = solanaUtils;

        // Ensure Helius API URL and API Key are defined
        const heliusApiUrl = this.solanaUtils.getHeliusApiUrl();
        if (!heliusApiUrl) {
            logger.error('Helius API URL is not defined');
            throw new Error('Helius API URL is not defined');
        }
        if (!this.birdeyeApiKey) {
            logger.error('Birdeye API Key is not defined');
            throw new Error('Birdeye API Key is not defined');
        }
    }

    async getTokenInformation(tokenAddress: string): Promise<TokenInfo> {
        logger.info(`Fetching token information for address: ${tokenAddress}`);
        try {
            const heliusApiUrl = this.solanaUtils.getHeliusApiUrl();
            logger.info(`Using Helius API URL: ${heliusApiUrl}`);
            
            // Get data from multiple sources with proper error handling
            const [heliusData, basicInfo, marketData, poolInfo, holderStats] = await Promise.all([
                this.getHeliusTokenInfo(tokenAddress, heliusApiUrl).catch(error => {
                    logger.error('Error fetching from Helius:', error);
                    return { name: '', symbol: '', decimals: 0, totalSupply: 0 }; // Provide default values
                }),
                this.getBasicTokenInfo(tokenAddress).catch(error => {
                    logger.error('Error fetching basic token info:', error);
                    return { name: '', symbol: '', decimals: 0, totalSupply: 0 }; // Provide default values
                }),
                this.getTokenMarketData(tokenAddress).catch(error => {
                    logger.error('Error fetching market data:', error);
                    return {}; // Return empty object on failure
                }),
                this.getLiquidityPoolInfo(tokenAddress).catch(error => {
                    logger.error('Error fetching liquidity pool info:', error);
                    return {}; // Return empty object on failure
                }),
                this.getTokenHolderStats(tokenAddress).catch(error => {
                    logger.error('Error fetching holder stats:', error);
                    return {
                        totalHolders: 0,
                        uniqueHolders: 0,
                        averageHolding: 0
                    };
                })
            ]);

            logger.info(`Successfully fetched data for token: ${tokenAddress}`);
            // Merge and ensure required fields have values
            return {
                address: tokenAddress,
                name: basicInfo.name || heliusData.name || '',
                symbol: basicInfo.symbol || heliusData.symbol || '',
                decimals: basicInfo.decimals || heliusData.decimals || 0,
                totalSupply: basicInfo.totalSupply || heliusData.totalSupply || 0,
                ...marketData,
                ...poolInfo,
                holderStats
            };
        } catch (error: unknown) {
            logger.error('Error getting token information:', error);
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            throw new Error(`Failed to fetch token information: ${errorMessage}`);
        }
    }

    private async getHeliusTokenInfo(tokenAddress: string, heliusApiUrl: string | undefined) {
        logger.info(`Fetching Helius token info for address: ${tokenAddress}`);
        if (!heliusApiUrl) {
            logger.warn('Helius API URL is not defined');
            return { name: '', symbol: '', decimals: 0, totalSupply: 0 };
        }

        try {
            // Use getAsset method as shown in the example
            const payload = {
                jsonrpc: "2.0",
                id: "test",
                method: "getAsset",
                params: {
                    id: tokenAddress
                }
            };

            logger.info(`Sending request to Helius API with payload: ${JSON.stringify(payload)}`);
            const response = await axios.post(heliusApiUrl, payload, {
                headers: {
                    'X-API-KEY': process.env.HELIUS_API_KEY || '',
                    'Content-Type': 'application/json'
                }
            });

            const result = response.data?.result;
            logger.info(`Received response from Helius API for token: ${tokenAddress}`);
            
            if (!result) {
                logger.warn('No data returned from Helius API');
                return { name: '', symbol: '', decimals: 0, totalSupply: 0 };
            }

            return {
                name: result.content?.metadata?.name || '',
                symbol: result.content?.metadata?.symbol || '',
                decimals: result.token_info?.decimals || 0,
                totalSupply: result.token_info?.supply || 0,
                priceUSD: result.token_info?.price_info?.price_per_token,
                // Helius doesn't provide these fields directly, so they'll be undefined
                marketCap: undefined,
                volume24h: undefined,
                priceChange24h: undefined,
                liquidityUSD: undefined,
                poolAddress: undefined,
                poolTokens: undefined,
                poolSOL: undefined,
            };
        } catch (error) {
            logger.error('Error fetching token info from Helius:', error);
            return { name: '', symbol: '', decimals: 0, totalSupply: 0 };
        }
    }

    async getBasicTokenInfo(tokenAddress: string) {
        logger.info(`Fetching basic token info for address: ${tokenAddress}`);
        try {
            const tokenPublicKey = new PublicKey(tokenAddress);
            const tokenInfo = await this.connection.getParsedAccountInfo(tokenPublicKey);
            const parsedData = tokenInfo.value?.data as { info?: { name?: string; symbol?: string; decimals?: number; supply?: number; } };
            logger.info(`Successfully fetched basic token info for address: ${tokenAddress}`);
            return {
                name: parsedData.info?.name || '',
                symbol: parsedData.info?.symbol || '',
                decimals: parsedData.info?.decimals || 0,
                totalSupply: parsedData.info?.supply || 0,
            };
        } catch (error) {
            logger.error('Error getting basic token info:', error);
            return { name: '', symbol: '', decimals: 0, totalSupply: 0 }; // Return object with default values
        }
    }

    private async getTokenMarketData(tokenAddress: string) {
        logger.info(`Fetching market data for address: ${tokenAddress}`);
        try {
            // Get market data from Birdeye
            const birdeyeResponse = await axios.get(
                `${this.birdeyeApiUrl}/public/token_list?address=${tokenAddress}`,
                {
                    headers: { 'X-API-KEY': this.birdeyeApiKey }
                }
            ).catch(error => {
                logger.error('Error from Birdeye:', error);
                return { data: { tokens: [] } };
            });

            // Get price from Helius
            const heliusApiUrl = this.solanaUtils.getHeliusApiUrl();
            const heliusApiKey = process.env.HELIUS_API_KEY;
            if (!heliusApiUrl || !heliusApiKey) {
                logger.error('Helius API URL or API Key is not defined');
                return {};
            }

            const heliusResponse = await axios.post(heliusApiUrl, {
                jsonrpc: "2.0",
                id: "test",
                method: "getAsset",
                params: {
                    id: tokenAddress
                }
            }, {
                headers: {
                    'X-API-KEY': heliusApiKey,
                    'Content-Type': 'application/json'
                }
            }).catch(error => {
                logger.error('Error from Helius:', error);
                return { data: { result: null } };
            });

            const tokenData = heliusResponse.data?.result;
            logger.info(`Successfully fetched market data for address: ${tokenAddress}`);

            return {
                priceUSD: tokenData?.token_info?.price_info?.price_per_token,
                marketCap: birdeyeResponse.data?.tokens?.[0]?.marketCap,
                volume24h: birdeyeResponse.data?.tokens?.[0]?.volume24h,
                priceChange24h: birdeyeResponse.data?.tokens?.[0]?.priceChange24h
            };
        } catch (error) {
            logger.error('Error getting market data:', error);
            return {};
        }
    }

    private async getLiquidityPoolInfo(tokenAddress: string) {
        logger.info(`Fetching liquidity pool info for address: ${tokenAddress}`);
        try {
            const response = await axios.get(
                `https://api.raydium.io/v2/main/pool/${tokenAddress}`
            );

            logger.info(`Successfully fetched liquidity pool info for address: ${tokenAddress}`);
            return {
                liquidityUSD: response.data?.liquidity?.usd,
                poolAddress: response.data?.id,
                poolTokens: response.data?.tokenAmount,
                poolSOL: response.data?.solAmount
            };
        } catch (error: unknown) {
            // Check if this is a 404 error (pool not found)
            if (axios.isAxiosError(error) && error.response?.status === 404) {
                logger.warn(`No liquidity pool found for token ${tokenAddress}`);
                return {};
            }
            logger.error('Error getting liquidity pool info:', error);
            return {};
        }
    }

    private async getTokenHolderStats(tokenAddress: string) {
        logger.info(`Fetching holder stats for address: ${tokenAddress}`);
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

            logger.info(`Successfully fetched holder stats for address: ${tokenAddress}`);
            return {
                totalHolders,
                uniqueHolders,
                averageHolding: uniqueHolders > 0 ? totalSupply / uniqueHolders : 0
            };
        } catch (error) {
            logger.error('Error getting holder stats:', error);
            return {
                totalHolders: 0,
                uniqueHolders: 0,
                averageHolding: 0
            };
        }
    }
} 
