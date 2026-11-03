-- Janela separada da votação dos técnicos.
CREATE TABLE award_popular_voting_windows (
    edition_id UUID PRIMARY KEY REFERENCES award_editions(id) ON DELETE CASCADE,
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    manually_closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_award_popular_window CHECK (closes_at > opens_at)
);

-- A unicidade é a última linha de defesa contra votos repetidos ou concorrentes.
-- ON DELETE SET NULL preserva as contagens quando a conta exerce exclusão de dados.
CREATE TABLE award_popular_votes (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN
      ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    entry_id UUID NOT NULL REFERENCES award_pre_vote_finalists(entry_id) ON DELETE RESTRICT,
    voter_user_id UUID REFERENCES user_accounts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_popular_voter_context UNIQUE
      (edition_id, event_id, division_id, category_id, gender, contest_category, voter_user_id)
);
CREATE INDEX idx_award_popular_vote_tally
    ON award_popular_votes (edition_id, event_id, division_id, category_id, gender, contest_category, entry_id);
