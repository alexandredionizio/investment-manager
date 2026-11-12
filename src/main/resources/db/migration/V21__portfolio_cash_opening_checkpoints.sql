-- User-declared cash checkpoint, not a verified bank/broker reconciliation.
-- Immutable for auditability; no backfill or inferred zero balance for legacy portfolios.
CREATE TABLE portfolio_cash_opening_checkpoints (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    checkpoint_date DATE NOT NULL,
    balance NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    source_description VARCHAR(255) NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_portfolio_cash_checkpoint UNIQUE (portfolio_id)
);
