ALTER TABLE award_registration_entries
DROP CONSTRAINT ck_award_registration_entry_media_status;

ALTER TABLE award_registration_entries
    ADD CONSTRAINT ck_award_registration_entry_media_status
        CHECK (
            media_status IN (
                             'PENDING',
                             'PROCESSING',
                             'READY',
                             'FAILED'
                )
            );

CREATE INDEX idx_award_registration_entries_media_status
    ON award_registration_entries (media_status);