import {
    TokenAmount,
    toToken,
    Percent,
    WSOLMint,
    type ApiV3PoolInfoStandardItem,
    type AmmV4Keys,
    type AmmV5Keys,
} from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey } from '@solana/web3.js'
import Decimal from 'decimal.js'
import logger from '../logger'
import { isValidAmm } from './utils'

async function getPoolByTokenMint(raydium: any, tokenMint: string): Promise<{ poolInfo: ApiV3PoolInfoStandardItem; poolKeys?: AmmV4Keys | AmmV5Keys }> {
    try {
        if (raydium.cluster === 'mainnet') {
            const allPools = await raydium.liquidity.fetchAllPools()
            const pool = allPools.find((p: any) => 
                p.baseMint.toString() === tokenMint && p.quoteMint.toString() === WSOLMint.toString() ||
                p.quoteMint.toString() === tokenMint && p.baseMint.toString() === WSOLMint.toString()
            )
            
            if (!pool) {
                throw new Error("Pool not found for token")
            }

            const data = await raydium.api.fetchPoolById({ ids: pool.id.toString() })
            return { poolInfo: data[0] as ApiV3PoolInfoStandardItem }
        } else {
            const allPools = await raydium.liquidity.fetchAllPools()
            const pool = allPools.find((p: any) => 
                p.baseMint.toString() === tokenMint && p.quoteMint.toString() === WSOLMint.toString() ||
                p.quoteMint.toString() === tokenMint && p.baseMint.toString() === WSOLMint.toString()
            )
            
            if (!pool) {
                throw new Error("Pool not found for token")
            }

            const data = await raydium.liquidity.getPoolInfoFromRpc({ poolId: pool.id.toString() })
            return { 
                poolInfo: data.poolInfo,
                poolKeys: data.poolKeys
            }
        }
    } catch (error) {
        logger.error('Error fetching pools:', error)
        throw error
    }
}

export const addLiquidity = async (
    tokenMint: string,
    tokenAmount: number,
    solAmount: number,
    walletPrivateKey: string,
    priorityFee: number = 46591500 // Default priority fee
) => {
    try {
        const raydium = await initSdk()
        logger.info('Adding liquidity with params:', { tokenMint, tokenAmount, solAmount })

        // Find pool by token mint
        const { poolInfo, poolKeys } = await getPoolByTokenMint(raydium, tokenMint)
        
        if (!isValidAmm(poolInfo.programId)) {
            throw new Error('Target pool is not a valid AMM pool')
        }

        // Calculate amounts with proper decimals
        const tokenAmountWithDecimals = new Decimal(tokenAmount)
            .mul(10 ** poolInfo.mintA.decimals)
            .toFixed(0)

        const solAmountWithDecimals = new Decimal(solAmount)
            .mul(10 ** poolInfo.mintB.decimals)
            .toFixed(0)

        // Calculate minimum amounts with 1% slippage
        const slippage = new Percent(1, 100)
        const tokenAmountWithSlippage = new Decimal(tokenAmount)
            .mul(0.99)
            .mul(10 ** poolInfo.mintA.decimals)
            .toFixed(0)

        const solAmountWithSlippage = new Decimal(solAmount)
            .mul(0.99)
            .mul(10 ** poolInfo.mintB.decimals)
            .toFixed(0)

        logger.info('Calculated amounts:', {
            tokenAmountWithDecimals,
            solAmountWithDecimals,
            tokenAmountWithSlippage,
            solAmountWithSlippage
        })

        const { execute } = await raydium.liquidity.addLiquidity({
            poolInfo,
            poolKeys,
            amountInA: new TokenAmount(
                toToken(poolInfo.mintA),
                tokenAmountWithDecimals
            ),
            amountInB: new TokenAmount(
                toToken(poolInfo.mintB),
                solAmountWithDecimals
            ),
            otherAmountMin: new TokenAmount(
                toToken(poolInfo.mintB),
                solAmountWithSlippage
            ),
            fixedSide: 'a',
            txVersion,
            computeBudgetConfig: {
                units: 600000,
                microLamports: priorityFee,
            }
        })

        const { txId } = await execute({ 
            sendAndConfirm: true
        })

        logger.info('Liquidity added successfully:', { txId })
        return txId
    } catch (error) {
        logger.error('Error adding liquidity:', error)
        throw error
    }
}

/** uncomment code below to execute */
// addLiquidity()