-- Fase 3: solicitacoes verificaveis para o juri tecnico por categoria.
-- Nao altera convites anteriores, credenciais nem votos ja registrados.
CREATE TABLE award_coach_category_applications (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL REFERENCES award_coach_category_links(id) ON DELETE RESTRICT,
    edition_id UUID NOT NULL REFERENCES award_editions(id) ON DELETE RESTRICT,
    user_id UUID NOT NULL REFERENCES user_accounts(id) ON DELETE RESTRICT,
    event_id BIGINT NOT NULL,
    division_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    team_id VARCHAR(100) NOT NULL,
    team_name VARCHAR(150) NOT NULL,
    coach_role VARCHAR(80) NOT NULL,
    evidence_url VARCHAR(500),
    status VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    review_reason VARCHAR(500),
    reviewed_by_user_id UUID REFERENCES user_accounts(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_coach_category_app_review CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL)
    )
);

-- Um treinador nao pode manter duas solicitacoes pendentes/aprovadas
-- para a mesma categoria, mesmo que receba outro convite.
CREATE UNIQUE INDEX uk_coach_category_app_active_context
    ON award_coach_category_applications(user_id, edition_id, event_id, division_id, category_id)
    WHERE status IN ('PENDING', 'APPROVED');
CREATE INDEX idx_coach_category_app_admin
    ON award_coach_category_applications(edition_id, status, created_at);
CREATE INDEX idx_coach_category_app_user
    ON award_coach_category_applications(user_id, created_at DESC);
