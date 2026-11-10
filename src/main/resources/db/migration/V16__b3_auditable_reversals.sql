-- IM-052 Etapa 2: nao removemos nem alteramos os valores originais importados.
ALTER TABLE transactions ADD COLUMN reversed_at TIMESTAMPTZ;
CREATE TABLE b3_transaction_reversals (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL UNIQUE REFERENCES transactions(id),
    batch_row_id BIGINT NOT NULL UNIQUE REFERENCES b3_import_batch_rows(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    reason VARCHAR(500) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 10 AND 500),
    reversed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_b3_reversals_user ON b3_transaction_reversals(user_id, reversed_at);
