ALTER TABLE award_registrations
    ADD COLUMN gender VARCHAR(20);

UPDATE award_registrations
SET gender = 'MALE'
WHERE gender IS NULL;

ALTER TABLE award_registrations
    ALTER COLUMN gender SET NOT NULL;

ALTER TABLE award_registrations
    ADD CONSTRAINT ck_award_registration_gender
        CHECK (
            gender IN (
                       'MALE',
                       'FEMALE'
                )
            );