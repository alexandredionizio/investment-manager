-- IM-042: tentativas explicitamente confirmadas mas rejeitadas; nao criam transacoes.
CREATE TABLE b3_import_rejected_attempts (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    source_filename VARCHAR(255) NOT NULL,
    source_sha256 VARCHAR(64),
    rejected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT NOT NULL
);
CREATE TABLE b3_import_rejected_rows (
    attempt_id BIGINT NOT NULL REFERENCES b3_import_rejected_attempts(id) ON DELETE CASCADE,
    excel_row INTEGER NOT NULL CHECK (excel_row > 0),
    reason TEXT NOT NULL,
    PRIMARY KEY (attempt_id, excel_row)
);
CREATE INDEX idx_b3_import_rejected_user_portfolio
    ON b3_import_rejected_attempts(user_id, portfolio_id, rejected_at DESC);
