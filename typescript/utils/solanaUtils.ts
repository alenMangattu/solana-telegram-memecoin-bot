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
        this.connection = new Connection(clusterUrl, {
            commitment: 'confirmed',
            disableRetryOnRateLimit: false,
            confirmTransactionInitialTimeout: 60000 as number,
            // Disable WebSocket subscriptions to avoid connection issues
            wsEndpoint: '' // Changed to an empty string to match the expected type
        });
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
            logger.info(`Creating memecoin ${name} (${symbol}) with supply ${supply}`);
            
            const updateCallback = async (message: string) => {
                try {
                    if (!chatId) {
                        logger.warn('No chat ID provided for update message');
                        return;
                    }

                    await axios.post('http://localhost:8888/api/telegram/send', {
                        chatId: chatId,
                        text: message
                    }).catch(error => {
                        logger.error('Failed to send update message:', error);
                    });
                } catch (error) {
                    logger.error('Failed to send update message:', error);
                }
            };
            
            const keypairPath = await this.saveWalletKeypair(walletPrivateKey);
            
            // Add retry logic for token creation
            let retries = 3;
            let tokenAddress: string | null = null;
            
            while (retries > 0 && !tokenAddress) {
                try {
                    await updateCallback(`Starting creation of memecoin: ${name} (${symbol}).`);
                    
                    tokenAddress = await this.createTokenWithCli(
                        keypairPath,
                        name,
                        symbol,
                        decimals,
                        supply,
                        metadataUri,
                        revokeMintAuthority,
                        revokeFreezeAuthority,
                        updateCallback
                    );
                    break;
                } catch (error) {
                    retries--;
                    if (retries === 0) throw error;
                    logger.warn(`Token creation failed, retrying... (${retries} attempts left)`);
                    await new Promise(resolve => setTimeout(resolve, 2000)); // Wait 2 seconds before retry
                }
            }

            await this.cleanupKeypairFile(keypairPath);

            if (!tokenAddress) {
                throw new Error('Failed to create token after multiple attempts');
            }

            await updateCallback(`Memecoin ${name} (${symbol}) created successfully with address: ${tokenAddress}.`);
            return tokenAddress;
        } catch (error: unknown) {
            logger.error('Error creating memecoin:', error);
            const errorMessage = error instanceof Error ? error.message : 'Unknown error';
            throw new Error('Failed to create memecoin: ' + errorMessage);
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

    private async createMetadata(
        mint: PublicKey,
        wallet: Keypair,
        name: string,
        symbol: string,
        uri: string
    ): Promise<void> {
        try {
            // Get metadata account PDA
            const [metadataAccount] = PublicKey.findProgramAddressSync(
                [
                    Buffer.from('metadata'),
                    METADATA_PROGRAM_ID.toBuffer(),
                    mint.toBuffer(),
                ],
                METADATA_PROGRAM_ID
            );

            // Create metadata
            const metadataData: DataV2 = {
                name,
                symbol,
                uri,
                sellerFeeBasisPoints: 0,
                creators: null,
                collection: null,
                uses: null,
                // version: 0,
            };

            // Create metadata instruction
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

            // Create and send transaction
            const metadataTx = new Transaction().add(createMetadataInstruction);
            const metadataSignature = await this.connection.sendTransaction(
                metadataTx,
                [wallet],
                { preflightCommitment: 'confirmed' }
            );
            await this.connection.confirmTransaction(metadataSignature, 'confirmed');
            
            logger.info('Metadata created successfully');
        } catch (error) {
            logger.error('Error creating metadata:', error);
            throw new Error(`Failed to create metadata: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    // Update confirmTransaction to use polling instead of WebSocket subscription
    private async confirmTransaction(signature: string, commitment: Commitment = 'confirmed'): Promise<void> {
        const latestBlockhash = await this.connection.getLatestBlockhash();
        await this.connection.confirmTransaction({
            signature,
            blockhash: latestBlockhash.blockhash,
            lastValidBlockHeight: latestBlockhash.lastValidBlockHeight
        }, commitment);
    }
} 