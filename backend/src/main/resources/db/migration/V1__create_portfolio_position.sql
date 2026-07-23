CREATE TABLE portfolio_position (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    quantity NUMERIC(20, 6) NOT NULL,
    entry_price NUMERIC(20, 6) NOT NULL,
    purchase_date DATE NOT NULL
);
