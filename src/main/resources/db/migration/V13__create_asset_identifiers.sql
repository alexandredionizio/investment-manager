-- Confirmed identifiers only; legacy assets and their financial history remain unchanged.
CREATE TABLE asset_identifiers (
    exchange VARCHAR(50) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('TICKER', 'ISIN')),
    value VARCHAR(20) NOT NULL,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    source VARCHAR(50) NOT NULL,
    observed_name VARCHAR(120) NOT NULL,
    effective_date DATE,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exchange, kind, value)
);
CREATE INDEX ix_asset_identifiers_asset ON asset_identifiers(asset_id);
ALTER TABLE transactions ADD COLUMN recorded_ticker VARCHAR(20);
UPDATE transactions t SET recorded_ticker = a.ticker FROM assets a WHERE a.id = t.asset_id;
