CREATE TABLE award_ballots (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL
        REFERENCES award_editions(id)
        ON DELETE RESTRICT,
    coach_voter_id UUID NOT NULL
        REFERENCES award_coach_voters(id)
        ON DELETE RESTRICT,
    voter_user_id UUID NOT NULL
        REFERENCES user_accounts(id)
        ON DELETE RESTRICT,
    submitted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_ballot_voter
        UNIQUE (edition_id, coach_voter_id),
    CONSTRAINT uk_award_ballot_user
        UNIQUE (edition_id, voter_user_id)
);

CREATE INDEX idx_award_ballots_user
    ON award_ballots (voter_user_id, edition_id);

CREATE TABLE award_ballot_votes (
    id UUID PRIMARY KEY,
    ballot_id UUID NOT NULL
        REFERENCES award_ballots(id)
        ON DELETE CASCADE,
    award_vote_category_id UUID NOT NULL
        REFERENCES award_vote_categories(id)
        ON DELETE RESTRICT,
    candidate_id UUID NOT NULL
        REFERENCES award_candidates(id)
        ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_award_ballot_vote_category
        UNIQUE (ballot_id, award_vote_category_id)
);

CREATE INDEX idx_award_ballot_votes_candidate
    ON award_ballot_votes (candidate_id);

CREATE INDEX idx_award_ballot_votes_category
    ON award_ballot_votes (award_vote_category_id);
