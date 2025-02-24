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

const execAsync = promisify(exec);

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
}

export class MemecoinService {
    private connection: Connection;
    private solanaUtils: SolanaUtils;
    private heliusRpc: HeliusRpcInterface | null;

    constructor(connection: Connection, solanaUtils: SolanaUtils, heliusRpc: HeliusRpcInterface | null = null) {
        this.connection = connection;
        this.solanaUtils = solanaUtils;
        this.heliusRpc = heliusRpc;
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

            const wallet = Keypair.fromSecretKey(bs58.decode(walletPrivateKey));
            
            const updateCallback = async (message: string) => {
                            try {
                                await axios.post('http://localhost:8888/api/telegram/send', {
                                    chatId: chatId,
                                    text: message
                                });
                            } catch (error) {
                                logger.error('Failed to send update message:', error);
                            }
            };

            await updateCallback(`🏗️ Creating memecoin ${name} (${symbol})...`);

            // Create mint account
            const mint = await createMint(
                this.connection,
                wallet,
                wallet.publicKey,
                revokeFreezeAuthority ? null : wallet.publicKey,
                decimals,
                undefined,
                { commitment: 'confirmed' }
            );

            logger.info(`Created mint: ${mint.toBase58()}`);
            await updateCallback(`✅ Token created with address: ${mint.toBase58()}`);

            // Create metadata
            await updateCallback(`🎨 Creating metadata...`);
            await this.createMetadata(
                mint,
                wallet,
                name,
                symbol,
                metadataUri,
                updateCallback
            );

            // Create associated token account and mint initial supply
            await updateCallback(`💰 Minting initial supply...`);
            const tokenAccount = await getOrCreateAssociatedTokenAccount(
                this.connection,
                wallet,
                mint,
                wallet.publicKey
            );

            await mintTo(
                this.connection,
                wallet,
                mint,
                tokenAccount.address,
                wallet,
                supply,
                [],
                { commitment: 'confirmed' }
            );

            // Revoke mint authority if requested
            if (revokeMintAuthority) {
                await updateCallback(`🔒 Revoking mint authority...`);
                await setAuthority(
                    this.connection,
                    wallet,
                    mint,
                    wallet,
                    AuthorityType.MintTokens,
                    null
                );
                logger.info('Mint authority revoked');
            }

            await updateCallback(`✅ Memecoin created successfully!`);
            return mint.toBase58();

        } catch (error) {
            logger.error('Error creating memecoin:', error);
            throw new Error(`Failed to create memecoin: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async createMetadata(
        mint: PublicKey,
        wallet: Keypair,
        name: string,
        symbol: string,
        uri: string,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<void> {
        try {
            const [metadataAccount] = PublicKey.findProgramAddressSync(
                [
                    Buffer.from('metadata'),
                    METADATA_PROGRAM_ID.toBuffer(),
                    mint.toBuffer(),
                ],
                METADATA_PROGRAM_ID
            );

            const metadataData: DataV2 = {
                name,
                symbol,
                uri,
                sellerFeeBasisPoints: 0,
                creators: null,
                collection: null,
                uses: null
            };

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
                        collectionDetails: null
                    }
                }
            );

            const metadataTx = new Transaction().add(createMetadataInstruction);
            
            // Get recent blockhash
            const { blockhash } = await this.connection.getLatestBlockhash();
            metadataTx.recentBlockhash = blockhash;
            metadataTx.feePayer = wallet.publicKey;
            
            // Sign transaction
            metadataTx.sign(wallet);

            if (updateCallback) {
                await updateCallback('Sending metadata transaction...');
            }

            // Send transaction
            const signature = await this.connection.sendTransaction(
                metadataTx,
                [wallet],
                { preflightCommitment: 'confirmed' }
            );

            if (updateCallback) {
                await updateCallback(`Confirming metadata transaction: ${signature}`);
            }

            // Confirm transaction using connection directly
            await this.connection.confirmTransaction(signature, 'confirmed');

            logger.info(`Metadata created successfully: ${signature}`);

        } catch (error) {
            logger.error('Failed to create metadata:', error);
            throw error;
        }
    }

    private async saveWalletKeypair(privateKey: string): Promise<string> {
        try {
            const tempDir = path.join(os.homedir(), '.config', 'solana', 'temp');
            await fs.promises.mkdir(tempDir, { recursive: true });

            const filename = `${uuidv4()}.json`;
            const keypairPath = path.join(tempDir, filename);

            const secretKey = bs58.decode(privateKey);

            if (secretKey.length !== 64) {
                throw new Error(`Invalid secret key length: ${secretKey.length} (expected 64)`);
            }

            const jsonArray = Array.from(secretKey);
            const jsonContent = JSON.stringify(jsonArray);

            await fs.promises.writeFile(keypairPath, jsonContent);
            await fs.promises.chmod(keypairPath, 0o600);

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

            const createTokenArgs = [
                'create-token',
                '--decimals', decimals.toString(),
                '--fee-payer', keypairPath,
                '--mint-authority', keypairPath
            ];

            if (!revokeFreezeAuthority) {
                createTokenArgs.push('--enable-freeze');
            }

            const createTokenProcess = await this.solanaUtils.executeCommand('spl-token', createTokenArgs);

            if (createTokenProcess.stderr) {
                throw new Error(`Token creation failed: ${createTokenProcess.stderr}`);
            }

            const outputMatch = createTokenProcess.stdout.match(/Creating token ([a-zA-Z0-9]{32,44})/);
            if (!outputMatch) {
                throw new Error(`Failed to extract token address from output: ${createTokenProcess.stdout}`);
            }
            const tokenAddress = outputMatch[1].trim();

            if (updateCallback) {
                await updateCallback(`Token created with address: ${tokenAddress}`);
                await updateCallback("🎨 Creating metadata...");
            }

            // Wait for token creation to propagate through the network
            await this.sleep(3000);

            const wallet = Keypair.fromSecretKey(
                Buffer.from(JSON.parse(await fs.promises.readFile(keypairPath, 'utf-8')))
            );

            // Try alternative method - use CLI for metadata creation
            try {
                // First attempt with on-chain metadata creation
                await this.createMetadataWithTransaction(
                new PublicKey(tokenAddress),
                wallet,
                name,
                symbol,
                metadataUri,
                updateCallback
            );
            } catch (error) {
                logger.error("Transaction-based metadata creation failed, trying direct CLI approach", error);
                if (updateCallback) {
                    await updateCallback("⚠️ Retrying metadata creation with alternative method...");
                }

                // Wait a bit before trying the next approach
                await this.sleep(2000);

                // Create token account and mint initial supply first
            await this.mintInitialSupply(
                tokenAddress,
                keypairPath,
                wallet,
                supply,
                updateCallback
            );

                // Try CLI-based metadata creation as a fallback
                await this.createMetadataWithCli(
                    tokenAddress,
                    keypairPath,
                    name,
                    symbol,
                    metadataUri,
                    updateCallback
                );
            }

            // Revoke mint authority if requested
            if (revokeMintAuthority) {
                await this.revokeMintAuthority(tokenAddress, keypairPath, updateCallback);
            }

            return tokenAddress;
        } catch (error) {
            logger.error('Error creating token:', error);
            throw new Error(`Failed to create token: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    // New method: try metadata creation with CLI commands if transaction method fails
    private async createMetadataWithCli(
        tokenAddress: string,
        keypairPath: string,
        name: string,
        symbol: string,
        uri: string,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<void> {
        if (updateCallback) {
            await updateCallback("🎭 Setting token metadata via CLI...");
        }

        try {
            // First, check if spl-token supports metadata commands
            const metadataArgs = [
                'metadata',
                tokenAddress,
                '--name', `"${name}"`,
                '--symbol', `"${symbol}"`,
                '--uri', `"${uri}"`,
                '--fee-payer', keypairPath,
            ];

            await this.solanaUtils.executeCommand('spl-token', metadataArgs);

            if (updateCallback) {
                await updateCallback("✅ Metadata created successfully via CLI!");
            }
        } catch (error) {
            logger.error('CLI metadata creation failed:', error);
            throw new Error(`Failed to create metadata via CLI: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async createMetadataWithTransaction(
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

                // Create a new transaction with just the metadata instruction
                const metadataTx = new Transaction().add(instruction);

                // Send and confirm with our modified method
                const signature = await this.solanaUtils.sendAndConfirmTransaction(
                    metadataTx,
                    [wallet],
                    {
                    commitment: 'confirmed',
                        maxRetries: 10,
                        skipPreflight: true
                    },
                    updateCallback
                );

                logger.info(`Metadata transaction confirmed: ${signature}`);
                if (updateCallback) {
                    await updateCallback(`✅ Metadata created successfully!`);
                }
                return;

            } catch (error) {
                lastError = error instanceof Error ? error : new Error(String(error));
                logger.error(`Metadata creation attempt ${attempt} failed:`, {
                    error: lastError.message,
                    stack: lastError.stack
                });

                if (attempt < maxRetries) {
                    const delay = Math.min(attempt * 6000, 30000);
                    logger.info(`Waiting ${delay}ms before retry ${attempt + 1}/${maxRetries}...`);
                    await this.sleep(delay);
                }
            }
        }

        throw lastError || new Error('Failed to create metadata after maximum retries');
    }

    private async mintInitialSupply(
        tokenAddress: string,
        keypairPath: string,
        wallet: Keypair,
        supply: number,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<void> {
        if (updateCallback) {
            await updateCallback("💰 Minting initial supply...");
        }

        const createAccountProcess = await this.solanaUtils.executeCommand('spl-token', [
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

        const mintProcess = await this.solanaUtils.executeCommand('spl-token', [
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
    }

    private async revokeMintAuthority(
        tokenAddress: string,
        keypairPath: string,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<void> {
        if (updateCallback) {
            await updateCallback("🔒 Revoking mint authority...");
        }

        const revokeMintProcess = await this.solanaUtils.executeCommand('spl-token', [
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

    private async cleanupKeypairFile(keypairPath: string): Promise<void> {
        try {
            await fs.promises.unlink(keypairPath);
            logger.info(`Cleaned up keypair file: ${keypairPath}`);
        } catch (error) {
            logger.error('Error cleaning up keypair file:', error);
        }
    }
    private sleep(ms: number): void {
        const end = Date.now() + ms; while (Date.now() < end) {             // Busy-waiting         
        }
    }
}