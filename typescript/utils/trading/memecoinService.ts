// memecoinService.ts - Updated version

import { Connection, Keypair, PublicKey, Transaction } from '@solana/web3.js';
import {
    createCreateMetadataAccountV3Instruction,
    PROGRAM_ID as METADATA_PROGRAM_ID,
    type DataV2
} from '@metaplex-foundation/mpl-token-metadata';
import { TOKEN_PROGRAM_ID } from '@solana/spl-token';
import bs58 from 'bs58';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { v4 as uuidv4 } from 'uuid';
import logger from '../logger';
import { HeliusRpcInterface } from '../heliusRpcInterface';
import { SolanaUtils } from '../solanaUtils';
import axios from 'axios';
import { KeypairUtils } from '../keypairUtils';
import {
    MARKET_STATE_LAYOUT_V3,
    Liquidity,
    type LiquidityPoolKeys,
    type LiquidityPoolKeysV4,
    LIQUIDITY_STATE_LAYOUT_V4
} from '@raydium-io/raydium-sdk';
import {
    createMint,
    getOrCreateAssociatedTokenAccount,
    mintTo,
    setAuthority,
    AuthorityType,
} from '@solana/spl-token';

// Raydium AMM v4 program ID
const LIQUIDITY_PROGRAM_ID_V4 = new PublicKey('675kPX9MHTjS2zt1qfr1NYHuzeLXfQM9H24wFSUt1Mp8');

interface ParsedTokenData {
    info: {
        mintAuthority: string;
        freezeAuthority: string;
        decimals: number;
        supply: string;
        isInitialized: boolean;
    }
}

