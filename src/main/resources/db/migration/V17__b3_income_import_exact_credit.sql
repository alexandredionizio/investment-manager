ALTER TABLE incomes ADD COLUMN imported_credit_amount NUMERIC(19, 8);
ALTER TABLE incomes ADD COLUMN b3_income_fingerprint VARCHAR(64);
CREATE UNIQUE INDEX ux_incomes_b3_import_fingerprint
    ON incomes(portfolio_id, b3_income_fingerprint)
    WHERE b3_income_fingerprint IS NOT NULL;

-- Proventos efetivamente creditados pela B3 não informam data-base.
ALTER TABLE incomes ALTER COLUMN base_date DROP NOT NULL;
