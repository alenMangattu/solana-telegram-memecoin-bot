import express from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { SolanaUtils } from './utils/solanaUtils';
import { MemecoinService } from './utils/trading/memecoinService';
import { LiquidityService } from './utils/trading/liquidityService';
import { HeliusRpcInterface } from './utils/heliusRpcInterface';
import logger from './utils/logger';
import dotenv from 'dotenv';
import { TokenInfoService } from './utils/tokeninfo';
import { SwapService } from './utils/trading/swapService';
import { InsufficientFundsError, InvalidAddressError } from './utils/errors';

dotenv.config();

const app = express();
const port = process.env.PORT || 3001;

// Ensure SOLANA_CLUSTER_URL is valid
const clusterUrl = process.env.SOLANA_CLUSTER_URL || "";
if (!clusterUrl.startsWith('http://') && !clusterUrl.startsWith('https://')) {
    throw new Error('SOLANA_CLUSTER_URL must start with http:// or https://');
}

// Initialize services
const heliusApiKey = process.env.HELIUS_API_KEY || '';
const solanaUtils = new SolanaUtils(clusterUrl, heliusApiKey);

const memecoinService = new MemecoinService(
    solanaUtils.getConnection(),
    solanaUtils,
    heliusApiKey ? new HeliusRpcInterface(heliusApiKey, clusterUrl) : null
);

const liquidityService = new LiquidityService(
    solanaUtils.getConnection(),
    solanaUtils
);

const swapService = new SwapService(
    solanaUtils.getConnection(),
    solanaUtils
);

// Rate limiting
const limiter = rateLimit({
    windowMs: 15 * 60 * 1000,
    max: 100
});

app.use(express.json());
app.use(limiter);

// Add input validation schema for pool creation
const createPoolSchema = z.object({
    fromToken: z.string(),
    toToken: z.string(),
    fromAmount: z.number().positive(),
    toAmount: z.number().positive(),
    walletPrivateKey: z.string(),
    decimals: z.number().int().min(0).max(9)
});

