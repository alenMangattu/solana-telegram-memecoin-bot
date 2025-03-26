export interface LimitOrder {
    orderId: string;
    createdAt: Date;
    executedAt?: Date;
    limitPrice: number;
    slippage: number;
    solAmount: number;
    status: 'pending' | 'active' | 'executed' | 'failed' | 'cancelled';
    tokenMint: string;
    transactionSignature?: string;
    updatedAt: Date;
    userId: number;
    walletId: string;
    action: 'BUY' | 'SELL';
}

export interface CopyTrading {
    id: number;
    userId: number;
    sourceWallet: string;
    targetWallet: string;
    enabled: boolean;
    amountMultiplier: number;
    createdAt: Date;
    updatedAt: Date;
}

export interface TokenPrice {
    tokenMint: string;
    price: number;
    lastUpdated: Date;
} 