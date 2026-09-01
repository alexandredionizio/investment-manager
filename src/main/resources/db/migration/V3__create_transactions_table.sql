CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,

                              portfolio_id BIGINT NOT NULL,
                              asset_id BIGINT NOT NULL,

                              type VARCHAR(20) NOT NULL,

                              quantity NUMERIC(19, 8) NOT NULL,
                              unit_price NUMERIC(19, 8) NOT NULL,

                              transaction_date DATE NOT NULL,

                              CONSTRAINT fk_transactions_portfolio
                                  FOREIGN KEY (portfolio_id)
                                      REFERENCES portfolios(id),

                              CONSTRAINT fk_transactions_asset
                                  FOREIGN KEY (asset_id)
                                      REFERENCES assets(id)
);