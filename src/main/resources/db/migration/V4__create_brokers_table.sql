CREATE TABLE brokers (
                         id BIGSERIAL PRIMARY KEY,
                         name VARCHAR(100) NOT NULL UNIQUE
);