-- Novas decisoes de empate sao por disputa. V23/V24 permanecem inalteradas:
-- a disputa historica Sub-7 A1 conserva sua excecao e seus quatro finalistas.
CREATE TABLE award_pre_vote_tie_decisions (
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE CASCADE,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(20) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    contest_category VARCHAR(40) NOT NULL CHECK (contest_category IN
      ('BEAUTIFUL_GOAL', 'BEST_DRIBBLE', 'FREE_KICK_GOAL', 'BEST_SAVE')),
    finalist_count SMALLINT NOT NULL CHECK (finalist_count IN (3, 4)),
    rank_1_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_2_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_3_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_4_entry_id UUID REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    decided_by_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (edition_id, event_id, division_id, category_id, gender, contest_category),
    CONSTRAINT ck_award_tie_fourth_entry CHECK (
        (finalist_count = 3 AND rank_4_entry_id IS NULL)
        OR (finalist_count = 4 AND rank_4_entry_id IS NOT NULL)
    ),
    CONSTRAINT ck_award_tie_distinct CHECK (
        rank_1_entry_id <> rank_2_entry_id AND
        rank_1_entry_id <> rank_3_entry_id AND
        rank_2_entry_id <> rank_3_entry_id AND
        (rank_4_entry_id IS NULL OR (
            rank_4_entry_id <> rank_1_entry_id AND
            rank_4_entry_id <> rank_2_entry_id AND
            rank_4_entry_id <> rank_3_entry_id
        ))
    )
);
