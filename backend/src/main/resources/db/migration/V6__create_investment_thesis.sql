CREATE TABLE investment_thesis (
    isin VARCHAR(12) PRIMARY KEY,
    why_interesting TEXT,
    expected_development TEXT,
    exit_criteria TEXT,
    status VARCHAR(10),
    status_reason VARCHAR(500),
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_investment_thesis_status CHECK (status IN ('WATCHING', 'REJECTED')),
    CONSTRAINT ck_investment_thesis_reason_needs_status CHECK (status_reason IS NULL OR status IS NOT NULL)
);
