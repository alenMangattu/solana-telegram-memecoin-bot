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
import { HeliusRpcInterface } from './heliusRpcInterface';
import dotenv from 'dotenv';
import { Helius } from 'helius-sdk';

dotenv.config(); // Load environment variables

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
    private heliusRpc: HeliusRpcInterface | null = null;
    private heliusSdk: Helius | null = null;

    constructor(clusterUrl: string, heliusApiKey?: string) {
        logger.info(`Initializing SolanaUtils with cluster URL: ${clusterUrl}`);
        this.connection = new Connection(clusterUrl, {
            commitment: 'confirmed',
            wsEndpoint: clusterUrl.replace('https', 'wss'),
        });
        logger.info('Connection initialized');
        this.tokenListCache = new Map();
        this.lastCacheUpdate = 0;

        // Initialize both Helius RPC and SDK if API key is provided
        if (heliusApiKey) {
            // Use devnet URL if clusterUrl contains 'devnet'
            const isDevnet = clusterUrl.includes('devnet');
            const heliusUrl = isDevnet
                ? 'https://devnet.helius-rpc.com/'
                : 'https://mainnet.helius-rpc.com/';

            this.heliusRpc = new HeliusRpcInterface(heliusApiKey, heliusUrl);
            this.heliusSdk = new Helius(heliusApiKey);
            logger.info(`Helius RPC and SDK initialized for ${isDevnet ? 'devnet' : 'mainnet'}`);
        }
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
                    // const delay = Math.min(attempt * 5000, 30000);
                    // logger.info(`Waiting ${delay}ms before retry ${attempt + 1}/${maxRetries}...`);
                    // await new Promise(resolve => setTimeout(resolve, delay));
                }
            }
        }

        throw lastError || new Error('Failed to create metadata after all retries');
    }

    private async confirmTransaction(signature: string, timeout: number = 60000): Promise<void> {
        logger.info(`Confirming transaction ${signature}`);
        const maxRetries = 10;
        const startTime = Date.now();

        for (let attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Check if we've exceeded timeout
                if (Date.now() - startTime > timeout) {
                    throw new TransactionTimeoutError(signature, timeout / 1000);
                }

                // Try Helius RPC first if available
                if (this.heliusRpc) {
                    const response = await this.heliusRpc.getSignatureStatuses([signature], true);
                    logger.info(`Helius response: ${JSON.stringify(response)}`);
                    const status = response.value[0];

                    if (status === null) {
                        logger.info(`Attempt ${attempt + 1}: No status yet from Helius, waiting...`);
                        await new Promise(resolve => setTimeout(resolve, 1000));
                        continue;
                    }

                    // Check for error in status
                    if (status.err) {
                        const errorDetails = JSON.stringify(status.err);
                        logger.error(`Transaction failed with error (Helius): ${errorDetails}`);
                        throw new Error(`Transaction failed: ${errorDetails}`);
                    }

                    // Check for confirmation status
                    if (status.confirmationStatus === 'confirmed' || 
                        status.confirmationStatus === 'finalized' ||
                        (status.status && status.status.Ok === null)) {
                        logger.info(`Transaction ${signature} confirmed with status: ${status.confirmationStatus || 'finalized'} (via Helius)`);
                        return;
                    }

                    logger.info(`Attempt ${attempt + 1}: Helius Status: ${JSON.stringify(status)}`);
                } else {
                    // Fallback to regular Solana RPC
                    const status = await this.connection.getSignatureStatus(signature);

                    if (!status || !status.value) {
                        logger.info(`Attempt ${attempt + 1}: No status yet from Solana RPC, waiting...`);
                        await new Promise(resolve => setTimeout(resolve, 2000)); // Wait before retrying
                        continue;
                    }

                    if (status.value.err) {
                        const errorDetails = JSON.stringify(status.value.err);
                        logger.error(`Transaction failed with error (Solana RPC): ${errorDetails}`);
                        throw new Error(`Transaction failed: ${errorDetails}`);
                    }

                    if (status.value.confirmationStatus === 'confirmed' ||
                        status.value.confirmationStatus === 'finalized') {
                        logger.info(`Transaction ${signature} confirmed with status: ${status.value.confirmationStatus} (via Solana RPC)`);
                        return;
                    }

                    logger.info(`Attempt ${attempt + 1}: Solana RPC Status: ${status.value.confirmationStatus}`);
                }

                // Calculate delay with exponential backoff
                const delay = Math.min(1000 * Math.pow(2, attempt), 10000); // Max 10 second delay
                // await new Promise(resolve => setTimeout(resolve, delay));

            } catch (error) {
                const errorMessage = error instanceof Error ? error.message : String(error);
                logger.error(`Error confirming transaction (attempt ${attempt + 1}/${maxRetries}):`, errorMessage);

                if (attempt === maxRetries - 1) {
                    throw new Error(`Failed to confirm transaction after ${maxRetries} attempts: ${errorMessage}`);
                }

                // await new Promise(resolve => setTimeout(resolve, 1000));
            }
        }

        throw new TransactionTimeoutError(signature, timeout / 1000);
    }

    async sendAndConfirmTransaction(
        transaction: Transaction,
        signers: Array<Keypair>,
        options?: ConfirmOptions & {
            validatorAcls?: string[];
            skipPreflight?: boolean;
        },
        updateCallback?: (message: string) => Promise<void>
    ): Promise<string> {
        const maxRetries = 3;
        let lastError: Error | null = null;

        for (let attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Get fresh blockhash for each attempt
                const { blockhash, lastValidBlockHeight } = await this.connection.getLatestBlockhash('confirmed');
                transaction.recentBlockhash = blockhash;
                transaction.feePayer = signers[0].publicKey;
                transaction.signatures = [];
                transaction.sign(...signers);

                // Try Helius first if available
                if (this.heliusSdk && attempt === 0) { // Only try Helius on first attempt
                    try {
                        logger.info('Attempting to use Helius SDK for transaction');
                        const signature = await this.heliusSdk.rpc.sendTransaction(
                            transaction,
                            {
                                skipPreflight: options?.skipPreflight ?? false,
                                maxRetries: 1, // Let our outer retry handle retries
                            }
                        );

                        logger.info(`Transaction sent with signature: ${signature} (via Helius)`);
                        if (updateCallback) {
                            await updateCallback(`Transaction sent with signature: ${signature}`);
                        }

                        await this.confirmTransaction(signature);
                        return signature;
                    } catch (heliusError) {
                        const errorMessage = heliusError instanceof Error ? heliusError.message : String(heliusError);
                        if (!errorMessage.includes('429') && !errorMessage.includes('BlockhashNotFound')) {
                            throw heliusError;
                        }
                        logger.warn('Helius error, falling back to Solana RPC:', errorMessage);
                    }
                }

                // Fallback to regular Solana Web3.js
                logger.info('Using Solana Web3.js for transaction');
                const commitment = options?.commitment ?? 'confirmed';
                
                const rawTransaction = transaction.serialize();
                const signature = await this.connection.sendRawTransaction(
                    rawTransaction,
                    {
                        skipPreflight: options?.skipPreflight,
                        preflightCommitment: commitment,
                        maxRetries: 1, // We handle retries here
                    }
                );

                logger.info(`Transaction sent with signature: ${signature} (via Solana RPC)`);
                if (updateCallback) {
                    await updateCallback(`Transaction sent with signature: ${signature}`);
                }

                await this.confirmTransaction(signature);
                return signature;

            } catch (error) {
                lastError = error instanceof Error ? error : new Error(String(error));
                const errorMessage = lastError.message;

                logger.error(`Transaction attempt ${attempt + 1} failed:`, errorMessage);
                if (updateCallback) {
                    await updateCallback(`Transaction attempt ${attempt + 1} failed: ${errorMessage}`);
                }

                // If it's not a blockhash error, throw immediately
                if (!errorMessage.includes('BlockhashNotFound') && 
                    !errorMessage.includes('blockhash')) {
                    throw lastError;
                }

                // Wait before retry
                if (attempt < maxRetries - 1) {
                    const delay = Math.min(1000 * Math.pow(2, attempt), 4000);
                    logger.info(`Waiting ${delay}ms before retry...`);
                    await new Promise(resolve => setTimeout(resolve, delay));
                }
            }
        }

        throw lastError || new Error('Transaction failed after all retries');
    }

    public async confirmTransactionWrapper(signature: string, timeout: number = 60000): Promise<void> {
        return this.confirmTransaction(signature, timeout);
    }

    /**
     * Gets transaction status using Helius RPC
     * @param signature Transaction signature
     * @returns Transaction status or null if not found
     */
    async getTransactionStatus(signature: string): Promise<SignatureStatus | null> {
        try {
            if (!this.heliusRpc) {
                throw new Error('Helius RPC not initialized - API key required');
            }

            const response = await this.heliusRpc.getSignatureStatuses([signature], true);
            return response.value[0];
        } catch (error) {
            logger.error('Error getting transaction status:', error);
            throw new Error(`Failed to get transaction status: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Gets all tokens owned by an address using Helius RPC
     * @param ownerAddress Owner's wallet address
     * @returns Array of token accounts
     */
    async getAllTokensByOwner(ownerAddress: string) {
        try {
            if (!this.heliusRpc) {
                throw new Error('Helius RPC not initialized - API key required');
            }

            return await this.heliusRpc.getAllTokensByOwner(ownerAddress);
        } catch (error) {
            logger.error('Error getting tokens by owner:', error);
            throw new Error(`Failed to get tokens: ${error instanceof Error ? error.message : String(error)}`);
        }
    }
} 