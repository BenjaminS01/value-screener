CREATE TABLE company_snapshot (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(10) NOT NULL,
    isin VARCHAR(12) NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    sector VARCHAR(100) NOT NULL,
    country VARCHAR(100) NOT NULL,
    business_description TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE research_finding (
    id BIGSERIAL PRIMARY KEY,
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id) ON DELETE CASCADE,
    criterion_key VARCHAR(40) NOT NULL,
    numeric_value NUMERIC(12, 4),
    boolean_value BOOLEAN,
    claim TEXT NOT NULL,
    source_url VARCHAR(1000),
    as_of_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_research_finding_snapshot_criterion UNIQUE (company_snapshot_id, criterion_key)
);
