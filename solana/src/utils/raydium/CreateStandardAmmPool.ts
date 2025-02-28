import {
    WSOLMint,
    FEE_DESTINATION_ID,
    OPEN_BOOK_PROGRAM,
} from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey, Keypair, Connection } from '@solana/web3.js'
import BN from 'bn.js'
import bs58 from 'bs58'
import logger from '../logger'
import { sendUnconfirmedTransaction, confirmLater, createTransaction, sendAndConfirmTransaction } from './utils'
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
    decimals: number
) => {
    const raydium = await initSdk()

    try {
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

        logger.info('Creating market...');
        const { execute: executeMarket, extInfo: marketInfo } = await raydium.marketV2.create({
            baseInfo: {
                mint: toTokenPubkey,
                decimals: decimals,
            },
            quoteInfo: {
                mint: fromTokenPubkey,
                decimals: 9, // SOL has 9 decimals
            },
            lotSize: 1, // Adjusted from example
            tickSize: 0.01,
            dexProgramId: OPEN_BOOK_PROGRAM,
            txVersion,
            computeBudgetConfig: {
                units: 800000, // Increased compute units
                microLamports: 100000,
            }
        });

        // Execute market creation with fire-and-forget approach
        logger.info('Executing market creation...');
        const { txIds } = await executeMarket({ 
            sequentially: false,
            skipPreflight: true  // Skip preflight checks for faster processing,
        });
        
        logger.info('Market creation transaction IDs:', txIds);
        
        // Confirm later with retries
        for (const txId of txIds) {
            await confirmLater(raydium.connection, txId);
        }

        logger.info('Market Info:', marketInfo);

        // Convert amounts to BN with proper decimal places
        const baseAmount = new BN(Math.floor(toAmount * Math.pow(10, decimals)))
        const quoteAmount = new BN(Math.floor(fromAmount * Math.pow(10, 9)))

        // Create pool with updated configuration
        const { execute, extInfo } = await raydium.cpmm.createPool({
            programId: CPMM_PROGRAM_ID,
            poolFeeAccount: FEE_DESTINATION_ID,
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
                microLamports: 100000,
            }
        });

        logger.info('Executing pool creation...');
        const { txId: poolTxId } = await execute({ 
            sendAndConfirm: false, // Skip confirmation
            skipPreflight: true    // Optional: Bypass preflight checks
        });

        // await confirmLater(raydium.connection, poolTxId); // Confirm later with retries
        logger.info("Pool created successfully with ID:", extInfo.address.poolId.toBase58());
        return extInfo.address.poolId.toBase58();

    } catch (error) {
        logger.error("Error creating AMM pool:", error);
        throw error;
    }
}