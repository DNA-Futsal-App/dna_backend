-- Stores only successfully fetched snapshots; never overwrites event data with
-- fabricated or cross-competition fallback responses.
CREATE TABLE sports_snapshot_archive (
    event_id BIGINT PRIMARY KEY CHECK (event_id > 0),
    season INTEGER NOT NULL,
    title TEXT,
    division TEXT,
    category TEXT,
    snapshot_json JSONB NOT NULL,
    last_success_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sports_snapshot_archive_default
    ON sports_snapshot_archive (season, title, division, category, last_success_at DESC);
