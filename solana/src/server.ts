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

// Add memecoin creation schema
const createMemecoinSchema = z.object({
    walletPrivateKey: z.string(),
    name: z.string(),
    symbol: z.string(),
    decimals: z.number().int().min(0).max(9),
    supply: z.number().positive(),
    metadataUri: z.string().url(),
    revokeMintAuthority: z.boolean().optional().default(false),
    revokeFreezeAuthority: z.boolean().optional().default(false),
    chatId: z.number()
});

// Add the memecoin creation endpoint
// app.post('/api/memecoin/create', async (req, res) => {
//     try {
//         const data = createMemecoinSchema.parse(req.body);
        
//         const tokenAddress = await memecoinService.createMemecoin(
//             data.walletPrivateKey,
//             data.name,
//             data.symbol,
//             data.decimals,
//             data.supply,
//             data.metadataUri,
//             data.revokeMintAuthority,
//             data.revokeFreezeAuthority,
//             data.chatId
//         );
        
//         res.json({ 
//             success: true,
//             tokenAddress 
//         });
//     } catch (error) {
//         logger.error('Memecoin creation error:', error);
//         res.status(500).json({ 
//             success: false,
//             error: 'Failed to create memecoin',
//             details: error instanceof Error ? error.message : String(error)
//         });
//     }
// });

app.post('/api/memecoin/create', async (req, res) => {
    try {
      const {
        walletPrivateKey,
        name,
        symbol,
        decimals,
        supply,
        metadataUri,
        revokeMintAuthority,
        revokeFreezeAuthority,
        chatId,
      } = req.body;
  
      const tokenAddress = await memecoinService.createMemecoin(
        walletPrivateKey,
        name,
        symbol,
        decimals,
        supply,
        metadataUri,
        revokeMintAuthority,
        revokeFreezeAuthority,
        chatId
      );
  
      res.json({ success: true, tokenAddress });
    } catch (error) {
      logger.error('Error in creating memecoin', error);
      res.status(500).json({ success: false, error: error instanceof Error ? error.message : String(error) });
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