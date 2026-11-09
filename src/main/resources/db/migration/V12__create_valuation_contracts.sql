CREATE TABLE valuation_contracts (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id BIGINT NOT NULL REFERENCES portfolios(id),
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    terms_json TEXT NOT NULL,
    CONSTRAINT uk_valuation_contract UNIQUE (portfolio_id, asset_id)
);
