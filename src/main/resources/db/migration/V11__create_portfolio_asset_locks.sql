CREATE TABLE portfolio_asset_locks (
    portfolio_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    PRIMARY KEY (portfolio_id, asset_id),
    CONSTRAINT fk_portfolio_asset_locks_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES portfolios(id),
    CONSTRAINT fk_portfolio_asset_locks_asset
        FOREIGN KEY (asset_id) REFERENCES assets(id)
);
