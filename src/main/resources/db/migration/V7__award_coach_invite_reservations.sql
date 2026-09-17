ALTER TABLE award_coach_invites
    ADD COLUMN reserved_by_user_id UUID
        REFERENCES user_accounts(id)
        ON DELETE SET NULL,
    ADD COLUMN reserved_at TIMESTAMPTZ,
    ADD COLUMN reservation_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_award_coach_invite_reserved_user
    ON award_coach_invites (reserved_by_user_id)
    WHERE reserved_by_user_id IS NOT NULL
      AND status = 'PENDING';

CREATE INDEX idx_award_coach_invite_reservation
    ON award_coach_invites (
        reserved_by_user_id,
        reservation_expires_at
    );
