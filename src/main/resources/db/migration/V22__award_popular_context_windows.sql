-- V20 continua imutável para preservar checksums em ambientes migrados.
-- O modelo antigo por edição permanece para histórico; as novas janelas são por disputa.
CREATE TABLE award_popular_context_windows (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN
       ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    manually_closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_award_popular_context_window_time CHECK (closes_at > opens_at),
    CONSTRAINT uk_award_popular_context_window UNIQUE
       (edition_id, event_id, division_id, category_id, gender, contest_category)
);
CREATE INDEX idx_award_popular_context_windows_edition
    ON award_popular_context_windows (edition_id, opens_at, closes_at);
