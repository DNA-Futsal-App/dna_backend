# Prêmio DNA Futsal — Fase 3: inscrição de técnicos por convite exclusivo

## Pré-requisitos

- Fases 1 e 2 aplicadas ao backend; migrations V26, V27 e V28 já executadas.
- Aplicar V29 pelo Flyway durante o deploy do backend. Nunca editar migrations previamente aplicadas.
- Publicar o backend antes do frontend.
- O link da Fase 2 exige a relação de equipes da Chave Ouro previamente preparada.

## Fluxo

1. Administrador gera o link de uma edição + modalidade + divisão + categoria.
2. Técnico abre o convite, escolhe o clube daquela categoria, informa função e uma URL de comprovação opcional.
3. Usuário existente: login no próprio convite e POST autenticado de solicitação.
4. Usuário novo: POST /api/v1/auth/register com `coachCategoryRegistration`.
   A solicitação é criada como PENDING na mesma transação da conta.
   A nova conta passa a PENDING_EMAIL_VERIFICATION; usuário precisa confirmar seu e-mail.
5. Admin consulta solicitações e verifica documentalmente o vínculo real com o clube.
6. Admin aprova: só agora se criam/reutilizam a credencial e o contexto de votação.
   Votos continuam sujeitos à janela de votação e ao filtro de atletas da Chave Ouro.
7. Admin rejeita com justificativa: o treinador pode apresentar nova solicitação, mantendo o histórico da rejeição.

### Endpoints

- GET `/api/v1/public/awards/coach-category-links/{token}/teams`: somente times do evento do convite, com token válido.
- POST `/api/v1/awards/coach-category-applications` (autenticado): `{token, teamId, role, evidenceUrl?}`.
- GET `/api/v1/awards/coach-category-applications/me` (autenticado).
- GET `/api/v1/admin/awards/editions/{editionId}/coach-category-applications?status=PENDING|APPROVED|REJECTED`.
- POST `/api/v1/admin/awards/editions/{editionId}/coach-category-applications/{id}/approve`.
- POST `/api/v1/admin/awards/editions/{editionId}/coach-category-applications/{id}/reject`: `{ "reason": "..." }`.

### Restrições e segurança

- Um convite identifica a categoria, mas **não confere acesso à votação**.
- O clube do treinador pode não pertencer à Chave Ouro: o filtro Ouro restringe **atletas votáveis**, não o clube que o técnico representa.
- A aprovação é exclusivamente manual por conta administradora. Um link não prova cargo técnico.
- Uma conta só pode manter uma solicitação PENDING ou APPROVED na mesma categoria/edição.
- A conta nova não pode ser aprovada antes de confirmar o e-mail.
- Contas legadas marcadas como ACTIVE continuam compatíveis com o sistema anterior; uma migração própria de verificação de e-mail de contas antigas deverá ser avaliada.
- Links expirados/revogados não aceitam novas solicitações. Solicitações já pendentes podem ser analisadas.
- As credenciais e os votos legados não são removidos nesta fase; o desligamento do fluxo antigo ocorre na Fase 6.
- A URL de comprovação é uma declaração do candidato: deve ser conferida pelo admin. Não existem integrações automáticas com fontes de identidade FPFS nesta fase.

### Checks de integração

- POST com token inválido/expirado ou time de outro evento não deve inserir solicitação.
- Registro novo deve iniciar PENDING_EMAIL_VERIFICATION e produzir solicitação PENDING;
  POST de login antes da confirmação não deve abrir sessão.
- Admin não pode aprovar conta pendente de confirmação.
- Aprovação deve criar um contexto ativo no V2; a mesma aprovação repetida deve retornar conflito.
- Duas requisições concorrentes para a mesma conta/categoria devem gerar no máximo uma solicitação pendente.
- Sem aprovação, `/coach-voting-v2` deve continuar negando acesso a esse contexto.
- Fluxos de inscrição de atletas, pré-voto e votação popular não devem ser modificados.
