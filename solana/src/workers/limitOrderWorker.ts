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

async function processLimitOrders() {
    try {
        // Get active orders for this worker
        const query = `
            SELECT * FROM limit_orders 
            WHERE status IN ('active', 'pending')
            AND MOD(CAST(order_id AS INTEGER), $1) = $2
        `;
        const result = await pool.query(query, [totalWorkers, workerId]);
        const orders = result.rows;

        for (const order of orders) {
            try {
                // Check if order can be executed
                const currentPrice = await orderService.getTokenPrice(order.tokenMint);
                if (currentPrice) {
                    await orderService.checkAndExecuteOrder(order, currentPrice);
                }
            } catch (error) {
                console.error(`Error processing order ${order.orderId}:`, error);
            }
        }
    } catch (error) {
        console.error('Error in limit order worker:', error);
    }
}

// Start monitoring
const interval = setInterval(processLimitOrders, 5000); // Check every 5 seconds

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
processLimitOrders().catch(console.error);

// Handle worker termination
process.on('exit', async () => {
    clearInterval(interval);
    await pool.end();
}); 