import {
    LIQUIDITY_STATE_LAYOUT_V4,
    type LiquidityPoolKeys,
    MAINNET_PROGRAM_ID,
    MARKET_STATE_LAYOUT_V3
} from "@raydium-io/raydium-sdk";
import {
    Connection,
    PublicKey
} from "@solana/web3.js";

const getPoolKeys = async (ammId: string, connection: Connection) => {
    const ammAccount = await connection.getAccountInfo(new PublicKey(ammId));
    if (ammAccount) {
        const poolState = LIQUIDITY_STATE_LAYOUT_V4.decode(ammAccount.data);
        const marketAccount = await connection.getAccountInfo(poolState.marketId);
        if (marketAccount) {
            const marketState = MARKET_STATE_LAYOUT_V3.decode(marketAccount.data);
            const marketAuthority = PublicKey.createProgramAddressSync(
                [
                    marketState.ownAddress.toBuffer(),
                    marketState.vaultSignerNonce.toArrayLike(Buffer, "le", 8),
                ],
                MAINNET_PROGRAM_ID.OPENBOOK_MARKET,
            );
            return {
                id: new PublicKey(ammId),
                programId: MAINNET_PROGRAM_ID.AmmV4,
                status: poolState.status,
                baseDecimals: poolState.baseDecimal.toNumber(),
                quoteDecimals: poolState.quoteDecimal.toNumber(),
                lpDecimals: 9,
                baseMint: poolState.baseMint,
                quoteMint: poolState.quoteMint,
                version: 4,
                authority: new PublicKey(
                    "5Q544fKrFoe6tsEbD7S8EmxGTJYAKtTVhAW5Q5pge4j1",
                ),
                openOrders: poolState.openOrders,
                baseVault: poolState.baseVault,
                quoteVault: poolState.quoteVault,
                marketProgramId: MAINNET_PROGRAM_ID.OPENBOOK_MARKET,
                marketId: marketState.ownAddress,
                marketBids: marketState.bids,
                marketAsks: marketState.asks,
                marketEventQueue: marketState.eventQueue,
                marketBaseVault: marketState.baseVault,
                marketQuoteVault: marketState.quoteVault,
                marketAuthority: marketAuthority,
                targetOrders: poolState.targetOrders,
                lpMint: poolState.lpMint,
            } as unknown as LiquidityPoolKeys;
        }
    }
};