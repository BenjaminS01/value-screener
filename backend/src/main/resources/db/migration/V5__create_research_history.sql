-- Append-only history: rows are only ever inserted. No ON DELETE CASCADE on purpose.
CREATE TABLE research_finding_history (
    id BIGSERIAL PRIMARY KEY,
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id),
    criterion_key VARCHAR(40) NOT NULL,
    numeric_value NUMERIC(12,4),
    boolean_value BOOLEAN,
    claim TEXT NOT NULL,
    source_url VARCHAR(1000),
    as_of_date DATE NOT NULL,
    replaced_at TIMESTAMPTZ NOT NULL,
    origin VARCHAR(10) NOT NULL,
    CONSTRAINT ck_research_finding_history_origin CHECK (origin IN ('INGEST', 'SYNC'))
);

CREATE INDEX ix_research_finding_history_snapshot ON research_finding_history (company_snapshot_id);
CREATE INDEX ix_research_finding_history_snapshot_criterion
    ON research_finding_history (company_snapshot_id, criterion_key);

CREATE TABLE stock_group_history (
    id BIGSERIAL PRIMARY KEY,
    company_snapshot_id BIGINT NOT NULL REFERENCES company_snapshot(id),
    previous_group_key VARCHAR(40) REFERENCES stock_group(key),
    previous_group_source VARCHAR(10),
    new_group_key VARCHAR(40) NOT NULL REFERENCES stock_group(key),
    new_group_source VARCHAR(10) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    origin VARCHAR(10) NOT NULL,
    CONSTRAINT ck_stock_group_history_origin CHECK (origin IN ('INGEST', 'SYNC', 'MANUAL')),
    CONSTRAINT ck_stock_group_history_previous_pair
        CHECK ((previous_group_key IS NULL) = (previous_group_source IS NULL))
);

CREATE INDEX ix_stock_group_history_snapshot ON stock_group_history (company_snapshot_id);
