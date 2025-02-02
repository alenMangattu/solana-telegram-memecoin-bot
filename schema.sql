-- ================================================
-- DROP EXISTING OBJECTS (if any) to start cleanly
-- ================================================

DROP TABLE IF EXISTS token_holdings CASCADE;
DROP TABLE IF EXISTS slippage_configs CASCADE;
DROP TABLE IF EXISTS user_state CASCADE;
DROP TABLE IF EXISTS exception_logs CASCADE;
DROP TABLE IF EXISTS transaction_history CASCADE;
DROP TABLE IF EXISTS trading_automation CASCADE;
DROP TABLE IF EXISTS copy_trading CASCADE;
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
    user_id INTEGER NOT NULL,
    source_wallet UUID NOT NULL,
    target_wallet UUID NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_source_wallet FOREIGN KEY (source_wallet) REFERENCES wallets(wallet_id) ON DELETE CASCADE,
    CONSTRAINT fk_target_wallet FOREIGN KEY (target_wallet) REFERENCES wallets(wallet_id) ON DELETE CASCADE
);

-- ================================================
-- 6. Create the "trading_automation" table.
-- ================================================
CREATE TABLE trading_automation (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    strategy_name VARCHAR(255) NOT NULL,
    parameters JSONB NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_automation FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ================================================
-- 7. Create the "transaction_history" table.
-- ================================================
CREATE TABLE transaction_history (
    id SERIAL PRIMARY KEY,
    wallet_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRADE')),
    amount NUMERIC(18, 8) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE CASCADE
);

-- ================================================
-- 8. Create the "exception_logs" table.
-- ================================================
CREATE TABLE exception_logs (
    id SERIAL PRIMARY KEY,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    user_id INTEGER,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exception_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- ================================================
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
-- 10. Create the "slippage_configs" table.
-- ================================================
CREATE TABLE slippage_configs (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    slippage_percentage NUMERIC(5, 2) NOT NULL CHECK (slippage_percentage >= 0 AND slippage_percentage <= 100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_slippage FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ================================================
-- 11. Create the "token_holdings" table.
-- ================================================
CREATE TABLE token_holdings (
    id SERIAL PRIMARY KEY,
    wallet_id UUID NOT NULL,
    token_symbol VARCHAR(50) NOT NULL,
    balance NUMERIC(18, 8) NOT NULL CHECK (balance >= 0),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_token_wallet FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE CASCADE,
    CONSTRAINT unique_wallet_token UNIQUE (wallet_id, token_symbol)
);
