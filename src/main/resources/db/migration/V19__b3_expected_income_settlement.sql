-- A previsão permanece auditável; o crédito efetivo continua registrado em incomes.
ALTER TABLE b3_expected_incomes
    ADD COLUMN matched_income_id BIGINT REFERENCES incomes(id) ON DELETE SET NULL,
    ADD COLUMN matched_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_b3_expected_matched_income ON b3_expected_incomes(matched_income_id)
    WHERE matched_income_id IS NOT NULL;
CREATE INDEX idx_b3_expected_outstanding ON b3_expected_incomes(portfolio_id, asset_id, expected_payment_date)
    WHERE matched_income_id IS NULL;
