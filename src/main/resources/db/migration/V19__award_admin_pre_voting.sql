-- Notas individuais, uma por administrador e vídeo.
CREATE TABLE award_pre_vote_scores (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE CASCADE,
    admin_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    approval_at TIMESTAMPTZ NOT NULL,
    score SMALLINT NOT NULL CHECK (score BETWEEN 1 AND 10),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_pre_vote_entry_admin_approval UNIQUE (entry_id, admin_user_id, approval_at)
);
CREATE INDEX idx_award_pre_vote_scores_admin ON award_pre_vote_scores (admin_user_id);

-- Snapshot definitivo dos classificados (3 por contexto, sem misturar gêneros/campeonatos).
CREATE TABLE award_pre_vote_finalists (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    entry_id UUID NOT NULL UNIQUE REFERENCES award_registration_entries(id) ON DELETE CASCADE,
    rank SMALLINT NOT NULL CHECK (rank BETWEEN 1 AND 3),
    average_score NUMERIC(8,4) NOT NULL CHECK (average_score BETWEEN 1 AND 10),
    score_count INTEGER NOT NULL CHECK (score_count > 0),
    finalized_at TIMESTAMPTZ NOT NULL,
    finalized_by_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT uk_award_pre_vote_finalist_context_rank UNIQUE
        (edition_id, event_id, division_id, category_id, gender, contest_category, rank)
);
CREATE INDEX idx_award_pre_vote_finalists_context ON award_pre_vote_finalists
    (edition_id, event_id, division_id, category_id, gender, contest_category);
