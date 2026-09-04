CREATE TABLE incomes (
                         id BIGSERIAL PRIMARY KEY,

                         portfolio_id BIGINT NOT NULL,
                         asset_id BIGINT NOT NULL,

                         type VARCHAR(30) NOT NULL,

                         amount_per_unit NUMERIC(19, 8) NOT NULL,
                         quantity NUMERIC(19, 8) NOT NULL,

                         payment_date DATE NOT NULL,

                         CONSTRAINT fk_incomes_portfolio
                             FOREIGN KEY (portfolio_id)
                                 REFERENCES portfolios(id),

                         CONSTRAINT fk_incomes_asset
                             FOREIGN KEY (asset_id)
                                 REFERENCES assets(id)
);