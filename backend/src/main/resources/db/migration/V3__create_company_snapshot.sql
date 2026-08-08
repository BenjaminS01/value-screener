CREATE TABLE company_snapshot (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    sector VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    business_description TEXT NOT NULL,
    moat_note TEXT,
    opportunities_and_risks_note TEXT,
    pe_ratio NUMERIC(12, 4),
    pb_ratio NUMERIC(12, 4),
    roe_percent NUMERIC(12, 4),
    debt_to_equity NUMERIC(12, 4),
    current_year_net_margin_percent NUMERIC(12, 4),
    current_year_fcf_positive BOOLEAN,
    current_year_net_income_increased_yoy BOOLEAN,
    insider_ownership_percent NUMERIC(12, 4),
    as_of_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE company_snapshot_source (
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id) ON DELETE CASCADE,
    source VARCHAR(500) NOT NULL,
    PRIMARY KEY (company_snapshot_id, source)
);

CREATE TABLE company_snapshot_updated_field (
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id) ON DELETE CASCADE,
    field_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (company_snapshot_id, field_name)
);
