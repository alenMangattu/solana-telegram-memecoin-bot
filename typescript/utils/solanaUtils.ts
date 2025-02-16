import { Connection, Keypair, PublicKey, Transaction, type Commitment } from '@solana/web3.js';
import {
    createMint,
    getOrCreateAssociatedTokenAccount,
    mintTo,
    TOKEN_PROGRAM_ID,
    setAuthority,
    AuthorityType
} from '@solana/spl-token';
import { Metadata } from '@metaplex-foundation/mpl-token-metadata';
import bs58 from 'bs58';
import logger from './logger';
import type { WalletInfo, MemecoinConfig, TokenMetadata } from './types';
import { getPoolKeys } from './raydium/raydium';
import axios from 'axios';
import path from 'path';
import os from 'os';
import fs from 'fs';
import { v4 as uuidv4 } from 'uuid';
// Removed CreateMetadataV2 import as it is not exported from the module
import { exec } from 'child_process';
import { 
    createCreateMetadataAccountV3Instruction,
    PROGRAM_ID as METADATA_PROGRAM_ID,
    type DataV2
} from '@metaplex-foundation/mpl-token-metadata';

interface WalletData {
    publicKey: string;
    privateKey: string; // Removed duplicate privateKey declaration
}

export class SolanaUtils {
    private connection: Connection;
    private tokenListCache: Map<string, any>;
    private lastCacheUpdate: number;
    private readonly CACHE_DURATION = 3600000; // 1 hour

    constructor(clusterUrl: string) {
        logger.info(`Initializing SolanaUtils with cluster URL: ${clusterUrl}`);
        this.connection = new Connection(clusterUrl, {
            commitment: 'confirmed',
            disableRetryOnRateLimit: false,
            confirmTransactionInitialTimeout: 60000,
            wsEndpoint: undefined, // Explicitly disable WebSocket
        });
        logger.info('Connection initialized');
        this.tokenListCache = new Map();
        this.lastCacheUpdate = 0;
    }

    async createWallet(): Promise<WalletInfo> {
        try {
            const keypair = Keypair.generate();
            return {
                publicKey: keypair.publicKey.toString(),
                privateKey: bs58.encode(keypair.secretKey),
            };
        } catch (error) {
            logger.error('Error creating wallet:', error);
            throw new Error('Failed to create wallet');
        }
    }

    async getBalance(address: string): Promise<number> {
        try {
            const publicKey = new PublicKey(address);
            const balance = await this.connection.getBalance(publicKey);
            return balance / 1e9; // Convert lamports to SOL
        } catch (error) {
            logger.error('Error getting balance:', error);
            throw new Error('Failed to get balance');
        }
    }

