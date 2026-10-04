ALTER TABLE award_registration_entries
    ADD COLUMN review_status VARCHAR(20),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN reviewed_by_user_id UUID,
    ADD COLUMN review_reason VARCHAR(1000);

UPDATE award_registration_entries
SET review_status = 'PENDING_REVIEW'
WHERE review_status IS NULL;

ALTER TABLE award_registration_entries
    ALTER COLUMN review_status SET NOT NULL,
    ALTER COLUMN review_status SET DEFAULT 'PENDING_REVIEW';

ALTER TABLE award_registration_entries
    ADD CONSTRAINT fk_award_registration_entry_reviewer
        FOREIGN KEY (reviewed_by_user_id)
        REFERENCES user_accounts(id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_award_registration_entry_review_status
        CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    ADD CONSTRAINT ck_award_registration_entry_review_metadata
        CHECK (
            (
                review_status = 'PENDING_REVIEW'
                AND reviewed_at IS NULL
                AND reviewed_by_user_id IS NULL
                AND review_reason IS NULL
            )
            OR (
                review_status = 'APPROVED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND review_reason IS NULL
            )
            OR (
                review_status = 'REJECTED'
                AND reviewed_at IS NOT NULL
                AND reviewed_by_user_id IS NOT NULL
                AND review_reason IS NOT NULL
                AND btrim(review_reason) <> ''
            )
        );

CREATE INDEX idx_award_registration_entries_review
    ON award_registration_entries (review_status, registration_id);
