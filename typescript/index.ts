<<<<<<< HEAD
import express from 'express';
import dotenv from 'dotenv';
import { server } from './utils/server';
import type { Server } from 'http';

dotenv.config();

const port = process.env.PORT || 3000;

// Check if SOLANA_CLUSTER_URL is set
if (!process.env.SOLANA_CLUSTER_URL) {
  console.error('Error: SOLANA_CLUSTER_URL is not defined in the environment variables.');
  process.exit(1);
}

let httpServer: Server;

// Start the server
httpServer = server.listen(port, () => {
  console.log(`Server is running on port ${port}`);
  console.log(`Solana cluster URL: ${process.env.SOLANA_CLUSTER_URL}`);
});

// Handle graceful shutdown
process.on('SIGTERM', () => {
  console.log('SIGTERM signal received: closing HTTP server');
  if (httpServer) {
    httpServer.close(() => {
      console.log('HTTP server closed');
      process.exit(0);
    });
  }
});

/**
 * API Documentation
 * 
 * POST /api/wallet/create
 * Creates a new Solana wallet
 * Response: {
 *   publicKey: string,
 *   privateKey: string
 * }
 * 
 * GET /api/wallet/balance/:address
 * Gets the SOL balance for a wallet address
 * Response: {
 *   balance: number // in SOL
 * }
 * 
 * POST /api/memecoin/create
 * Creates a new memecoin token
 * Request Body: {
 *   name: string,
 *   symbol: string,
 *   decimals: number (0-9),
 *   supply: number,
 *   metadataUri: string (URL),
 *   revokeMintAuthority: boolean,
 *   revokeFreezeAuthority: boolean,
 *   walletPrivateKey: string
 * }
 * Response: {
 *   tokenAddress: string
 * }
 * 
 * POST /api/liquidity/add
 * Adds liquidity to a Raydium pool
 * Request Body: {
 *   tokenMint: string,
 *   tokenAmount: number,
 *   solAmount: number,
 *   walletPrivateKey: string
 * }
 * Response: {
 *   signature: string
 * }
 */
=======
console.log("Bella Raga! ");
>>>>>>> 78e8847 (Modified the token progam from token 2022 -> standard token program (metadata still doesnt work))
