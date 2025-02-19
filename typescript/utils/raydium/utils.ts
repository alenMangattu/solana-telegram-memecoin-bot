import { Connection, VersionedTransaction } from '@solana/web3.js';
import logger from '../logger';
import { SolanaUtils } from '../solanaUtils';

export const isValidAmm = (programId: string): boolean => {
    // Your logic to validate the AMM
    return programId === 'expectedProgramId'; // Example condition
};

export async function sendUnconfirmedTransaction(connection: Connection, tx: VersionedTransaction) {
    try {
        const rawTx = tx.serialize();
        const txId = await connection.sendRawTransaction(rawTx, {
            skipPreflight: true,
            preflightCommitment: 'processed'
        });
        
        logger.info(`Transaction sent: ${txId}`);
        return txId;
        
    } catch (error) {
        logger.error('Error sending transaction:', error);
        throw error;
    }
}

export async function confirmLater(connection: Connection, txId: string, retries = 5) {
    const solanaUtils = new SolanaUtils(connection.rpcEndpoint);

    for (let i = 0; i < retries; i++) {
        try {
            logger.info(`Confirming transaction ${txId}`);
            await solanaUtils.confirmTransactionWrapper(txId);
            
            logger.info(`Transaction ${txId} confirmed`);
            return true;
            
        } catch (error) {
            logger.warn(`Confirmation attempt ${i + 1} failed:`, error);
            await new Promise(resolve => setTimeout(resolve, 5000)); // Wait 5s
        }
    }
    
    logger.error(`Transaction ${txId} not confirmed after ${retries} attempts`);
    return false;
}
