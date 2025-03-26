import { parentPort, workerData } from 'worker_threads';
import { Pool } from 'pg';
import { OrderService } from '../services/orderService';
import { Connection } from '@solana/web3.js';
import dotenv from 'dotenv';

dotenv.config();

if (!parentPort) {
    throw new Error('This file must be run as a worker thread');
}

const { workerId, totalWorkers } = workerData;

// Initialize database connection
const pool = new Pool({
    user: 'postgres',
    host: 'localhost',
    database: 'lunax',
    password: 'postgres',
    port: 5432,
});

// Initialize Solana connection
const connection = new Connection(process.env.RPC_URL!);

// Initialize OrderService
const orderService = new OrderService(pool);

async function processCopyTrading() {
    try {
        // Get active copy trading pairs for this worker
        const query = `
            SELECT ct.*, sw.address as source_wallet_address, tw.address as target_wallet_address
            FROM copy_trading ct
            JOIN wallets sw ON ct.source_wallet_id = sw.wallet_id
            JOIN wallets tw ON ct.target_wallet_id = tw.wallet_id
            WHERE ct.status = 'active'
            AND MOD(CAST(ct.copy_trading_id AS INTEGER), $1) = $2
        `;
        const result = await pool.query(query, [totalWorkers, workerId]);
        const copyTradingPairs = result.rows;

        for (const pair of copyTradingPairs) {
            try {
                // Get the latest signature for this source wallet
                const signatures = await connection.getSignaturesForAddress(
                    pair.source_wallet_address,
                    { limit: 1 }
                );

                if (signatures.length > 0) {
                    const latestSignature = signatures[0].signature;
                    
                    // Check if we've already processed this signature
                    const processedQuery = `
                        SELECT 1 FROM processed_transactions 
                        WHERE signature = $1 AND copy_trading_id = $2
                    `;
                    const processedResult = await pool.query(processedQuery, [
                        latestSignature,
                        pair.copy_trading_id
                    ]);

                    if (processedResult.rows.length === 0) {
                        // Process the transaction
                        await orderService.copySwapTransaction(
                            pair.source_wallet_address,
                            pair.target_wallet_address,
                            latestSignature,
                            pair.amount_multiplier
                        );

                        // Mark transaction as processed
                        await pool.query(
                            'INSERT INTO processed_transactions (signature, copy_trading_id) VALUES ($1, $2)',
                            [latestSignature, pair.copy_trading_id]
                        );
                    }
                }
            } catch (error) {
                console.error(`Error processing copy trading pair ${pair.copy_trading_id}:`, error);
            }
        }
    } catch (error) {
        console.error('Error in copy trading worker:', error);
    }
}

// Start monitoring
const interval = setInterval(processCopyTrading, 10000); // Check every 10 seconds

// Handle worker messages
parentPort.on('message', (message) => {
    if (message === 'stop') {
        clearInterval(interval);
        pool.end().then(() => {
            parentPort?.postMessage('stopped');
        });
    }
});

// Initial processing
processCopyTrading().catch(console.error);

// Handle worker termination
process.on('exit', async () => {
    clearInterval(interval);
    await pool.end();
}); 