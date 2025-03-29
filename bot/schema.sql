-- ================================================
-- DROP EXISTING OBJECTS (if any) to start cleanly
-- ================================================

DROP TABLE IF EXISTS user_state CASCADE;
DROP TABLE IF EXISTS copy_trading CASCADE;
DROP TABLE IF EXISTS encryption_key_metadata CASCADE;
DROP TABLE IF EXISTS wallets CASCADE;
DROP TABLE IF EXISTS users CASCADE;

DROP FUNCTION IF EXISTS validate_current_wallet() CASCADE;

-- ================================================
-- 1. Create the "users" table.
-- ================================================
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    accepted_tos BOOLEAN NOT NULL DEFAULT FALSE,
    current_wallet UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ================================================
-- 2. Create the "wallets" table.
-- ================================================
CREATE TABLE wallets (
    wallet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER NOT NULL,
    wallet_address VARCHAR(255) NOT NULL UNIQUE,
    wallet_label VARCHAR(255), -- Optional label for user convenience
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_wallet FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Add foreign key for current_wallet in users.
ALTER TABLE users
    ADD CONSTRAINT fk_current_wallet FOREIGN KEY (current_wallet) REFERENCES wallets(wallet_id) ON DELETE SET NULL;

-- ================================================
-- 3. Create the "validate_current_wallet" function.
-- ================================================
CREATE OR REPLACE FUNCTION validate_current_wallet()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.current_wallet IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM wallets
        WHERE wallet_id = NEW.current_wallet AND user_id = NEW.id
    )) THEN
        RAISE EXCEPTION 'Invalid current wallet selection';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ================================================
-- 4. Drop any existing trigger and create the trigger.
-- ================================================
DROP TRIGGER IF EXISTS ensure_valid_current_wallet ON users;
CREATE TRIGGER ensure_valid_current_wallet
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION validate_current_wallet();

-- ================================================
-- 5. Create the "copy_trading" table.
-- ================================================
CREATE TABLE copy_trading (
    id SERIAL PRIMARY KEY,
    user_id SERIAL NOT NULL,
    source_wallet TEXT NOT NULL REFERENCES wallets(wallet_id),
    target_wallet TEXT NOT NULL REFERENCES wallets(wallet_id),
    enabled BOOLEAN DEFAULT true NOT NULL,
    amount_multiplier NUMERIC NOT NULL DEFAULT '1',
    has_error BOOLEAN DEFAULT false,
    error_count INTEGER DEFAULT 0,
    last_error_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);

-- 9. Create the "user_state" table.
-- ================================================
CREATE TABLE user_state (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    current_page VARCHAR(50) NOT NULL,
    state_data JSONB,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_state FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ================================================
-- 12. Create the "encryption_key_metadata" table
-- ================================================
CREATE TABLE encryption_key_metadata (
    id SERIAL PRIMARY KEY,
    wallet_id UUID NOT NULL,
    version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_wallet_key FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE CASCADE
);

-- ================================================
-- 12. Create the "user_settings" table.
-- ================================================
DROP TABLE IF EXISTS user_settings CASCADE; -- Ensure the table is dropped if it exists

CREATE TABLE user_settings (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL UNIQUE,
    settings JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_settings FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ================================================
-- 13. Create the "limit_orders" table.
-- ================================================
DROP TABLE IF EXISTS limit_orders CASCADE;

CREATE TABLE limit_orders (
    order_id TEXT PRIMARY KEY,
    user_id SERIAL NOT NULL,
    wallet_id TEXT NOT NULL REFERENCES wallets(wallet_id),
    token_mint TEXT NOT NULL,
    action TEXT CHECK (action IN ('BUY', 'SELL')) NOT NULL,
    sol_amount NUMERIC NOT NULL,
    limit_price NUMERIC NOT NULL,
    slippage NUMERIC NOT NULL,
    status TEXT NOT NULL, -- 'active', 'pending', 'executed', 'failed'
    transaction_signature TEXT,
    executed_at TIMESTAMP,
    has_error BOOLEAN DEFAULT false,
    error_count INTEGER DEFAULT 0,
    last_error_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW() NOT NULL
);
-- Create index for faster querying by status
CREATE INDEX idx_limit_orders_status ON limit_orders(status);
-- Create index for faster querying by user
CREATE INDEX idx_limit_orders_user ON limit_orders(user_id);

