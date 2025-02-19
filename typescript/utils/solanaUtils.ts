import { Connection, Keypair, PublicKey, Transaction, type Commitment, type ConfirmOptions, type SignatureStatus } from '@solana/web3.js';
import bs58 from 'bs58';
import logger from './logger';
import type { WalletInfo } from './types';
import { getPoolKeys } from './raydium/raydium';
import axios from 'axios';
import path from 'path';
import os from 'os';
import fs from 'fs';
import { v4 as uuidv4 } from 'uuid';
import { 
    createCreateMetadataAccountV3Instruction,
    PROGRAM_ID as METADATA_PROGRAM_ID,
    type DataV2
} from '@metaplex-foundation/mpl-token-metadata';

interface WalletData {
    publicKey: string;
    privateKey: string; // Removed duplicate privateKey declaration
}

class TransactionTimeoutError extends Error {
    constructor(signature: string, timeoutSeconds: number) {
        super(`Transaction ${signature} timed out after ${timeoutSeconds} seconds`);
        this.name = 'TransactionTimeoutError';
    }
}

class TransactionDroppedError extends Error {
    constructor(signature: string) {
        super(`Transaction ${signature} was dropped (block height exceeded)`);
        this.name = 'TransactionDroppedError';
    }
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
            wsEndpoint: clusterUrl.replace('https', 'wss'),  // Correct WebSocket URL
            disableRetryOnRateLimit: false,
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
            
            // Check RPC connection before proceeding
            await this.checkRpcConnection();
            
            const keypairPath = await this.saveWalletKeypair(walletPrivateKey);
            logger.info(`Keypair saved at: ${keypairPath}`);

            let retries = 3;
            let tokenAddress: string | null = null;

            while (retries > 0 && !tokenAddress) {
                try {
                    logger.info(`Attempting to create token (${retries} retries left)...`);
                    
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
                            try {
                                await axios.post('http://localhost:8888/api/telegram/send', {
                                    chatId: chatId,
                                    text: message
                                });
                            } catch (error) {
                                logger.error('Failed to send update message:', error);
                            }
                        }
                    );