interface ParsedMetadataData {
    info: {
        data: {
            name: string;
            symbol: string;
            uri: string;
            sellerFeeBasisPoints: number;
        }
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

    getConnection(): Connection {
        return this.connection;
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
            await this.solanaUtils.checkRpcConnection();

            const wallet = Keypair.fromSecretKey(bs58.decode(walletPrivateKey));
            
            // Check wallet balance
            const balance = await this.connection.getBalance(wallet.publicKey);
            const minimumBalance = 10000000; // 0.01 SOL
            if (balance < minimumBalance) {
                throw new Error(`Insufficient SOL balance. Minimum 0.01 SOL required, found ${balance / 1e9} SOL`);
            }

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

            // Method 1: Use web3.js directly to create the token
            try {
                return await this.createMemecoinWithWeb3(
                    wallet,
                    name,
                    symbol,
                    decimals,
                    supply,
                    metadataUri,
                    revokeMintAuthority,
                    revokeFreezeAuthority,
                    updateCallback
                );
            } catch (error) {
                logger.error('Web3 token creation failed, falling back to CLI method:', error);
                await updateCallback(`⚠️ First method failed, trying alternative approach...`);
                
                // Method 2: Fall back to CLI method
                const keypairPath = await KeypairUtils.saveWalletKeypair(walletPrivateKey);
                logger.info(`Keypair saved at: ${keypairPath}`);

                try {
                    const tokenAddress = await this.createTokenWithCli(
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
                    
                    await KeypairUtils.cleanupKeypairFile(keypairPath);
                    return tokenAddress;
                } catch (error) {
                    await KeypairUtils.cleanupKeypairFile(keypairPath);
                    throw error;
                }
            }
        } catch (error) {
            logger.error('Error creating memecoin:', error);
            throw new Error(`Failed to create memecoin: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    private async createMemecoinWithWeb3(
        wallet: Keypair,
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
            // Create mint account
            if (updateCallback) {
                await updateCallback(`Creating mint account...`);
            }
            
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
            if (updateCallback) {
                await updateCallback(`✅ Token created with address: ${mint.toBase58()}`);
            }

            // Create metadata
            if (updateCallback) {
                await updateCallback(`🎨 Creating metadata...`);
            }
            
            await this.createMetadataWithTransaction(
                mint,
                wallet,
                name,
                symbol,
                metadataUri,
                updateCallback
            );

            // Create associated token account and mint initial supply
            if (updateCallback) {
                await updateCallback(`💰 Minting initial supply...`);
            }
            
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
                if (updateCallback) {
                    await updateCallback(`🔒 Revoking mint authority...`);
                }
                
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

            if (updateCallback) {
                await updateCallback(`✅ Memecoin created successfully!`);
            }
            
            return mint.toBase58();
        } catch (error) {
            logger.error('Error creating memecoin with web3:', error);
            throw error;
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

            // Try alternative methods for metadata creation
            try {
                // First attempt with on-chain metadata creation via transaction
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

    private async createMetadata(
        mint: PublicKey,
        wallet: Keypair,
        name: string,
        symbol: string,
        uri: string,
        updateCallback?: (message: string) => Promise<void>
    ): Promise<void> {
        try {
            // Check wallet balance first
            const balance = await this.connection.getBalance(wallet.publicKey);
            if (balance < 10000000) { // 0.01 SOL minimum
                throw new Error(`Insufficient SOL balance. Minimum 0.01 SOL required, found ${balance / 1e9} SOL`);
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

            const tx = new Transaction().add(instruction);
            
            // Get recent blockhash
            const { blockhash } = await this.connection.getLatestBlockhash('confirmed');
            tx.recentBlockhash = blockhash;
            tx.feePayer = wallet.publicKey;

            if (updateCallback) {
                await updateCallback('Sending metadata transaction...');
            }

            // Send and confirm transaction
            const signature = await this.connection.sendTransaction(
                tx,
                [wallet],
                {
                    preflightCommitment: 'confirmed',
                    skipPreflight: false // Enable preflight to catch errors early
                }
            );

            if (updateCallback) {
                await updateCallback(`Confirming metadata transaction: ${signature}`);
            }

            // Wait for confirmation with explicit timeout
            const confirmation = await this.connection.confirmTransaction({
                signature,
                blockhash,
                lastValidBlockHeight: (await this.connection.getLatestBlockhash()).lastValidBlockHeight
            }, 'confirmed');

            if (confirmation.value.err) {
                throw new Error(`Transaction failed: ${JSON.stringify(confirmation.value.err)}`);
            }

            logger.info(`Metadata created successfully: ${signature}`);
        } catch (error) {
            logger.error('Failed to create metadata:', error);
            const errorMessage = error instanceof Error ? error.message : String(error);
            throw new Error(`Metadata creation failed: ${errorMessage}`);
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

                // Send and confirm with the solanaUtils method
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

    private sleep(ms: number): Promise<void> {
        return new Promise(resolve => setTimeout(resolve, ms));
    }

    async getTokenInfo(tokenAddress: string) {
        try {
            const mint = new PublicKey(tokenAddress);
            logger.info(`Fetching token info for: ${tokenAddress}`);

            // Get basic token info
            const tokenInfo = await this.connection.getParsedAccountInfo(mint);
            if (!tokenInfo.value) {
                throw new Error('Token not found');
            }

            // Get metadata
            const [metadataPDA] = PublicKey.findProgramAddressSync(
                [
                    Buffer.from('metadata'),
                    METADATA_PROGRAM_ID.toBuffer(),
                    mint.toBuffer(),
                ],
                METADATA_PROGRAM_ID
            );
            const metadataInfo = await this.connection.getParsedAccountInfo(metadataPDA);

            // Get Raydium pool info (if exists)
            let raydiumInfo = null;
            try {
                const WSOL_MINT = new PublicKey('So11111111111111111111111111111111111111112');
                const poolAccounts = await this.fetchMarketAccounts(mint, WSOL_MINT);
                if (poolAccounts.length > 0) {
                    const accountId = poolAccounts[0].id;
                    const accountData = poolAccounts[0].poolstate;
                    const market = await this.getMinimalMarketV3(accountData.marketId);
                    
                    const poolKeys = this.createPoolKeys(accountId, accountData, market);
                    const poolInfo = await Liquidity.fetchInfo({ 
                        connection: this.connection, 
                        poolKeys: poolKeys 
                    });

                    raydiumInfo = {
                        poolAddress: accountId.toString(),
                        liquidity: poolInfo.baseReserve.toString(),
                        priceInSol: this.calculateTokenPrice(poolInfo)
                    };
                }
            } catch (error) {
                logger.warn('Failed to fetch Raydium info:', error);
            }

            // Get holder count using Helius if available
            let holderCount = null;
            if (this.heliusRpc) {
                try {
                    const response = await this.heliusRpc.getAssetsByGroup({
                        groupKey: 'collection',
                        groupValue: tokenAddress,
                    });
                    holderCount = response.total;
                } catch (error) {
                    logger.warn('Failed to fetch holder count:', error);
                }
            }

            const tokenData = tokenInfo.value.data as unknown as ParsedTokenData;
            const metadataData = metadataInfo.value?.data as unknown as ParsedMetadataData;

            return {
                address: tokenAddress,
                mintAuthority: tokenData.info.mintAuthority,
                freezeAuthority: tokenData.info.freezeAuthority,
                decimals: tokenData.info.decimals,
                supply: tokenData.info.supply,
                metadata: metadataInfo.value ? {
                    name: metadataData.info.data.name,
                    symbol: metadataData.info.data.symbol,
                    uri: metadataData.info.data.uri,
                    sellerFeeBasisPoints: metadataData.info.data.sellerFeeBasisPoints,
                } : null,
                raydiumInfo,
                holderCount,
                isInitialized: tokenData.info.isInitialized,
                lastUpdated: new Date().toISOString()
            };
        } catch (error) {
            logger.error('Error fetching token info:', error);
            throw error;
        }
    }

    private async fetchMarketAccounts(base: PublicKey, quote: PublicKey) {
        try {
            const accounts = await this.connection.getProgramAccounts(
                LIQUIDITY_PROGRAM_ID_V4,
                {
                    commitment: 'confirmed',
                    filters: [
                        { dataSize: LIQUIDITY_STATE_LAYOUT_V4.span },
                        {
                            memcmp: {
                                offset: LIQUIDITY_STATE_LAYOUT_V4.offsetOf('baseMint'),
                                bytes: base.toBase58()
                            }
                        },
                        {
                            memcmp: {
                                offset: LIQUIDITY_STATE_LAYOUT_V4.offsetOf('quoteMint'),
                                bytes: quote.toBase58()
                            }
                        }
                    ]
                }
            );

            return accounts.map(({ pubkey, account }) => ({
                id: pubkey,
                poolstate: LIQUIDITY_STATE_LAYOUT_V4.decode(account.data)
            }));
        } catch (error) {
            logger.error('Error fetching market accounts:', error);
            return [];
        }
    }

    private async getMinimalMarketV3(marketId: PublicKey) {
        const marketInfo = await this.connection.getAccountInfo(marketId);
        if (!marketInfo) {
            throw new Error('Market info not found');
        }
        return MARKET_STATE_LAYOUT_V3.decode(marketInfo.data);
    }

    private createPoolKeys(
        ammId: PublicKey,
        poolState: any,
        marketInfo: any
    ): LiquidityPoolKeysV4 {
        return {
            id: ammId,
            baseMint: poolState.baseMint,
            quoteMint: poolState.quoteMint,
            lpMint: poolState.lpMint,
            baseDecimals: poolState.baseDecimal.toNumber(),
            quoteDecimals: poolState.quoteDecimal.toNumber(),
            lpDecimals: poolState.lpDecimal.toNumber(),
            version: 4,
            programId: LIQUIDITY_PROGRAM_ID_V4,
            authority: poolState.authority,
            openOrders: poolState.openOrders,
            targetOrders: poolState.targetOrders,
            baseVault: poolState.baseVault,
            quoteVault: poolState.quoteVault,
            withdrawQueue: poolState.withdrawQueue,
            lpVault: poolState.lpVault,
            marketVersion: 3,
            marketId: marketInfo.ownAddress,
            marketProgramId: marketInfo.programId,
            marketAuthority: marketInfo.marketAuthority,
            marketBaseVault: marketInfo.baseVault,
            marketQuoteVault: marketInfo.quoteVault,
            marketBids: marketInfo.bids,
            marketAsks: marketInfo.asks,
            marketEventQueue: marketInfo.eventQueue,
            lookupTableAccount: new PublicKey('11111111111111111111111111111111')
        };
    }

    private calculateTokenPrice(poolInfo: any): number {
        if (!poolInfo.baseReserve || !poolInfo.quoteReserve) {
            return 0;
        }
        return poolInfo.quoteReserve.toNumber() / poolInfo.baseReserve.toNumber();
    }
}