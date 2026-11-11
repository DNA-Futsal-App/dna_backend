CREATE TABLE award_gold_eligibility (
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    stage VARCHAR(12) NOT NULL CHECK (stage IN ('PAULISTA', 'UNIAO')),
    source_collected_at TIMESTAMPTZ,
    prepared_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (edition_id, event_id, division_id, category_id)
);

CREATE TABLE award_gold_eligible_teams (
    edition_id UUID NOT NULL,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    team_id VARCHAR(100) NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    PRIMARY KEY (edition_id, event_id, division_id, category_id, team_id),
    FOREIGN KEY (edition_id, event_id, division_id, category_id)
        REFERENCES award_gold_eligibility(edition_id, event_id, division_id, category_id)
        ON DELETE CASCADE
);
