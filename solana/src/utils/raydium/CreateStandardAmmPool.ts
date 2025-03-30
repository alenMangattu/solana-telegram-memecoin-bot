import {
    WSOLMint,
    FEE_DESTINATION_ID,
    OPEN_BOOK_PROGRAM,
    DEVNET_PROGRAM_ID,
} from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey, Keypair, Connection, ComputeBudgetProgram } from '@solana/web3.js'
import BN from 'bn.js'
import bs58 from 'bs58'
import logger from '../logger'
import { TOKEN_PROGRAM_ID } from '@solana/spl-token'

// CPMM Program ID for Standard AMM
const CPMM_PROGRAM_ID = new PublicKey('CPMDWBwJDtYax9qW7AyRuVC19Cc4L4Vcy4n2BHAbHkCW')

// Helper function to validate and convert to PublicKey
const toPublicKey = (address: string, label: string): PublicKey => {
    try {
        // Handle SOL token cases
        if (address === 'SOL' || address === '3' || address === WSOLMint.toBase58()) {
            return WSOLMint;
        }
        
        // Validate address format
        if (!/^[A-HJ-NP-Za-km-z1-9]{32,44}$/.test(address)) {
            throw new Error(`Invalid ${label} address format`);
        }
        
        return new PublicKey(address);
    } catch (error: unknown) {
        logger.error(`Invalid ${label} address:`, address);
        throw new Error(`Invalid ${label} address: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
}

async function confirmTransaction(connection: Connection, signature: string): Promise<void> {
    logger.info(`Confirming transaction ${signature}`);
    
    try {
        const latestBlockhash = await connection.getLatestBlockhash();
        
        const response = await connection.confirmTransaction({
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

export const createAmmPool = async (
    fromToken: string,    // SOL token address or "SOL"
    toToken: string,      // Your token address
    fromAmount: number,   // SOL amount
    toAmount: number,     // Your token amount
    walletPrivateKey: string,
    decimals: number,
    priorityFee: number = 46591500 // Default priority fee
) => {
    try {
        const raydium = await initSdk()

        // Validate input parameters
        if (!fromToken || !toToken) {
            throw new Error('Token addresses are required');
        }

        if (fromAmount <= 0 || toAmount <= 0) {
            throw new Error('Token amounts must be greater than 0');
        }

        logger.info('Creating AMM pool with params:', {
            fromToken,
            toToken,
            fromAmount,
            toAmount,
            decimals
        });

        // Convert token addresses to PublicKey objects with validation
        const fromTokenPubkey = toPublicKey(fromToken, 'fromToken')
        const toTokenPubkey = toPublicKey(toToken, 'toToken')

        // Validate token program IDs
        const fromTokenInfo = await raydium.token.getTokenInfo(fromTokenPubkey.toBase58())
        const toTokenInfo = await raydium.token.getTokenInfo(toTokenPubkey.toBase58())

        if (fromTokenInfo.programId !== TOKEN_PROGRAM_ID.toBase58() || 
            toTokenInfo.programId !== TOKEN_PROGRAM_ID.toBase58()) {
            throw new Error('AMM pools with openbook market only support TOKEN_PROGRAM_ID mints');
        }

        // Validate initial liquidity
        const baseAmount = new BN(Math.floor(toAmount * Math.pow(10, decimals)))
        const quoteAmount = new BN(Math.floor(fromAmount * Math.pow(10, 9)))

        if (baseAmount.mul(quoteAmount).lte(new BN(1).mul(new BN(10 ** decimals)).pow(new BN(2)))) {
            throw new Error('Initial liquidity too low, try adding more baseAmount/quoteAmount');
        }

        logger.info('Creating market...');
        const { execute: executeMarket, extInfo: marketInfo } = await raydium.marketV2.create({
            baseInfo: {
                mint: toTokenPubkey,
                decimals: decimals,
            },
            quoteInfo: {
                mint: fromTokenPubkey,
                decimals: 9,
            },
            lotSize: 1,
            tickSize: 0.01,
            dexProgramId: raydium.cluster === 'mainnet' ? OPEN_BOOK_PROGRAM : DEVNET_PROGRAM_ID.OPENBOOK_MARKET,
            txVersion,
            computeBudgetConfig: {
                units: 800000,
                microLamports: priorityFee,
            }
        });

        // Execute market creation with proper error handling
        logger.info('Executing market creation...');
        const { txIds } = await executeMarket({
            sequentially: true
        });
        
        logger.info('Market creation transaction IDs:', txIds);
        
        // Confirm transactions with retries
        for (const txId of txIds) {
            await confirmTransaction(raydium.connection, txId);
        }

        logger.info('Market Info:', marketInfo);

        // Create pool with updated configuration
        const { execute, extInfo } = await raydium.cpmm.createPool({
            programId: CPMM_PROGRAM_ID,
            poolFeeAccount: raydium.cluster === 'mainnet' ? FEE_DESTINATION_ID : DEVNET_PROGRAM_ID.FEE_DESTINATION_ID,
            mintA: {
                address: toTokenPubkey.toBase58(),
                decimals: decimals,
                programId: CPMM_PROGRAM_ID.toBase58()
            },
            mintB: {
                address: fromTokenPubkey.toBase58(),
                decimals: 9,
                programId: CPMM_PROGRAM_ID.toBase58()
            },
            mintAAmount: baseAmount,
            mintBAmount: quoteAmount,
            startTime: new BN(Math.floor(Date.now() / 1000)),
            feeConfig: (await raydium.api.getCpmmConfigs())[0],
            associatedOnly: false,
            ownerInfo: {
                feePayer: new PublicKey(walletPrivateKey),
                useSOLBalance: true
            },
            txVersion,
            computeBudgetConfig: {
                units: 800000,
                microLamports: priorityFee,
            }
        });

        logger.info('Executing pool creation...');
        const { txId } = await execute({
            sendAndConfirm: true
        });

        logger.info("Pool created successfully with ID:", extInfo.address.poolId.toBase58());
        return extInfo.address.poolId.toBase58();

    } catch (error) {
        logger.error("Error creating AMM pool:", error);
        throw error;
    }
}