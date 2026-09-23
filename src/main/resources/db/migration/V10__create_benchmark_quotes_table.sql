CREATE TABLE benchmark_quotes (
                                  id BIGSERIAL PRIMARY KEY,
                                  benchmark VARCHAR(20) NOT NULL,
                                  quote_date DATE NOT NULL,
                                  value NUMERIC(19, 8) NOT NULL,

                                  CONSTRAINT uk_benchmark_quotes_benchmark_date
                                      UNIQUE (benchmark, quote_date)
);

CREATE INDEX idx_benchmark_quotes_benchmark_date
    ON benchmark_quotes (benchmark, quote_date);