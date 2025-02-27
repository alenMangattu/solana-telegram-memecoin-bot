import { PublicKey } from "@solana/web3.js";
import { initSdk, txVersion } from '../../config'
import Decimal from 'decimal.js'
import BN from 'bn.js'

export const removeLiquidity = async (
    poolId: string,
    removePercentage: number, // 0-100 percentage of liquidity to remove
    walletPrivateKey: string
) => {
    const raydium = await initSdk()

    try {
        const poolInfo = await raydium.liquidity.getPoolInfoFromRpc({ poolId })
        
        // Get user's LP token balance
        const lpTokenAccount = await raydium.connection.getTokenAccountsByOwner(
            new PublicKey(walletPrivateKey),
            { mint: new PublicKey(poolInfo.poolInfo.lpMint.toString()) }
        );

        if (lpTokenAccount.value.length === 0) {
            throw new Error("No LP token account found for this wallet");
        }

        const tokenBalance = await raydium.connection.getTokenAccountBalance(
            lpTokenAccount.value[0].pubkey
        );

        // Calculate amount to remove based on percentage
        const withdrawLpAmount = new BN(
            new Decimal(tokenBalance.value.uiAmount || 0)
                .mul(removePercentage)
                .div(100)
                .mul(new Decimal(10).pow(tokenBalance.value.decimals))
                .toFixed(0)
        );

        // Calculate expected output amounts based on pool ratios
        const [baseRatio, quoteRatio] = [
            new Decimal(poolInfo.poolInfo.mintAmountA).div(poolInfo.poolInfo.lpAmount || 1),
            new Decimal(poolInfo.poolInfo.mintAmountB).div(poolInfo.poolInfo.lpAmount || 1),
        ]

        const withdrawAmountDe = new Decimal(withdrawLpAmount.toString())
            .div(10 ** poolInfo.poolInfo.lpMint.decimals);

        const [withdrawAmountA, withdrawAmountB] = [
            withdrawAmountDe.mul(baseRatio).mul(10 ** poolInfo.poolInfo.mintA.decimals),
            withdrawAmountDe.mul(quoteRatio).mul(10 ** poolInfo.poolInfo.mintB.decimals),
        ]

        const lpSlippage = 0.01 // 1% slippage

        const { execute } = await raydium.liquidity.removeLiquidity({
            poolInfo: poolInfo.poolInfo,
            poolKeys: poolInfo.poolKeys,
            lpAmount: withdrawLpAmount,
            baseAmountMin: new BN(withdrawAmountA.mul(1 - lpSlippage).toFixed(0)),
            quoteAmountMin: new BN(withdrawAmountB.mul(1 - lpSlippage).toFixed(0)),
            txVersion,
        })

        const { txId } = await execute({ sendAndConfirm: true })
        return txId
    } catch (error) {
        console.error('Error removing liquidity:', error);
        throw error;
    }
} 