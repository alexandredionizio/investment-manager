-- External investor transfers only. Existing operations are NOT retrospectively backfilled.
CREATE TABLE portfolio_capital_movements (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    request_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CONTRIBUTION', 'WITHDRAWAL')),
    movement_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_portfolio_capital_movement_request UNIQUE (portfolio_id, request_id)
);
CREATE INDEX idx_portfolio_capital_movements_date
    ON portfolio_capital_movements (portfolio_id, movement_date, id);
