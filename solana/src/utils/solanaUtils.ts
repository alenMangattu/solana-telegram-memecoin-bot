import { Connection, Keypair, PublicKey, Transaction, TransactionExpiredTimeoutError, type Commitment, type ConfirmOptions, sendAndConfirmTransaction as webJsSendAndConfirmTransaction } from '@solana/web3.js';
import {
    createCreateMetadataAccountV3Instruction,
    PROGRAM_ID as METADATA_PROGRAM_ID,
    type DataV2
} from '@metaplex-foundation/mpl-token-metadata';
import bs58 from 'bs58';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { v4 as uuidv4 } from 'uuid';
import logger from './logger';
import { HeliusRpcInterface } from './heliusRpcInterface';
import dotenv from 'dotenv';
import { exec } from 'child_process';
import { promisify } from 'util';
import axios from 'axios';
import {
    createMint,
    getOrCreateAssociatedTokenAccount,
    mintTo,
    setAuthority,
    AuthorityType,
} from '@solana/spl-token';
import { TokenListProvider } from '@solana/spl-token-registry';
const execAsync = promisify(exec);

// Load environment variables from .env file
dotenv.config();

const TOKEN_PROGRAM_ID = new PublicKey('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA');

export class SolanaUtils {
    private connection: Connection;
    private heliusApiKey?: string;
    private heliusRpc: HeliusRpcInterface | null;

    constructor(rpcUrl: string, heliusApiKey?: string) {
        // Force higher timeout and commitment levels
        this.connection = new Connection(rpcUrl, {
            commitment: 'confirmed',
            confirmTransactionInitialTimeout: 60000
        });
        this.heliusApiKey = heliusApiKey;
        this.heliusRpc = heliusApiKey ? new HeliusRpcInterface(heliusApiKey, rpcUrl) : null;
    }

    getConnection(): Connection {
        return this.connection;
    }

    async checkRpcConnection(): Promise<void> {
        try {
            await this.connection.getLatestBlockhash();
        } catch (error) {
            logger.error('Failed to connect to RPC:', error);
            throw new Error('Failed to connect to Solana RPC');
        }
    }

    async executeCommand(command: string, args: string[]): Promise<{ stdout: string; stderr: string }> {
        const cmdString = `${command} ${args.join(' ')}`;
        try {
            return await execAsync(cmdString);
        } catch (error) {
            logger.error(`Command execution failed: ${cmdString}`, error);
            throw error;
        }
    }

    // Simplified and direct transaction method
    // Modified sendAndConfirmTransaction method for SolanaUtils class
    async sendAndConfirmTransaction(
        transaction: Transaction,
        signers: Keypair[],
        options: { commitment: Commitment; maxRetries?: number; skipPreflight?: boolean } = { commitment: 'confirmed' },
        updateCallback?: (message: string) => Promise<void>
    ): Promise<string> {
        try {
            if (updateCallback) {
                await updateCallback('Preparing transaction...');
            }

            // Get a fresh blockhash
            const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash(options.commitment);
            transaction.recentBlockhash = blockhash;
            transaction.lastValidBlockHeight = lastValidBlockHeight;
            transaction.feePayer = signers[0].publicKey;

            // Sign transaction
            transaction.sign(...signers);
            
            if (updateCallback) {
                await updateCallback('Sending transaction...');
            }

            // Send raw transaction
            const rawTransaction = transaction.serialize();
            const signature = await this.connection.sendRawTransaction(rawTransaction, {
                skipPreflight: options.skipPreflight === true,
                preflightCommitment: options.commitment,
                    maxRetries: options.maxRetries || 5
            });

            if (updateCallback) {
                await updateCallback(`Transaction sent with signature: ${signature}. Awaiting confirmation...`);
            }

            // Confirm with explicit timeout and retry logic
            let confirmed = false;
            let attempts = 0;
            const maxAttempts = 30; // More attempts for confirmation
            const interval = 2000; // 2 seconds interval

            while (!confirmed && attempts < maxAttempts) {
                try {
                    attempts++;
                    logger.info(`Confirming transaction ${attempts}/${maxAttempts}...`);

                    // Use a timeout promise to prevent hanging
                    const confirmationPromise = this.connection.confirmTransaction({
                        signature,
                        blockhash,
                        lastValidBlockHeight
                    }, options.commitment);

                    // Create a timeout promise
                    const timeoutPromise = new Promise((_, reject) => {
                        setTimeout(() => reject(new Error('Confirmation timeout')), 5); // 10-second timeout
                    });

                    // Race the promises
                    const response = await Promise.race([confirmationPromise, timeoutPromise]) as any;

                    logger.info(`Transaction confirmation response: ${JSON.stringify(response)}`);
                    if (response?.value?.err) {
                        logger.error(`Transaction error: ${JSON.stringify(response.value.err)}`);
                        throw new Error(`Transaction failed: ${JSON.stringify(response.value.err)}`);
                    }

                    confirmed = true;
                    logger.info(`Transaction confirmed after ${attempts} attempts: ${signature}`);

                    if (updateCallback) {
                        await updateCallback(`Transaction confirmed: ${signature}`);
                    }
                    return signature;
                } catch (error) {
                    logger.warn(`Confirmation attempt ${attempts}/${maxAttempts} failed: ${error instanceof Error ? error.message : String(error)}`);
                    if (attempts >= maxAttempts) {
                        throw error;
                    }
                    logger.info(`Retrying in ${interval}ms...`);
                    await this.sleep(interval);
                }
            }

            if (!confirmed) {
                throw new Error(`Failed to confirm transaction after ${maxAttempts} attempts`);
            }

            return signature;
        } catch (error) {
            logger.error('Transaction failed:', error);
            throw error;
        }
    }

    private sleep(ms: number): void {
        const end = Date.now() + ms;
        while (Date.now() < end) {
            // Busy-waiting
        }
    }

    async confirmTransaction(signature: string, commitment: Commitment = 'confirmed'): Promise<void> {
        const latestBlockhash = await this.connection.getLatestBlockhash();
        await this.connection.confirmTransaction({
            signature,
            blockhash: latestBlockhash.blockhash,
            lastValidBlockHeight: latestBlockhash.lastValidBlockHeight
        }, commitment);
    }

    async getTokenMetadata(tokenAddress: string) {
        const mintAddress = new PublicKey(tokenAddress);
    
        let tokenName: string;
        let tokenSymbol: string;
        let tokenLogo: string;
    
        const provider = await new TokenListProvider().resolve();
        const tokenList = provider.filterByChainId(Number(process.env.SOLANA_CHAIN_ID)).getList();
        const tokenMap = tokenList.reduce((map, item) => {
            map.set(item.address, item);
            return map;
        }, new Map());
    
        const tokenData = tokenMap.get(mintAddress.toBase58());
        tokenName = tokenData.name;
        tokenSymbol = tokenData.symbol;
        tokenLogo = tokenData.logoURI;
    }

    getHeliusApiUrl(): string | undefined {
        return this.heliusApiKey ? `https://api.helius.xyz/v0/assets` : undefined;
    }
}