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

// CPMM Program ID for Standard AMM
const CPMM_PROGRAM_ID = new PublicKey('CPMDWBwJDtYax9qW7AyRuVC19Cc4L4Vcy4n2BHAbHkCW')

// Helper function to validate and convert to PublicKey
const toPublicKey = (address: string, label: string): PublicKey => {
    try {
        if (address === 'SOL' || address === WSOLMint.toBase58()) {
            return WSOLMint;
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
    fromToken: string,    // SOL token address
    toToken: string,      // Your token address
    fromAmount: number,   // SOL amount
    toAmount: number,     // Your token amount
    walletPrivateKey: string,
    decimals: number
) => {
    const raydium = await initSdk()

    try {
        logger.info('Creating AMM pool with params:', {
            fromToken,
            toToken,
            fromAmount,
            toAmount,
            decimals
        });

        // Properly decode the private key
        const decodedPrivateKey = bs58.decode(walletPrivateKey)
        const walletKeypair = Keypair.fromSecretKey(decodedPrivateKey)

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
            lotSize: 1,
            tickSize: 0.01,
            dexProgramId: OPEN_BOOK_PROGRAM,
            txVersion,
            computeBudgetConfig: {
                units: 600000,
                microLamports: 100000,
            }
        });

        // Execute market creation and wait for confirmation
        logger.info('Executing market creation...');
        const { txIds } = await executeMarket({ 
            sequentially: true
        });
        
        logger.info('Market creation transaction IDs:', txIds);
        
        // Confirm each transaction with WebSocket
        for (const txId of txIds) {
            try {
                logger.info(`Confirming market creation tx: ${txId}`);
                await new Promise((resolve, reject) => {
                    const timeout = setTimeout(() => {
                        reject(new Error(`Transaction ${txId} timed out after 120 seconds`));
                    }, 120000);

                    const subscription = raydium.connection.onSignature(
                        txId,
                        (result, context) => {
                            clearTimeout(timeout);
                            raydium.connection.removeSignatureListener(subscription);
                            
                            if (result.err) {
                                logger.error(`Transaction ${txId} failed:`, result.err);
                                reject(new Error(`Transaction failed: ${JSON.stringify(result.err)}`));
                            } else {
                                logger.info(`Market creation tx ${txId} confirmed with result:`, result);
                                resolve(result);
                            }
                        },
                        'confirmed'
                    );
                });
            } catch (error) {
                logger.error(`Failed to confirm market creation tx ${txId}:`, error);
                throw error;
            }
        }

        logger.info('Market creation completed successfully');
        logger.info('Market Info:', {
            marketId: marketInfo.address.marketId.toBase58(),
            baseVault: marketInfo.address.baseVault.toBase58(),
            quoteVault: marketInfo.address.quoteVault.toBase58()
        });

        // Convert amounts to BN with proper decimal places
        const baseAmount = new BN(Math.floor(toAmount * Math.pow(10, decimals)))
        const quoteAmount = new BN(Math.floor(fromAmount * Math.pow(10, 9))) // SOL has 9 decimals

        logger.info('Creating pool with amounts:', {
            baseAmount: baseAmount.toString(),
            quoteAmount: quoteAmount.toString()
        });

        // Now create the pool with the new market
        logger.info('Creating pool...');
        const { execute, extInfo } = await raydium.cpmm.createPool({
            programId: CPMM_PROGRAM_ID,
            poolFeeAccount: CPMM_PROGRAM_ID,
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
            startTime: new BN(0),
            feeConfig: (await raydium.api.getCpmmConfigs())[0],
            associatedOnly: false,
            ownerInfo: {
                feePayer: walletKeypair.publicKey,
                useSOLBalance: true
            },
            txVersion,
            computeBudgetConfig: {
                units: 600000,
                microLamports: 100000,
            }
        });

        logger.info('Executing pool creation...');
        const { txId } = await execute({ sendAndConfirm: true });
        logger.info('Pool creation transaction ID:', txId);

        await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                reject(new Error(`Pool creation transaction ${txId} timed out after 120 seconds`));
            }, 120000);

            const subscription = raydium.connection.onSignature(
                txId,
                (result, context) => {
                    clearTimeout(timeout);
                    raydium.connection.removeSignatureListener(subscription);
                    
                    if (result.err) {
                        logger.error(`Pool creation tx ${txId} failed:`, result.err);
                        reject(new Error(`Transaction failed: ${JSON.stringify(result.err)}`));
                    } else {
                        logger.info(`Pool creation tx ${txId} confirmed with result:`, result);
                        resolve(result);
                    }
                },
                'confirmed'
            );
        });

        logger.info("Pool created successfully with ID:", extInfo.address.poolId.toBase58());
        return extInfo.address.poolId.toBase58();
    } catch (error) {
        logger.error("Error creating AMM pool:", error)
        throw error
    }
}

/** uncomment code below to execute */
// createAmmPool()