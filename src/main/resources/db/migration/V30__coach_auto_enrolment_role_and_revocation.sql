-- Evolucao aditiva a partir da V29: nao apagar dados existentes.
-- COACH eh um papel unico adicional (nao remove ADMIN).
ALTER TABLE user_accounts DROP CONSTRAINT ck_user_accounts_role;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_role
    CHECK (role IN ('USER', 'ADMIN', 'COACH'));

-- Aprovacao automatica apos convite ou confirmacao de e-mail.
-- Revogacao administrativa encerra o vinculo e permite um novo convite no futuro.
ALTER TABLE award_coach_category_applications
    DROP CONSTRAINT award_coach_category_applications_status_check;
ALTER TABLE award_coach_category_applications
    ADD CONSTRAINT ck_coach_category_application_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVOKED'));
ALTER TABLE award_coach_category_applications
    DROP CONSTRAINT ck_coach_category_app_review;
ALTER TABLE award_coach_category_applications
    ADD CONSTRAINT ck_coach_category_app_review CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (status = 'APPROVED' AND reviewed_at IS NOT NULL)
        OR (status IN ('REJECTED', 'REVOKED') AND reviewed_at IS NOT NULL
            AND reviewed_by_user_id IS NOT NULL)
    );
-- O administrador pode atribuir categorias extras sem precisar emitir um convite.
ALTER TABLE award_coach_category_applications ALTER COLUMN link_id DROP NOT NULL;

-- Preserva trilha administrativa mesmo quando os votos sao removidos por exigencia de negocio.
CREATE TABLE award_coach_revocation_audit (
    id UUID PRIMARY KEY,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    target_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('CATEGORY_REVOKED','COACH_REVOKED')),
    reason VARCHAR(500) NOT NULL,
    performed_by_user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    ballots_deleted INTEGER NOT NULL CHECK (ballots_deleted >= 0),
    votes_deleted INTEGER NOT NULL CHECK (votes_deleted >= 0),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_coach_revocation_edition_user
    ON award_coach_revocation_audit(edition_id, user_id, created_at DESC);

-- Usuarios ja aprovados antes desta migration recebem o papel de tecnico.
UPDATE user_accounts u
   SET role = 'COACH', token_version = token_version + 1
 WHERE role = 'USER'
   AND EXISTS (SELECT 1 FROM award_coach_category_applications a
                WHERE a.user_id = u.id AND a.status = 'APPROVED');

-- Serialize a submissao de votos com a revogacao do mesmo contexto,
-- inclusive se alguem chamar diretamente o endpoint legado V2.
CREATE FUNCTION award_require_active_voter_before_ballot() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE voter_active BOOLEAN;
BEGIN
    SELECT v.active INTO voter_active
      FROM award_coach_voters v WHERE v.id = NEW.coach_voter_id FOR UPDATE;
    IF voter_active IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Coach voting context is inactive'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_award_require_active_voter_before_ballot
BEFORE INSERT ON award_ballots
FOR EACH ROW EXECUTE FUNCTION award_require_active_voter_before_ballot();