    async createMemecoin(
        walletPrivateKey: string,
        name: string,
        symbol: string,
        decimals: number,
        supply: number,
        metadataUri: string,
        revokeMintAuthority: boolean,
        revokeFreezeAuthority: boolean,
        chatId: number
    ): Promise<string> {
        try {
            logger.info(`Creating memecoin: ${name} (${symbol}) with supply ${supply}`);
            
            // Log the wallet private key (for debugging purposes, ensure this is secure)
            logger.debug(`Using wallet private key: ${walletPrivateKey}`);

            const keypairPath = await this.saveWalletKeypair(walletPrivateKey);
            logger.info(`Keypair saved at: ${keypairPath}`);

            // Add retry logic for token creation
            let retries = 3;
            let tokenAddress: string | null = null;

            while (retries > 0 && !tokenAddress) {
                try {
                    logger.info(`Attempting to create token...`);
                    
                    tokenAddress = await this.createTokenWithCli(
                        keypairPath,
                        name,
                        symbol,
                        decimals,
                        supply,
                        metadataUri,
                        revokeMintAuthority,
                        revokeFreezeAuthority,
                        async (message: string) => {
                            logger.info(`Update: ${message}`);
                            // Send update message to chat (if applicable)
                            await axios.post('http://localhost:8888/api/telegram/send', {
                                chatId: chatId,
                                text: message
                            }).catch(error => {
                                logger.error('Failed to send update message:', error);
                            });
                        }
                    );

                    logger.info(`Token created successfully with address: ${tokenAddress}`);
                    break; // Exit the loop if successful
                } catch (error: unknown) {
                    retries--;
                    const errorMessage = error instanceof Error ? error.message : String(error);
                    logger.error(`Token creation failed, retries left: ${retries}. Error: ${errorMessage}`);
                    if (retries === 0) throw error; // Rethrow if no retries left
                    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait before retrying
                }
            }
            await this.cleanupKeypairFile(keypairPath);
            logger.info(`Keypair file cleaned up: ${keypairPath}`);
            return tokenAddress as string; // Assert that tokenAddress is a string
        } catch (error) {
            logger.error('Error creating memecoin:', error);
            throw new Error(`Failed to create memecoin: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async saveWalletKeypair(privateKey: string): Promise<string> {
        try {
            // Create temporary directory if it doesn't exist
            const tempDir = path.join(os.homedir(), '.config', 'solana', 'temp');
            await fs.promises.mkdir(tempDir, { recursive: true });
            
            // Create temporary file for keypair
            const filename = `${uuidv4()}.json`;
            const keypairPath = path.join(tempDir, filename);
            
            // Convert Base58 to byte array
            const secretKey = bs58.decode(privateKey);
            
            if (secretKey.length !== 64) {
                throw new Error(`Invalid secret key length: ${secretKey.length} (expected 64)`);
            }
            
            // Convert bytes to unsigned values and create JSON array
            const jsonArray = Array.from(secretKey);
            const jsonContent = JSON.stringify(jsonArray);
            
            // Write keypair to file
            await fs.promises.writeFile(keypairPath, jsonContent);
            await fs.promises.chmod(keypairPath, 0o600);
            
            logger.info(`Created keypair file at: ${keypairPath}`);
            return keypairPath;
        } catch (error) {
            logger.error('Error saving wallet keypair:', error);
            throw new Error('Failed to save wallet keypair');
        }
    }

    private async createTokenWithCli(
        keypairPath: string,
        name: string,
        symbol: string,
        decimals: number,
        supply: number,
        metadataUri: string,
        revokeMintAuthority: boolean,
        revokeFreezeAuthority: boolean,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<string> {
        try {
            if (updateCallback) {
                await updateCallback("🏗️ Creating token...");
            }

            // Create token using spl-token CLI with correct syntax
            const createTokenArgs = [
                'create-token',
                '--decimals', decimals.toString(),
                '--fee-payer', keypairPath,
                '--mint-authority', keypairPath
            ];

            // Don't create with freeze authority if we plan to revoke it
            if (!revokeFreezeAuthority) {
                createTokenArgs.push('--enable-freeze');
            }

            const createTokenProcess = await this.executeCommand('spl-token', createTokenArgs);

            if (createTokenProcess.stderr) {
                throw new Error(`Token creation failed: ${createTokenProcess.stderr}`);
            }

            // Extract token address from output
            const outputMatch = createTokenProcess.stdout.match(/Creating token ([a-zA-Z0-9]{32,44})/);
            if (!outputMatch) {
                throw new Error(`Failed to extract token address from output: ${createTokenProcess.stdout}`);
            }
            const tokenAddress = outputMatch[1].trim();

            logger.info(`Token created with address: ${tokenAddress}`);

            // Skip freeze authority check since we know if we created it with or without freeze authority
            if (!revokeFreezeAuthority) {
                logger.info('Token created without freeze authority');
            }

            if (updateCallback) {
                await updateCallback("🎨 Creating metadata...");
            }

            // Create metadata using Metaplex
            const wallet = Keypair.fromSecretKey(
                Buffer.from(JSON.parse(await fs.promises.readFile(keypairPath, 'utf-8')))
            );

            await this.createMetadata(
                new PublicKey(tokenAddress),
                wallet,
                name,
                symbol,
                metadataUri
            );

            if (updateCallback) {
                await updateCallback("💰 Minting initial supply...");
            }

            // Create token account
            const createAccountProcess = await this.executeCommand('spl-token', [
                'create-account',
                tokenAddress,
                '--fee-payer', keypairPath,
                '--owner', wallet.publicKey.toString()
            ]);

            if (createAccountProcess.stderr) {
                throw new Error(`Account creation failed: ${createAccountProcess.stderr}`);
            }

            const accountMatch = createAccountProcess.stdout.match(/Creating account ([a-zA-Z0-9]{32,44})/);
            if (!accountMatch) {
                throw new Error(`Failed to extract account address from output: ${createAccountProcess.stdout}`);
            }
            const tokenAccount = accountMatch[1].trim();

            // Mint initial supply
            const mintProcess = await this.executeCommand('spl-token', [
                'mint',
                tokenAddress,
                supply.toString(),
                tokenAccount,
                '--fee-payer', keypairPath,
                '--owner', keypairPath
            ]);

            if (mintProcess.stderr) {
                throw new Error(`Minting failed: ${mintProcess.stderr}`);
            }

            logger.info(`Minted ${supply} tokens to ${tokenAccount}`);

            // Revoke mint authority if requested
            if (revokeMintAuthority) {
                if (updateCallback) {
                    await updateCallback("🔒 Revoking mint authority...");
                }
                const revokeMintProcess = await this.executeCommand('spl-token', [
                    'authorize',
                    tokenAddress,
                    'mint',
                    '--disable',
                    '--fee-payer', keypairPath,
                    '--owner', keypairPath
                ]);

                if (revokeMintProcess.stderr) {
                    throw new Error(`Revoking mint authority failed: ${revokeMintProcess.stderr}`);
                }
                logger.info('Mint authority revoked');
            }

            return tokenAddress;
        } catch (error) {
            logger.error('Error creating token:', error);
            throw new Error(`Failed to create token: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async executeCommand(command: string, args: string[]): Promise<{ stdout: string; stderr: string }> {
        return new Promise((resolve, reject) => {
            const { exec } = require('child_process');
            const cmdString = `${command} ${args.join(' ')}`;
            logger.debug(`Executing command: ${cmdString}`);

            exec(cmdString, (error: Error | null, stdout: string, stderr: string) => {
                if (error) {
                    logger.error(`Command execution failed: ${error.message}`);
                    logger.error(`Command stderr: ${stderr}`);
                    reject(new Error(stderr || error.message));
                    return;
                }
                
                // Even if there's no error, log the output for debugging
                if (stdout) logger.debug(`Command stdout: ${stdout}`);
                if (stderr) logger.debug(`Command stderr: ${stderr}`);
                
                resolve({ stdout, stderr });
            });
        });
    }

    private async cleanupKeypairFile(keypairPath: string): Promise<void> {
        try {
            await fs.promises.unlink(keypairPath);
            logger.info(`Cleaned up keypair file: ${keypairPath}`);
        } catch (error) {
            logger.error('Error cleaning up keypair file:', error);
            // Don't throw here as this is cleanup code
        }
    }

    async addLiquidity(
        tokenMint: string,
        tokenAmount: number,
        solAmount: number,
        walletPrivateKey: string
    ): Promise<string> {
        try {
            const wallet = Keypair.fromSecretKey(bs58.decode(walletPrivateKey));

            // Get Raydium pool keys
            const poolKeys = await getPoolKeys(tokenMint, this.connection);
            if (!poolKeys) {
                throw new Error('Failed to get pool keys');
            }

            // Create and send transaction
            // Note: This is a simplified version. You'll need to implement the actual
            // Raydium liquidity addition logic here using their SDK

            return 'transaction_signature';
        } catch (error) {
            logger.error('Error adding liquidity:', error);
            throw new Error('Failed to add liquidity');
        }
    }

    private async checkRpcConnection(): Promise<void> {
        try {
            logger.info('Checking RPC connection...');
            const version = await this.connection.getVersion();
            logger.info(`Connected to Solana node version:`, version);
        } catch (error) {
            logger.error('Failed to connect to Solana RPC:', error);
            throw new Error('Could not connect to Solana RPC node');
        }
    }

    private async createMetadata(
        mint: PublicKey,
        wallet: Keypair,
        name: string,
        symbol: string,
        uri: string,
        maxRetries: number = 5
    ): Promise<void> {
        let lastError: Error | null = null;
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info(`Metadata creation attempt ${attempt}/${maxRetries} for token ${mint.toString()}`);
                
                // Add RPC connection check with more detailed logging
                logger.info('Checking RPC connection status...');
                const version = await this.connection.getVersion();
                logger.info('RPC connection verified. Node version:', version);
                
                // Create metadata with more detailed logging
                const [metadataAccount] = PublicKey.findProgramAddressSync(
                    [
                        Buffer.from('metadata'),
                        METADATA_PROGRAM_ID.toBuffer(),
                        mint.toBuffer(),
                    ],
                    METADATA_PROGRAM_ID
                );
                
                logger.info(`Generated metadata account address: ${metadataAccount.toString()}`);
                logger.info('Creating metadata with parameters:', { name, symbol, uri });

                // Create metadata
                const metadataData: DataV2 = {
                    name,
                    symbol,
                    uri,
                    sellerFeeBasisPoints: 0,
                    creators: null,
                    collection: null,
                    uses: null,
                };
                logger.info('Created metadata data structure:', metadataData);

                // Create metadata instruction
                logger.info('Creating metadata instruction...');
                const createMetadataInstruction = createCreateMetadataAccountV3Instruction(
                    {
                        metadata: metadataAccount,
                        mint: mint,
                        mintAuthority: wallet.publicKey,
                        payer: wallet.publicKey,
                        updateAuthority: wallet.publicKey,
                    },
                    {
                        createMetadataAccountArgsV3: {
                            data: metadataData,
                            isMutable: true,
                            collectionDetails: null,
                        }
                    }
                );
                logger.info('Metadata instruction created');

                // Send and confirm transaction
                logger.info('Building transaction...');
                const metadataTx = new Transaction().add(createMetadataInstruction);
                
                logger.info('Getting latest blockhash...');
                const latestBlockhash = await this.connection.getLatestBlockhash();
                metadataTx.recentBlockhash = latestBlockhash.blockhash;
                metadataTx.feePayer = wallet.publicKey;
                
                // Add retry logic for sending transaction
                let signature;
                let sendAttempts = 3;
                
                while (sendAttempts > 0) {
                    try {
                        signature = await this.connection.sendTransaction(metadataTx, [wallet], {
                            skipPreflight: false,
                            preflightCommitment: 'confirmed',
                            maxRetries: 5
                        });
                        break;
                    } catch (error) {
                        sendAttempts--;
                        if (sendAttempts === 0) throw error;
                        logger.warn(`Failed to send transaction, retrying... (${sendAttempts} attempts left)`);
                        await new Promise(resolve => setTimeout(resolve, 2000));
                    }
                }

                if (!signature) {
                    throw new Error('Failed to send transaction after all retries');
                }

                logger.info(`Transaction sent with signature: ${signature}`);
                
                // Use polling-based confirmation
                logger.info('Waiting for transaction confirmation...');
                await this.pollForSignatureStatus(signature, 120000, 2000);  // Use longer timeout
                
                logger.info('Metadata created successfully');

                if (attempt > 1) {
                    logger.info(`Metadata creation succeeded after ${attempt} attempts`);
                }
                return;
            } catch (error) {
                lastError = error instanceof Error ? error : new Error(String(error));
                logger.error(`Metadata creation attempt ${attempt} failed:`, {
                    error: lastError.message,
                    stack: lastError.stack
                });
                
                if (attempt < maxRetries) {
                    const delay = Math.min(attempt * 5000, 30000);
                    logger.info(`Waiting ${delay}ms before retry ${attempt + 1}/${maxRetries}...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            }
        }
        
        throw lastError || new Error('Failed to create metadata after all retries');
    }

    private async pollForSignatureStatus(
        signature: string,
        timeout: number = 120000,
        interval: number = 2000
    ): Promise<void> {
        const start = Date.now();
        logger.info(`Starting to poll for signature ${signature}`);
        
        // Get the latest blockhash and its last valid block height
        const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash();
        logger.info(`Got blockhash ${blockhash} with last valid block height ${lastValidBlockHeight}`);

        // Create a promise that will resolve when we receive a websocket notification
        const subscriptionPromise = new Promise<void>((resolve, reject) => {
            try {
                const subscriptionId = this.connection.onSignature(
                    signature,
                    (result) => {
                        if (result.err) {
                            reject(new Error(`Transaction failed: ${JSON.stringify(result.err)}`));
                        } else {
                            resolve();
                        }
                    },
                    'confirmed'
                );

                // Clean up subscription after timeout
                setTimeout(() => {
                    this.connection.removeSignatureListener(subscriptionId);
                }, timeout);
            } catch (err) {
                logger.warn('Failed to set up signature subscription:', err);
                // Continue with polling if subscription fails
            }
        });

        // Polling loop
        const pollingPromise = (async () => {
            let attempts = 0;
            while (Date.now() - start < timeout) {
                attempts++;
                try {
                    // Check if blockhash has expired
                    const currentBlockHeight = await this.connection.getBlockHeight();
                    if (currentBlockHeight > lastValidBlockHeight) {
                        throw new Error(`Transaction expired: block height ${currentBlockHeight} > last valid height ${lastValidBlockHeight}`);
                    }

                    // Get status using getSignatureStatuses
                    const statuses = await this.connection.getSignatureStatuses([signature], {
                        searchTransactionHistory: true
                    });

                    if (!statuses || !statuses.value[0]) {
                        logger.info(`No status yet. Current block height: ${currentBlockHeight}`);
                    } else {
                        const status = statuses.value[0];
                        if (status.err) {
                            throw new Error(`Transaction failed: ${JSON.stringify(status.err)}`);
                        }

                        logger.info('Transaction status:', {
                            slot: status.slot,
                            confirmations: status.confirmations,
                            confirmationStatus: status.confirmationStatus
                        });

                        if (status.confirmationStatus === 'confirmed' || 
                            status.confirmationStatus === 'finalized') {
                            return;
                        }
                    }

                    // Add exponential backoff
                    const backoffInterval = Math.min(interval * Math.pow(1.5, attempts - 1), 5000);
                    await new Promise(resolve => setTimeout(resolve, backoffInterval));
                } catch (error) {
                    const errorMessage = error instanceof Error ? error.message : String(error);
                    logger.error(`Error polling signature status (attempt ${attempts}):`, errorMessage);
                    
                    if (attempts >= 10 || 
                        errorMessage.includes('Transaction failed') || 
                        errorMessage.includes('Transaction expired')) {
                        throw error;
                    }

                    await new Promise(resolve => setTimeout(resolve, Math.min(interval * 1.5, 5000)));
                }
            }
            throw new Error(`Transaction confirmation timeout after ${timeout}ms`);
        })();

        // Wait for either the polling or the subscription to succeed
        try {
            await Promise.race([
                subscriptionPromise,
                pollingPromise
            ]);
            logger.info('Transaction confirmed successfully');
        } catch (error) {
            logger.error('Transaction confirmation failed:', error);
            throw error;
        }
    }

    private async confirmTransaction(signature: string, commitment: Commitment = 'confirmed'): Promise<void> {
        try {
            await this.pollForSignatureStatus(signature);
        } catch (error) {
            logger.error('Error confirming transaction:', error);
            throw new Error(`Failed to confirm transaction: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
} 