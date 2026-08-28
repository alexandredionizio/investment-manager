CREATE TABLE assets (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    type VARCHAR(30) NOT NULL,
    sector VARCHAR(100),
    exchange VARCHAR(50),

    CONSTRAINT uk_assets_ticker_exchange UNIQUE (ticker, exchange)
);