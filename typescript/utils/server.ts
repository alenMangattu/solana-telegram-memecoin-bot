import express from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { SolanaUtils } from './solanaUtils';
import logger from './logger';
import dotenv from 'dotenv';
import axios from 'axios';
import { createMarket } from './raydium/CreateMarket';
import { createAmmPool } from './raydium/CreateStandardAmmPool';
import { addLiquidity } from './raydium/AddLiquidity';
import { burnLiquidity } from './raydium/BurnLiquidity';
import { removeLiquidity } from './raydium/RemoveLiquidity';

dotenv.config();

const app = express();
const port = process.env.PORT || 3001;

// Ensure SOLANA_CLUSTER_URL is valid and has proper protocol
const clusterUrl = process.env.SOLANA_CLUSTER_URL || "";
if (!clusterUrl.startsWith('http://') && !clusterUrl.startsWith('https://')) {
    throw new Error('SOLANA_CLUSTER_URL must start with http:// or https://');
}

// Initialize SolanaUtils with Helius API key from environment variables
const heliusApiKey = process.env.HELIUS_API_KEY || '';
const solanaUtils = new SolanaUtils(clusterUrl, heliusApiKey);

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100 // limit each IP to 100 requests per windowMs
});

app.use(express.json());
app.use(limiter);

// Input validation schemas
const createMemecoinSchema = z.object({
  walletPrivateKey: z.string(),
  name: z.string(),
  symbol: z.string(),
  decimals: z.number().int().min(0).max(9),
  supply: z.number().positive(),
  metadataUri: z.string().url(),
  revokeMintAuthority: z.boolean(),
  revokeFreezeAuthority: z.boolean(),
  chatId: z.number()
});

// Add timeout to axios instance
const axiosInstance = axios.create({
    timeout: 300000 // 5 minutes
});

// Routes
app.post('/api/wallet/create', async (req, res) => {
  try {
    const wallet = await solanaUtils.createWallet();
    res.json(wallet);
  } catch (error) {
    logger.error('Wallet creation error:', error);
    res.status(500).json({ error: 'Failed to create wallet' });
  }
});

app.get('/api/wallet/balance/:address', async (req, res) => {
  try {
    const balance = await solanaUtils.getBalance(req.params.address);
    res.json({ balance });
  } catch (error) {
    logger.error('Balance check error:', error);
    res.status(500).json({ error: 'Failed to get balance' });
  }
});

/**
 * Example request body:
 * {
 *   "walletId": "uuid-string",
 *   "name": "My Token",
 *   "symbol": "MTK",
 *   "decimals": 9,
 *   "supply": 1000000,
 *   "metadataUri": "https://example.com/metadata.json",
 *   "revokeMintAuthority": true,
 *   "revokeFreezeAuthority": true
 * }
 */
app.post('/api/memecoin/create', async (req, res) => {
  try {
    const data = createMemecoinSchema.parse(req.body);
    const chatId = req.body.chatId; // Make sure this is passed from your Java backend
    
    const tokenAddress = await solanaUtils.createMemecoin(
      data.walletPrivateKey,
      data.name,
      data.symbol,
      data.decimals,
      data.supply,
      data.metadataUri,
      data.revokeMintAuthority,
      data.revokeFreezeAuthority,
      chatId  // Pass the chatId directly
    );
    res.json({ tokenAddress });
  } catch (error) {
    logger.error('Memecoin creation error:', error);
    res.status(400).json({ 
      error: 'Failed to create memecoin',
      details: error instanceof Error ? error.message : String(error)
    });
  }
});

app.post('/api/liquidity/add', async (req, res) => {
  try {
    const { tokenMint, tokenAmount, solAmount, walletPrivateKey } = req.body;
    const txSignature = await solanaUtils.addLiquidity(
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
    
    // Validate burn percentage
    if (burnPercentage <= 0 || burnPercentage > 100) {
      throw new Error("Burn percentage must be between 0 and 100");
    }
    
    const txSignature = await burnLiquidity(poolId, burnPercentage, walletPrivateKey);
    res.json({ signature: txSignature });
  } catch (error) {
    logger.error('Liquidity burn error:', error);
    res.status(500).json({ error: 'Failed to burn liquidity' });
  }
});

app.post('/api/liquidity/remove', async (req, res) => {
  try {
    const { poolId, removePercentage, walletPrivateKey } = req.body;
    
    // Validate remove percentage
    if (removePercentage <= 0 || removePercentage > 100) {
      throw new Error("Remove percentage must be between 0 and 100");
    }
    
    const txSignature = await removeLiquidity(poolId, removePercentage, walletPrivateKey);
    res.json({ signature: txSignature });
  } catch (error) {
    logger.error('Liquidity removal error:', error);
    res.status(500).json({ error: 'Failed to remove liquidity' });
  }
});

app.post('/api/market/create', async (req, res) => {
  try {
    const { tokenMint, walletPrivateKey, lotSize, tickSize } = req.body;
    const marketId = await createMarket(tokenMint, walletPrivateKey, lotSize, tickSize);
    res.json({ marketId });
  } catch (error) {
    logger.error('Market creation error:', error);
    res.status(500).json({ error: 'Failed to create market' });
  }
});

app.post('/api/pool/create', async (req, res) => {
  try {
    const { 
      fromToken,        // SOL token info
      toToken,          // Your token info
      fromAmount,       // SOL amount
      toAmount,         // Your token amount
      walletPrivateKey,
      decimals
    } = req.body;
    
    const poolId = await createAmmPool(
      fromToken,
      toToken,
      fromAmount, 
      toAmount,
      walletPrivateKey,
      decimals
    );
    res.json({ poolId });
  } catch (error) {
    logger.error('Pool creation error:', error);
    res.status(500).json({ error: 'Failed to create pool' });
  }
});

app.get('/hello', (req, res) => {
  res.send('Hello World');
});

app.listen(port, () => {
  logger.info(`Server running on port ${port}`);
}).on('error', (error: any) => {
    if (error.code === 'EADDRINUSE') {
        logger.error(`Port ${port} is already in use. Please choose a different port.`);
        process.exit(1);
    } else {
        logger.error('Failed to start server:', error);
        process.exit(1);
    }
});

export const server = app; 