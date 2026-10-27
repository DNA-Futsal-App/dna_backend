-- Additive: older registrations and Union preferences remain unchanged (NULL).
ALTER TABLE user_accounts
    ADD COLUMN followed_competition_code VARCHAR(40);
