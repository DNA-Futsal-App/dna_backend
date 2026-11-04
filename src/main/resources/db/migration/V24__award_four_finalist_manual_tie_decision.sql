-- A V23 permanece imutavel. A decisao administrativa define a ordem de quatro
-- finalistas no unico empate triplo (3o/4o/5o) autorizado na edicao.
-- Nunca altera as notas; a publicacao valida novamente todos os votos dos jurados.
CREATE TABLE award_four_finalist_decisions (
    edition_id UUID PRIMARY KEY REFERENCES award_four_finalist_exceptions(edition_id) ON DELETE CASCADE,
    rank_1_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_2_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_3_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    rank_4_entry_id UUID NOT NULL REFERENCES award_registration_entries(id) ON DELETE RESTRICT,
    decision_note TEXT NOT NULL CHECK (LENGTH(BTRIM(decision_note)) >= 20),
    decided_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_award_four_finalist_distinct CHECK (
        rank_1_entry_id <> rank_2_entry_id AND
        rank_1_entry_id <> rank_3_entry_id AND
        rank_1_entry_id <> rank_4_entry_id AND
        rank_2_entry_id <> rank_3_entry_id AND
        rank_2_entry_id <> rank_4_entry_id AND
        rank_3_entry_id <> rank_4_entry_id
    )
);
