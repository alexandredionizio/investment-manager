-- IM-042: lotes B3 imutáveis; a chave é escopada à carteira.
CREATE TABLE b3_import_batches (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    content_sha256 VARCHAR(64) NOT NULL,
    source_filename VARCHAR(255) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_count INTEGER NOT NULL CHECK (row_count > 0),
    CONSTRAINT uq_b3_import_portfolio_content UNIQUE (portfolio_id, content_sha256)
);
CREATE TABLE b3_import_batch_rows (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES b3_import_batches(id),
    excel_row INTEGER NOT NULL,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id),
    CONSTRAINT uq_b3_import_batch_excel_row UNIQUE (batch_id, excel_row),
    CONSTRAINT uq_b3_import_transaction UNIQUE (transaction_id)
);
CREATE INDEX idx_b3_import_batches_user_portfolio ON b3_import_batches(user_id, portfolio_id);
