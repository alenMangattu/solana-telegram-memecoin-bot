import express from 'express';
import dotenv from 'dotenv';
import { server } from './server';
import type { Server } from 'http';
import { Pool } from 'pg';
import { OrderService } from './services/orderService';
import { Worker } from 'worker_threads';
import path from 'path';
import os from 'os';

dotenv.config();

const port = process.env.PORT || 3000;

// Check required environment variables
const requiredEnvVars = [
    'SOLANA_CLUSTER_URL',
    'HELIUS_API_KEY',
    'RPC_URL',
    'WALLETS_PATH',
    'ENCRYPTION_KEYS_PATH',
    'DB_USER',
    'DB_HOST',
    'DB_NAME',
    'DB_PASSWORD',
    'DB_PORT'
];

for (const envVar of requiredEnvVars) {
    if (!process.env[envVar]) {
        console.error(`Error: ${envVar} is not defined in the environment variables.`);
        process.exit(1);
    }
}

let httpServer: Server;

// Database configuration
const pool = new Pool({
    user: process.env.DB_USER,
    host: process.env.DB_HOST,
    database: process.env.DB_NAME,
    password: process.env.DB_PASSWORD,
    port: parseInt(process.env.DB_PORT || '5432', 10),
});

// Initialize services
const orderService = new OrderService(pool);

// Function to create a worker
function createWorker(workerPath: string, workerData: any) {
    return new Promise((resolve, reject) => {
        const worker = new Worker(workerPath, { workerData });
        worker.on('message', resolve);
        worker.on('error', reject);
        worker.on('exit', (code) => {
            if (code !== 0) {
                reject(new Error(`Worker stopped with exit code ${code}`));
            }
        });
    });
}

// Function to start trading workers
async function startTradingWorkers() {
    const numCores = os.cpus().length;
    const numWorkers = Math.max(2, Math.min(numCores - 1, 4)); // Use 2-4 workers

    console.log(`Starting ${numWorkers} trading workers...`);

    // Create workers for limit orders and copy trading
    const limitOrderWorkers = Array(numWorkers).fill(null).map((_, index) => 
        createWorker(
            path.join(__dirname, 'workers/limitOrderWorker.ts'),
            { workerId: index, totalWorkers: numWorkers }
        )
    );

    const copyTradingWorkers = Array(numWorkers).fill(null).map((_, index) => 
        createWorker(
            path.join(__dirname, 'workers/copyTradingWorker.ts'),
            { workerId: index, totalWorkers: numWorkers }
        )
    );

    try {
        // Start all workers in parallel
        await Promise.all([
            ...limitOrderWorkers,
            ...copyTradingWorkers
        ]);
        console.log('All trading workers started successfully');
    } catch (error) {
        console.error('Error starting trading workers:', error);
        process.exit(1);
    }
}

// Start the server
httpServer = server.listen(port, async () => {
    console.log(`Server is running on port ${port}`);
    console.log(`Solana cluster URL: ${process.env.SOLANA_CLUSTER_URL}`);
    
    // Start trading workers after server is running
    await startTradingWorkers();
});

// Handle graceful shutdown
process.on('SIGTERM', async () => {
    console.log('SIGTERM signal received: closing HTTP server and workers');
    if (httpServer) {
        httpServer.close(async () => {
            console.log('HTTP server closed');
            await pool.end();
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