-- Janela extraordinaria independente da abertura global. Nao altera inscricoes existentes.
CREATE TABLE award_registration_reopenings (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_award_reopening_window CHECK (closes_at > opens_at),
    CONSTRAINT uk_award_registration_reopening_context UNIQUE (
        edition_id, event_id, division_id, category_id, gender, contest_category
    )
);
CREATE INDEX idx_award_reopening_open ON award_registration_reopenings
    (edition_id, opens_at, closes_at) WHERE closed_at IS NULL;
