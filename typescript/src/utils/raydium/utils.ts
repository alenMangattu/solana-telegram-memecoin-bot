import { Connection, Transaction, Keypair, TransactionInstruction } from '@solana/web3.js';
import logger from '../logger';

export const isValidAmm = (programId: string): boolean => {
    // Your logic to validate the AMM
    return programId === 'expectedProgramId'; // Example condition
};

export async function sendUnconfirmedTransaction(
    connection: Connection,
    transaction: Transaction,
    signers: Keypair[]
): Promise<string> {
    try {
        const signature = await connection.sendTransaction(transaction, signers, {
            skipPreflight: true
        });
        return signature;
    } catch (error) {
        logger.error('Error sending unconfirmed transaction:', error);
        throw error;
    }
}

export async function confirmLater(connection: Connection, signature: string): Promise<void> {
    try {
        const result = await connection.confirmTransaction(signature, 'confirmed');
        if (result.value.err) {
            throw new Error(`Transaction failed: ${JSON.stringify(result.value.err)}`);
        }
    } catch (error) {
        logger.error('Error confirming transaction:', error);
        throw error;
    }
}

export async function createTransaction(
    connection: Connection,
    instructions: TransactionInstruction[],
    signers: Keypair[]
): Promise<Transaction> {
    const transaction = new Transaction();
    transaction.add(...instructions);
    const { blockhash } = await connection.getLatestBlockhash();
    transaction.recentBlockhash = blockhash;
    transaction.feePayer = signers[0].publicKey;
    return transaction;
}

export async function sendAndConfirmTransaction(
    connection: Connection,
    transaction: Transaction,
    signers: Keypair[]
): Promise<string> {
    const signature = await connection.sendTransaction(transaction, signers);
    await confirmLater(connection, signature);
    return signature;
}