import {
    TokenAmount,
    toToken,
    Percent,
    WSOLMint,
} from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey } from '@solana/web3.js'
import Decimal from 'decimal.js'

async function getPoolByTokenMint(raydium: any, tokenMint: string): Promise<string | null> {
    try {
        const allPools = await raydium.liquidity.fetchAllPools()
        const pool = allPools.find((p: any) => 
            p.baseMint.toString() === tokenMint && p.quoteMint.toString() === WSOLMint.toString() ||
            p.quoteMint.toString() === tokenMint && p.baseMint.toString() === WSOLMint.toString()
        )
        return pool ? pool.id.toString() : null
    } catch (error) {
        console.error('Error fetching pools:', error)
        return null
    }
}

export const addLiquidity = async (
    tokenMint: string,
    tokenAmount: number,
    solAmount: number,
    walletPrivateKey: string
) => {
    const raydium = await initSdk()

    // Find pool by token mint
    const poolId = await getPoolByTokenMint(raydium, tokenMint)
    if (!poolId) {
        throw new Error("Pool not found for token")
    }

    // Get pool info using pool ID
    const poolInfo = await raydium.liquidity.getPoolInfoFromRpc({ 
        poolId: poolId // Use poolId as a string directly
    })

    if (!poolInfo) {
        throw new Error("Pool info not found")
    }

    // Calculate minimum amounts with 1% slippage
    const slippage = new Percent(1, 100)
    const tokenAmountWithSlippage = new Decimal(tokenAmount)
        .mul(0.99)
        .mul(10 ** poolInfo.poolInfo.mintA.decimals)
        .toFixed(0)

    const solAmountWithSlippage = new Decimal(solAmount)
        .mul(0.99)
        .mul(10 ** poolInfo.poolInfo.mintB.decimals)
        .toFixed(0)

    const { execute } = await raydium.liquidity.addLiquidity({
        poolInfo: poolInfo.poolInfo,
        poolKeys: poolInfo.poolKeys,
        amountInA: new TokenAmount(
            toToken(poolInfo.poolInfo.mintA),
            new Decimal(tokenAmount).mul(10 ** poolInfo.poolInfo.mintA.decimals).toFixed(0)
        ),
        amountInB: new TokenAmount(
            toToken(poolInfo.poolInfo.mintB),
            new Decimal(solAmount).mul(10 ** poolInfo.poolInfo.mintB.decimals).toFixed(0)
        ),
        otherAmountMin: new TokenAmount(
            toToken(poolInfo.poolInfo.mintB),
            solAmountWithSlippage
        ),
        fixedSide: 'a',
        txVersion,
    })

    const { txId } = await execute({ sendAndConfirm: true })
    return txId
}

/** uncomment code below to execute */
// addLiquidity()