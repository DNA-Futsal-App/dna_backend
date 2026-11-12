# DNA Futsal — Credenciamento automático e revogação de técnicos (V30)

## Requisitos

Aplicar as fases 1–4 antes deste patch. A migration `V30__coach_auto_enrolment_role_and_revocation.sql` pressupõe a V29. Execute em homologação com backup. Não execute `flyway repair`, `clean` ou `baseline` como substitutos da migration.

## Fluxo

- Convite por modalidade/divisão/categoria é um token secreto emitido na Fase 2. Revogação do **link** impede novos cadastros, mas não exclui automaticamente votos de quem já se credenciou.
- Usuário já ativo: o cadastro pelo link cria `APPROVED`, credencial e contexto automaticamente.
- Conta nova: fica `PENDING_EMAIL_VERIFICATION` e solicitação `PENDING`. Após o commit da confirmação de e-mail, o hook tenta criar credencial/contexto em uma nova transação e muda o registro para `APPROVED`, sem administrador. Uma falha temporária da FPFS não desfaz a confirmação de e-mail; a consulta `/me` recupera pedidos pendentes de contas já confirmadas (e a página de votação chama `/me` antes de carregar contextos).
- `role='COACH'` é gravado para usuário normal credenciado. A conta `ADMIN` mantém seu papel. JWT incorpora `SCOPE_COACH` e mantém `SCOPE_USER`. Os endpoints de votação verificam **também** vínculo com categoria ativo, credencial e situação do campeonato.
- Admin pode conceder categoria extra sem link, revogar uma categoria ou revogar todas as categorias de uma edição.
- Revogação de **categoria** remove o registro de voto (`award_ballots`) daquele contexto; FK `award_ballot_votes.ballot_id ON DELETE CASCADE` remove escolhas. A aplicação vai a `REVOKED`, o contexto vira `active=false` e uma auditoria armazena somente autor/motivo/quantidades.
- Revogação de **treinador** remove TODOS os votos de seus contextos na edição, inclusive contextos legados, e desativa a credencial. Se não houver outros vínculos `APPROVED`, o papel volta a `USER` (ADMIN é preservado). O token JWT antigo fica inválido pela versão.
- Endpoint legado de desativação de credencial/contexto passa a usar a mesma rotina. Contrato de resposta desses dois endpoints muda de `CoachCredentialResponse` para `RevocationResult`; consumidores que usam o retorno precisam adaptar. O endpoint legado de adição de categoria passa a criar também o vínculo exigido pela nova votação, inferindo a modalidade pelo catálogo; em caso de ambiguidade o administrador deve usar o painel novo.

## Endpoints administrativos

- `GET /api/v1/admin/awards/editions/{editionId}/coach-category-applications?status=APPROVED|PENDING|REVOKED|REJECTED`
- `POST /api/v1/admin/awards/editions/{editionId}/coach-category-applications/{applicationId}/revoke` com `{ "reason": "..." }`
- `POST /api/v1/admin/awards/editions/{editionId}/coach-category-applications/credentials/{credentialId}/revoke` com `{ "reason": "..." }`
- `POST /api/v1/admin/awards/editions/{editionId}/coach-category-applications/credentials/{credentialId}/categories` com `{ "eventId":123, "divisionId":1, "categoryId":7, "gender":"MALE", "teamId":"123" }`

## Segurança / impacto

A V30 não apaga votos na migração. Somente o comando explícito de revogação os exclui (irreversivelmente). Um gatilho no banco trava/valida o contexto antes de inserir `award_ballots`, de modo que não apareçam votos posteriores à revogação mesmo usando a API V2 legada.

**Risco de acesso por convite repassado:** qualquer pessoa com token válido pode se identificar como treinador do clube. Isso é consequência da remoção de aprovação humana. Para mitigar, compartilhe links apenas em canal confiável; no futuro, limite usos ou associe convites a destinatários específicos.

## Smoke tests sugeridos

1. Conta já confirmada + link válido: cria contexto e `COACH`, aparece `/app/votacao-tecnicos`.
2. Conta nova + link válido: requer verificação por e-mail; depois credenciamento automático.
3. Link revogado: bloqueia novos cadastros, sem alterar treinadores já cadastrados.
4. Remover uma categoria já votada: `award_ballots` e `award_ballot_votes` daquele contexto deixam de existir; outros contextos permanecem.
5. Remover treinador com múltiplas categorias: desaparecem todos os votos na edição, outras edições não são afetadas.
6. Administrador em papel ADMIN recebe categoria extra, sem perder administração.
7. Duas submissões simultâneas: somente um registro por contexto.
8. `git apply --check`, `mvn test`, `npm run lint`, `npm run build` antes de promover o release.
