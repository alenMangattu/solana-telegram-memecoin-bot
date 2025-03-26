import { pgTable, serial, text, timestamp, boolean, uuid, integer, numeric } from 'drizzle-orm/pg-core';

export const wallets = pgTable('wallets', {
    walletId: text('wallet_id').primaryKey(),
    userId: serial('user_id').notNull(),
    walletAddress: text('wallet_address').notNull(),
    createdAt: timestamp('created_at').defaultNow().notNull(),
    updatedAt: timestamp('updated_at').defaultNow().notNull()
});

export const encryptionKeyMetadata = pgTable('encryption_key_metadata', {
    id: serial('id').primaryKey(),
    walletId: text('wallet_id').notNull().references(() => wallets.walletId),
    version: serial('version').notNull(),
    active: boolean('active').notNull().default(true),
    createdAt: timestamp('created_at').defaultNow().notNull(),
    updatedAt: timestamp('updated_at').defaultNow().notNull(),
    expiresAt: timestamp('expires_at')
});

export const limitOrders = pgTable('limit_orders', {
    orderId: text('order_id').primaryKey(),
    userId: serial('user_id').notNull(),
    walletId: text('wallet_id').notNull().references(() => wallets.walletId),
    tokenMint: text('token_mint').notNull(),
    action: text('action', { enum: ['BUY', 'SELL'] }).notNull(),
    solAmount: numeric('sol_amount').notNull(),
    limitPrice: numeric('limit_price').notNull(),
    slippage: numeric('slippage').notNull(),
    status: text('status').notNull(), // 'active', 'pending', 'executed', 'failed'
    transactionSignature: text('transaction_signature'),
    executedAt: timestamp('executed_at'),
    createdAt: timestamp('created_at').defaultNow().notNull(),
    updatedAt: timestamp('updated_at').defaultNow().notNull()
});

export const copyTrading = pgTable('copy_trading', {
    id: serial('id').primaryKey(),
    userId: serial('user_id').notNull(),
    sourceWallet: text('source_wallet').notNull().references(() => wallets.walletId),
    targetWallet: text('target_wallet').notNull().references(() => wallets.walletId),
    enabled: boolean('enabled').notNull().default(true),
    amountMultiplier: numeric('amount_multiplier').notNull().default('1'),
    createdAt: timestamp('created_at').defaultNow().notNull(),
    updatedAt: timestamp('updated_at').defaultNow().notNull()
}); 