app.post('/api/pool/create', async (req, res) => {
    try {
        const data = createPoolSchema.parse(req.body);
        
        const poolId = await liquidityService.createPool(
            data.fromToken,
            data.toToken,
            data.fromAmount,
            data.toAmount,
            data.walletPrivateKey,
            data.decimals
        );
        
        res.json({ poolId });
    } catch (error) {
        logger.error('Pool creation error:', error);
        res.status(500).json({ 
            error: 'Failed to create pool',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

app.post('/api/liquidity/add', async (req, res) => {
    try {
        const { tokenMint, tokenAmount, solAmount, walletPrivateKey } = req.body;
        const txSignature = await liquidityService.addLiquidity(
            tokenMint,
            tokenAmount,
            solAmount,
            walletPrivateKey
        );
        res.json({ signature: txSignature });
    } catch (error) {
        logger.error('Liquidity addition error:', error);
        res.status(500).json({ error: 'Failed to add liquidity' });
    }
});

app.post('/api/liquidity/burn', async (req, res) => {
    try {
        const { poolId, burnPercentage, walletPrivateKey } = req.body;
        
        if (burnPercentage <= 0 || burnPercentage > 100) {
            throw new Error("Burn percentage must be between 0 and 100");
        }
        
        const signature = await liquidityService.burnLiquidity(
            poolId,
            burnPercentage,
            walletPrivateKey
        );
        res.json({ signature });
    } catch (error) {
        logger.error('Liquidity burn error:', error);
        res.status(500).json({ 
            error: 'Failed to burn liquidity',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

app.post('/api/liquidity/remove', async (req, res) => {
    try {
        const { poolId, removePercentage, walletPrivateKey } = req.body;
        
        if (removePercentage <= 0 || removePercentage > 100) {
            throw new Error("Remove percentage must be between 0 and 100");
        }
        
        const signature = await liquidityService.removeLiquidity(
            poolId,
            removePercentage,
            walletPrivateKey
        );
        res.json({ signature });
    } catch (error) {
        logger.error('Liquidity removal error:', error);
        res.status(500).json({ 
            error: 'Failed to remove liquidity',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

// Add memecoin creation schema with better validation
const createMemecoinSchema = z.object({
    walletPrivateKey: z.string().min(32, "Invalid private key length"),
    name: z.string().min(1, "Name is required").max(32, "Name too long"),
    symbol: z.string().min(1, "Symbol is required").max(10, "Symbol too long"),
    decimals: z.number().int().min(0).max(9),
    supply: z.number().positive(),
    metadataUri: z.string().url("Invalid metadata URI format"),
    revokeMintAuthority: z.boolean().optional().default(false),
    revokeFreezeAuthority: z.boolean().optional().default(false),
    chatId: z.number().int().positive()
});

// Update the memecoin creation endpoint
app.post('/api/memecoin/create', async (req, res) => {
    try {
        logger.debug('Received memecoin creation request:', {
            body: req.body,
            headers: req.headers,
            timestamp: new Date().toISOString()
        });
        
        const data = createMemecoinSchema.parse(req.body);
        logger.debug('Validated memecoin creation data:', {
            name: data.name,
            symbol: data.symbol,
            decimals: data.decimals,
            supply: data.supply,
            metadataUri: data.metadataUri,
            revokeMintAuthority: data.revokeMintAuthority,
            revokeFreezeAuthority: data.revokeFreezeAuthority,
            chatId: data.chatId
        });

        const tokenAddress = await memecoinService.createMemecoin(
            data.walletPrivateKey,
            data.name,
            data.symbol,
            data.decimals,
            data.supply,
            data.metadataUri,
            data.revokeMintAuthority,
            data.revokeFreezeAuthority,
            data.chatId
        );

        logger.info('Memecoin created successfully:', {
            tokenAddress,
            name: data.name,
            symbol: data.symbol,
            chatId: data.chatId
        });

        res.json({ 
            success: true,
            tokenAddress,
            name: data.name,
            symbol: data.symbol,
            decimals: data.decimals,
            supply: data.supply,
            metadataUri: data.metadataUri,
            revokeMintAuthority: data.revokeMintAuthority,
            revokeFreezeAuthority: data.revokeFreezeAuthority,
            chatId: data.chatId,
            createdAt: new Date().toISOString()
        });
    } catch (error) {
        logger.error('Error in memecoin creation:', {
            error: error instanceof Error ? error.message : 'Unknown error',
            stack: error instanceof Error ? error.stack : undefined,
            timestamp: new Date().toISOString()
        });

        if (error instanceof z.ZodError) {
            logger.debug('Validation error:', {
                errors: error.errors,
                receivedData: req.body
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid input data',
                errorType: 'VALIDATION_ERROR',
                details: error.errors
            });
        }

        if (error instanceof Error) {
            if (error.message.includes('Insufficient SOL balance')) {
                return res.status(400).json({
                    success: false,
                    error: '❌ Insufficient SOL balance. Minimum 0.01 SOL required.',
                    errorType: 'INSUFFICIENT_FUNDS'
                });
            }

            if (error.message.includes('Invalid token address')) {
                return res.status(400).json({
                    success: false,
                    error: '❌ Invalid token address format.',
                    errorType: 'INVALID_ADDRESS'
                });
            }
        }

        return res.status(500).json({
            success: false,
            error: '❌ Failed to create memecoin. Please try again later.',
            errorType: 'UNKNOWN_ERROR'
        });
    }
});

// Example of instantiation
const tokenInfoService = new TokenInfoService(solanaUtils);

// Add the new endpoint for fetching token information
app.get('/api/token/info/:id', async (req, res) => {
    const tokenId = req.params.id; // Extract token ID from the request parameters
    try {
        const tokenInfo = await tokenInfoService.getTokenInformation(tokenId); // Fetch token information
        res.json(tokenInfo); // Return the token information as JSON
    } catch (error) {
        logger.error('Error fetching token information:', error);
        res.status(500).json({ 
            error: 'Failed to fetch token information',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

// Add input validation schema for swap execution with priority
const swapSchema = z.object({
    tokenMint: z.string(),
    solAmount: z.number().positive(),
    slippage: z.number().min(0).max(100),
    walletPrivateKey: z.string(),
    priority: z.enum(['MIN', 'MID', 'MAX']).default('MID')
});

app.post('/api/swap/execute', async (req, res) => {
    try {
        logger.debug('Received swap execution request:', {
            body: req.body,
            headers: req.headers,
            timestamp: new Date().toISOString()
        });
        
        const data = swapSchema.parse(req.body);
        logger.debug('Validated swap request data:', {
            tokenMint: data.tokenMint,
            solAmount: data.solAmount,
            slippage: data.slippage,
            priority: data.priority,
            walletPrivateKeyLength: data.walletPrivateKey.length
        });
        
        // Additional validation for token mint
        if (!data.tokenMint || data.tokenMint.length < 32 || data.tokenMint.length > 44) {
            logger.debug('Invalid token mint format:', {
                tokenMint: data.tokenMint,
                length: data.tokenMint?.length
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid token mint address format',
                errorType: 'INVALID_TOKEN_MINT'
            });
        }

        // Additional validation for wallet private key
        if (!data.walletPrivateKey || data.walletPrivateKey.length < 32) {
            logger.debug('Invalid wallet private key format:', {
                walletPrivateKeyLength: data.walletPrivateKey?.length
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid wallet private key format',
                errorType: 'INVALID_PRIVATE_KEY'
            });
        }
        
        // Map priority levels to SOL amounts (in lamports)
        const priorityFees = {
            'MAX': 0.02 * 1e9,    // 0.02 SOL
            'MID': 0.01 * 1e9,    // 0.01 SOL
            'MIN': 0.005 * 1e9    // 0.005 SOL
        };
        
        logger.debug('Calculated priority fee:', {
            priority: data.priority,
            feeInLamports: priorityFees[data.priority],
            feeInSOL: priorityFees[data.priority] / 1e9
        });
        
        const result = await swapService.executeSwap(
            data.tokenMint,
            data.solAmount,
            data.slippage,
            data.walletPrivateKey,
            priorityFees[data.priority]
        );
        
        logger.debug('Swap execution completed:', {
            success: result.success,
            signature: result.signature,
            amountOut: result.amountOut,
            timestamp: new Date().toISOString()
        });
        
        return res.json(result);
    } catch (error) {
        logger.error('Error in swap execution:', {
            error: error instanceof Error ? error.message : 'Unknown error',
            stack: error instanceof Error ? error.stack : undefined,
            timestamp: new Date().toISOString()
        });

        if (error instanceof z.ZodError) {
            logger.debug('Validation error:', {
                errors: error.errors,
                receivedData: req.body
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid input data',
                errorType: 'VALIDATION_ERROR',
                details: error.errors
            });
        }

        if (error instanceof Error) {
            if (error.name === 'InsufficientFundsError') {
                logger.debug('Insufficient funds error:', {
                    message: error.message
                });
                return res.status(400).json({
                    success: false,
                    error: '❌ Insufficient SOL balance. Please add more SOL to your wallet.',
                    errorType: 'INSUFFICIENT_FUNDS'
                });
            }

            if (error.name === 'InvalidAddressError') {
                logger.debug('Invalid address error:', {
                    message: error.message
                });
                return res.status(400).json({
                    success: false,
                    error: '❌ Invalid token address. Please check and try again.',
                    errorType: 'INVALID_ADDRESS'
                });
            }
        }

        return res.status(500).json({
            success: false,
            error: '❌ An unexpected error occurred. Please try again later.',
            errorType: 'UNKNOWN_ERROR'
        });
    }
});

// Add input validation schema for limit orders
const limitOrderSchema = z.object({
    tokenMint: z.string(),
    solAmount: z.number().positive(),
    limitPrice: z.number().positive(),
    slippage: z.number().min(0).max(100),
    walletPrivateKey: z.string()
});

app.post('/api/limit/create', async (req, res) => {
    try {
        const data = limitOrderSchema.parse(req.body);
        
        const result = await swapService.createLimitOrder(
            data.tokenMint,
            data.solAmount,
            data.limitPrice,
            data.slippage,
            data.walletPrivateKey
        );
        
        res.json(result);
    } catch (error) {
        logger.error('Limit order creation error:', error);
        res.status(500).json({ 
            success: false,
            error: 'Failed to create limit order',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

app.get('/api/token/price/:id', async (req, res) => {
    const tokenId = req.params.id;
    try {
        // Get token price from an oracle or exchange
        // This is a simplified example - in a real app you would use a price oracle
        const tokenInfo = await tokenInfoService.getTokenInformation(tokenId);
        
        // If token has Raydium info with price, return it
        if (tokenInfo.raydiumInfo && tokenInfo.raydiumInfo.priceInSol) {
            const solPrice = await solanaUtils.getSolUsdPrice();
            const tokenPriceUsd = tokenInfo.raydiumInfo.priceInSol * solPrice;
            
            res.json({ 
                price: tokenPriceUsd,
                priceInSol: tokenInfo.raydiumInfo.priceInSol,
                solPrice: solPrice
            });
        } else {
            // No price info available
            res.json({ price: 0 });
        }
    } catch (error) {
        logger.error('Error fetching token price:', error);
        res.status(500).json({ 
            error: 'Failed to fetch token price',
            details: error instanceof Error ? error.message : String(error)
        });
    }
});

// Add input validation schema for selling tokens
const sellTokenSchema = z.object({
    tokenMint: z.string(),
    percentage: z.number().min(0).max(100),
    slippage: z.number().min(0).max(100),
    walletPrivateKey: z.string(),
    priority: z.enum(['MIN', 'MID', 'MAX']).default('MID')
});

app.post('/api/swap/sell', async (req, res) => {
    try {
        logger.debug('Received sell token request:', {
            body: req.body,
            headers: req.headers,
            timestamp: new Date().toISOString()
        });
        
        const data = sellTokenSchema.parse(req.body);
        logger.debug('Validated sell request data:', {
            tokenMint: data.tokenMint,
            percentage: data.percentage,
            slippage: data.slippage,
            priority: data.priority,
            walletPrivateKeyLength: data.walletPrivateKey.length
        });
        
        // Additional validation for token mint
        if (!data.tokenMint || data.tokenMint.length < 32 || data.tokenMint.length > 44) {
            logger.debug('Invalid token mint format:', {
                tokenMint: data.tokenMint,
                length: data.tokenMint?.length
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid token mint address format',
                errorType: 'INVALID_TOKEN_MINT'
            });
        }

        // Additional validation for wallet private key
        if (!data.walletPrivateKey || data.walletPrivateKey.length < 32) {
            logger.debug('Invalid wallet private key format:', {
                walletPrivateKeyLength: data.walletPrivateKey?.length
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid wallet private key format',
                errorType: 'INVALID_PRIVATE_KEY'
            });
        }
        
        // Map priority levels to SOL amounts (in lamports)
        const priorityFees = {
            'MAX': 0.02 * 1e9,    // 0.02 SOL
            'MID': 0.01 * 1e9,    // 0.01 SOL
            'MIN': 0.005 * 1e9    // 0.005 SOL
        };
        
        logger.debug('Calculated priority fee:', {
            priority: data.priority,
            feeInLamports: priorityFees[data.priority],
            feeInSOL: priorityFees[data.priority] / 1e9
        });
        
        const result = await swapService.executeSell(
            data.tokenMint,
            data.percentage,
            data.slippage,
            data.walletPrivateKey,
            priorityFees[data.priority]
        );
        
        logger.debug('Sell execution completed:', {
            success: result.success,
            signature: result.signature,
            amountOut: result.amountOut,
            timestamp: new Date().toISOString()
        });
        
        return res.json(result);
    } catch (error) {
        logger.error('Error in sell execution:', {
            error: error instanceof Error ? error.message : 'Unknown error',
            stack: error instanceof Error ? error.stack : undefined,
            timestamp: new Date().toISOString()
        });

        if (error instanceof z.ZodError) {
            logger.debug('Validation error:', {
                errors: error.errors,
                receivedData: req.body
            });
            return res.status(400).json({
                success: false,
                error: 'Invalid input data',
                errorType: 'VALIDATION_ERROR',
                details: error.errors
            });
        }

        if (error instanceof Error) {
            if (error.name === 'InsufficientFundsError') {
                logger.debug('Insufficient funds error:', {
                    message: error.message
                });
                return res.status(400).json({
                    success: false,
                    error: '❌ Insufficient token balance. Please check your balance.',
                    errorType: 'INSUFFICIENT_FUNDS'
                });
            }

            if (error.name === 'InvalidAddressError') {
                logger.debug('Invalid address error:', {
                    message: error.message
                });
                return res.status(400).json({
                    success: false,
                    error: '❌ Invalid token address. Please check and try again.',
                    errorType: 'INVALID_ADDRESS'
                });
            }
        }

        return res.status(500).json({
            success: false,
            error: '❌ An unexpected error occurred. Please try again later.',
            errorType: 'UNKNOWN_ERROR'
        });
    }
});

app.listen(port, () => {
    logger.info(`Server running on port ${port}`);
}).on('error', (error: any) => {
    if (error.code === 'EADDRINUSE') {
        logger.error(`Port ${port} is already in use`);
        process.exit(1);
    } else {
        logger.error('Failed to start server:', error);
        process.exit(1);
    }
});

export const server = app;