                    logger.info(`Token created successfully with address: ${tokenAddress}`);
                    break;
                } catch (error) {
                    retries--;
                    const errorMessage = error instanceof Error ? error.message : String(error);
                    logger.error(`Token creation attempt failed: ${errorMessage}`);
                    if (retries === 0) throw error;
                    await new Promise(resolve => setTimeout(resolve, 2000));
                }
            }

            await this.cleanupKeypairFile(keypairPath);
            return tokenAddress as string;
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

            if (updateCallback) {
                await updateCallback(`Token created with address: ${tokenAddress}`);
            }
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
                metadataUri,
                updateCallback,
                5
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
        updateCallback?: (message: string) => Promise<void>,
        maxRetries: number = 5
    ): Promise<void> {
        let lastError: Error | null = null;
        
        for (let attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info(`Creating metadata attempt ${attempt}/${maxRetries}`);
                
                if (updateCallback) {
                    await updateCallback(`Creating metadata attempt ${attempt}/${maxRetries}`);
                }

                const metadataData: DataV2 = {
                    name,
                    symbol,
                    uri,
                    sellerFeeBasisPoints: 0,
                    creators: null,
                    collection: null,
                    uses: null
                };

                const [metadataPDA] = PublicKey.findProgramAddressSync(
                    [
                        Buffer.from('metadata'),
                        METADATA_PROGRAM_ID.toBuffer(),
                        mint.toBuffer(),
                    ],
                    METADATA_PROGRAM_ID
                );

                const instruction = createCreateMetadataAccountV3Instruction(
                    {
                        metadata: metadataPDA,
                        mint: mint,
                        mintAuthority: wallet.publicKey,
                        payer: wallet.publicKey,
                        updateAuthority: wallet.publicKey,
                    },
                    {
                        createMetadataAccountArgsV3: {
                            data: metadataData,
                            isMutable: true,
                            collectionDetails: null
                        }
                    }
                );

                const metadataTx = new Transaction().add(instruction);
                
                logger.info('Getting latest blockhash...');
                const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash();
                metadataTx.recentBlockhash = blockhash;
                metadataTx.feePayer = wallet.publicKey;
                
                // Send and confirm transaction
                const signature = await this.sendAndConfirmTransaction(metadataTx, [wallet], {
                    commitment: 'confirmed',
                    maxRetries: 5,
                    skipPreflight: false
                }, updateCallback);

                logger.info(`Metadata transaction sent: ${signature}`);
                if (updateCallback) {
                    await updateCallback("Metadata transaction sent: " + signature);
                }
                logger.info('Metadata created successfully');
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

    private async pollForSignatureStatus(signature: string, maxRetries: number = 10): Promise<void> {
        logger.info(`Confirming transaction ${signature}`);
        
        for (let attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Get status using getSignatureStatus
                const status = await this.connection.getSignatureStatus(signature);
                
                if (!status || !status.value) {
                    logger.info(`Attempt ${attempt + 1}: No status yet, waiting...`);
                    await new Promise(resolve => setTimeout(resolve, 2000));
                    continue;
                }

                if (status.value.err) {
                    const errorDetails = JSON.stringify(status.value.err);
                    logger.error(`Transaction failed with error: ${errorDetails}`);
                    throw new Error(`Transaction failed: ${errorDetails}`);
                }

                if (status.value.confirmationStatus === 'confirmed' || 
                    status.value.confirmationStatus === 'finalized') {
                    logger.info(`Transaction ${signature} confirmed with status: ${status.value.confirmationStatus}`);
                    return;
                }

                logger.info(`Attempt ${attempt + 1}: Status: ${status.value.confirmationStatus}`);
                // Increase delay for subsequent attempts
                const delay = Math.min(2000 * (attempt + 1), 10000); // Max 10 second delay
                await new Promise(resolve => setTimeout(resolve, delay));
            } catch (error) {
                const errorMessage = error instanceof Error ? error.message : String(error);
                logger.error(`Error confirming transaction (attempt ${attempt + 1}/${maxRetries}):`, errorMessage);
                
                if (attempt === maxRetries - 1) {
                    throw new Error(`Failed to confirm transaction after ${maxRetries} attempts: ${errorMessage}`);
                }
                
                // Exponential backoff for retries
                const delay = Math.min(2000 * Math.pow(2, attempt), 15000); // Max 15 second delay
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
        
        throw new TransactionTimeoutError(signature, 10);
    }

    private async confirmTransaction(signature: string, timeout: number = 60000): Promise<void> {
        logger.info(`Confirming transaction ${signature}`);
        
        try {
            const latestBlockhash = await this.connection.getLatestBlockhash();
            
            const response = await this.connection.confirmTransaction({
                signature,
                blockhash: latestBlockhash.blockhash,
                lastValidBlockHeight: latestBlockhash.lastValidBlockHeight
            }, 'confirmed');

            if (response.value.err) {
                throw new Error(`Transaction failed: ${JSON.stringify(response.value.err)}`);
            }

            logger.info(`Transaction ${signature} confirmed successfully`);
        } catch (error) {
            logger.error(`Error confirming transaction: ${error}`);
            throw error;
        }
    }

    async sendAndConfirmTransaction(
        transaction: Transaction,
        signers: Array<Keypair>,
        options?: ConfirmOptions,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<string> {
        const commitment = options?.commitment ?? 'confirmed';
        const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash(commitment);
        
        transaction.recentBlockhash = blockhash;
        transaction.sign(...signers);
    
        const signature = await this.connection.sendRawTransaction(transaction.serialize(), {
            skipPreflight: options?.skipPreflight,
            preflightCommitment: options?.preflightCommitment || commitment,
            maxRetries: options?.maxRetries
        });
    
        logger.info(`Transaction sent with signature: ${signature}`);
    
        // if (updateCallback) {
        //     await updateCallback(`Transaction sent with signature: ${signature}`);
        // }
    
        try {
            await this.confirmTransaction(signature);
            // if (updateCallback) {
            //     // await updateCallback(`Transaction ${signature} confirmed successfully`);
            // }
            return signature;
        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            logger.error(`Transaction confirmation failed: ${errorMessage}`);
            if (updateCallback) {
                await updateCallback(`Transaction failed: ${errorMessage}`);
            }
            throw error;
        }
    }

    public async confirmTransactionWrapper(signature: string, timeout: number = 60000): Promise<void> {
        return this.confirmTransaction(signature, timeout);
    }
} 