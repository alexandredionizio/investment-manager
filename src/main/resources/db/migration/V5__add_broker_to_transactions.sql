ALTER TABLE transactions
    ADD COLUMN broker_id BIGINT;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_broker
        FOREIGN KEY (broker_id)
            REFERENCES brokers(id);