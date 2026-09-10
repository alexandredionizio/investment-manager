TRUNCATE TABLE portfolios RESTART IDENTITY CASCADE;

ALTER TABLE portfolios
    ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE portfolios
    ADD CONSTRAINT fk_portfolios_user
        FOREIGN KEY (user_id)
            REFERENCES users(id);