CREATE TABLE portfolios (
                            id BIGSERIAL PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            description VARCHAR(255),
                            created_at TIMESTAMP NOT NULL
);