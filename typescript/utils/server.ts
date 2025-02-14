import express from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import { SolanaUtils } from './solanaUtils';
import logger from './logger';
import dotenv from 'dotenv';
import axios from 'axios';

dotenv.config();

const app = express();
const port = process.env.PORT || 3001;

// Ensure SOLANA_CLUSTER_URL is valid and has proper protocol
const clusterUrl = process.env.SOLANA_CLUSTER_URL || "";
if (!clusterUrl.startsWith('http://') && !clusterUrl.startsWith('https://')) {
    throw new Error('SOLANA_CLUSTER_URL must start with http:// or https://');
}

const solanaUtils = new SolanaUtils(clusterUrl);

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