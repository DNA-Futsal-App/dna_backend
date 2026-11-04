-- Permite apenas uma disputa com quatro finalistas por edição.
-- Exceções são ativas somente quando explicitamente cadastradas após conferência das notas.
CREATE TABLE award_four_finalist_exceptions (
    edition_id UUID PRIMARY KEY REFERENCES award_editions(id) ON DELETE CASCADE,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN
      ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A constraint antiga foi criada na V19 como CHECK inline, gerando este nome pelo PostgreSQL.
-- Nunca modificar V19: preservar o histórico do Flyway em bases que já migraram.
ALTER TABLE award_pre_vote_finalists
    DROP CONSTRAINT award_pre_vote_finalists_rank_check;
ALTER TABLE award_pre_vote_finalists
    ADD CONSTRAINT ck_award_pre_vote_finalists_rank_1_to_4 CHECK (rank BETWEEN 1 AND 4);
