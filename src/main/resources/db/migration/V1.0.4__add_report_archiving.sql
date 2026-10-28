ALTER TABLE report
    ADD COLUMN IF NOT EXISTS archived_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS archived_by     VARCHAR,
    ADD COLUMN IF NOT EXISTS archive_reason  VARCHAR;

CREATE INDEX IF NOT EXISTS report_archived_at_idx ON report (archived_at);
