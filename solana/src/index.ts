import express from 'express';
import dotenv from 'dotenv';
import { server } from './server';
import type { Server } from 'http';
import { Pool } from 'pg';
import { OrderService } from './services/orderService';

dotenv.config();

const port = process.env.PORT || 3000;

// Check if SOLANA_CLUSTER_URL is set
if (!process.env.SOLANA_CLUSTER_URL) {
  console.error('Error: SOLANA_CLUSTER_URL is not defined in the environment variables.');
  process.exit(1);
}

// Check if HELIUS_API_KEY is set
if (!process.env.HELIUS_API_KEY) {
  console.error('Error: HELIUS_API_KEY is not defined in the environment variables.');
  process.exit(1);
}

let httpServer: Server;

// Database configuration
const pool = new Pool({
    user: 'postgres',
    host: 'localhost',
    database: 'lunax',
    password: 'postgres', // Replace with your actual password
    port: 5432,
});

// Initialize services
const orderService = new OrderService(pool);

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

// Handle application shutdown
process.on('SIGINT', async () => {
    console.log('Shutting down...');
    await pool.end();
    process.exit(0);
});

process.on('SIGTERM', async () => {
    console.log('Shutting down...');
    await pool.end();
    process.exit(0);
});

// Start the application
console.log('Starting LunaX trading services...');

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
 *   walletPrivateKey: string,
 *   chatId: number
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