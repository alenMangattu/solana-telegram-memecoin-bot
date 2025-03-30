import { OPEN_BOOK_PROGRAM, WSOLMint, DEVNET_PROGRAM_ID } from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey, Keypair, ComputeBudgetProgram } from '@solana/web3.js'
import bs58 from 'bs58'
import logger from '../logger'
import { TOKEN_PROGRAM_ID } from '@solana/spl-token'

export const createMarket = async (
    tokenMint: string,
    walletPrivateKey: string,
    lotSize: number = 1,
    tickSize: number = 0.01,
    priorityFee: number = 46591500 // Default priority fee
) => {
    try {
        const raydium = await initSdk()
        logger.info('Creating market with params:', { tokenMint, lotSize, tickSize })

        // Decode private key
        const decodedPrivateKey = bs58.decode(walletPrivateKey)
        const walletKeypair = Keypair.fromSecretKey(decodedPrivateKey)

        // Validate token mint
        const tokenMintInfo = await raydium.token.getTokenInfo(tokenMint)
        if (tokenMintInfo.programId !== TOKEN_PROGRAM_ID.toBase58()) {
            throw new Error('Create market does not support token 2022 program')
        }

        // Create compute budget instructions
        const computeBudgetIx = ComputeBudgetProgram.setComputeUnitPrice({
            microLamports: priorityFee
        });
        const computeBudgetLimitIx = ComputeBudgetProgram.setComputeUnitLimit({
            units: 800000
        });

        // Create the market
        const { execute, extInfo, transactions } = await raydium.marketV2.create({
            baseInfo: {
                mint: new PublicKey(tokenMint),
                decimals: tokenMintInfo.decimals,
            },
            quoteInfo: {
                mint: WSOLMint,
                decimals: 9,
            },
            lotSize,
            tickSize,
            dexProgramId: raydium.cluster === 'mainnet' ? OPEN_BOOK_PROGRAM : DEVNET_PROGRAM_ID.OPENBOOK_MARKET,
            txVersion,
            computeBudgetConfig: {
                units: 800000,
                microLamports: priorityFee,
            },
            // Optional queue spaces for better performance
            requestQueueSpace: 5120 + 12,
            eventQueueSpace: 262144 + 12,
            orderbookQueueSpace: 65536 + 12,
        })

        logger.info('Market creation info:', {
            totalTransactions: transactions.length,
            marketInfo: Object.keys(extInfo.address).reduce(
                (acc, cur) => ({
                    ...acc,
                    [cur]: extInfo.address[cur as keyof typeof extInfo.address].toBase58(),
                }),
                {}
            )
        })

        // Execute market creation with proper error handling
        const txIds = await execute({
            sequentially: true
        });
        logger.info('Market created successfully:', { txIds });
        return extInfo.address.marketId.toBase58()
    } catch (error) {
        logger.error('Error creating market:', error)
        throw error
    }
}

/** uncomment code below to execute */
// createMarket()