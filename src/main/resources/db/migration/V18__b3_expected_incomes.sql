-- Previsões são separadas dos créditos efetivamente recebidos.
CREATE TABLE b3_expected_incomes (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    income_type VARCHAR(32) NOT NULL,
    expected_payment_date DATE NOT NULL,
    quantity NUMERIC(19,8) NOT NULL CHECK (quantity > 0),
    expected_net_amount NUMERIC(19,8) NOT NULL CHECK (expected_net_amount > 0),
    institution VARCHAR(255),
    fingerprint CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_b3_expected_fingerprint UNIQUE (portfolio_id, fingerprint)
);
CREATE INDEX idx_b3_expected_portfolio_date ON b3_expected_incomes(portfolio_id, expected_payment_date);
