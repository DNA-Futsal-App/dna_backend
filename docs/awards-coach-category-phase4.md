# DNA Futsal — Fase 4: votação por categoria aprovada

## Pré-requisitos

- Fases 1, 2 e 3 aplicadas no backend e as migrations V26–V29 executadas.
- Aprovação administrativa da solicitação de treinador; esta fase não cria ou aprova credenciais.
- O elenco da Chave Ouro da categoria deve ter sido preparado na Fase 1.
- A edição deve estar com votação aberta e dentro da janela vigente para registrar votos.

## API (JWT do treinador)

- `GET /api/v1/awards/coach-category-voting/contexts` lista **somente** contextos ativos associados a solicitações APPROVED, pertencentes ao usuário autenticado.
- `GET /api/v1/awards/coach-category-voting/context?contextId=UUID` obtém as categorias de premiação e equipes, marcando `athleteEligible` para cada equipe.
- `GET /api/v1/awards/coach-category-voting/candidates?contextId=UUID&voteCategoryId=UUID&teamId=ID` consulta os candidatos válidos.
- `POST /api/v1/awards/coach-category-voting/ballot` recebe `{ "contextId": "UUID", "votes": [{ "voteCategoryId": "UUID", "candidateId": "UUID" }] }`.
- `GET /api/v1/awards/coach-category-voting/ballot?contextId=UUID` retorna o comprovante após o voto.

## Garantias

1. Um convite não é autorização de voto: sem solicitação APPROVED e vínculo de contexto ativo, retorna 403.
2. A verificação de autorização ocorre no backend **antes de cada operação**; alterar a interface não contorna a regra.
3. A Fase 4 reutiliza `CoachVotingV2Service` para validação de edição, janela, time, tipo de candidato, vínculo do usuário e duplicidade de voto.
4. Para prêmios destinados a atletas, apenas equipes com `athleteEligible = true` podem ser selecionadas; o backend também valida Chave Ouro no POST.
5. Para a premiação de técnicos, mantém-se a regra já vigente: não votar na própria equipe. A elegibilidade Ouro limita **atletas**, não técnicos.
6. O comprovante de cada contexto pode ser consultado, mas não alterado. Os resultados agregados seguem restritos à administração.
7. Nenhuma migration é necessária: as tabelas das Fases 1–3 e o armazenamento de votos existente já atendem a esta etapa.

## Testes de aceitação sugeridos

- Usuário com solicitação PENDING/REJECTED não aparece em `/contexts`, nem pode acessar candidatos ou submeter voto por `contextId` manipulado.
- Treinador aprovado em Sub-11 não consegue votar em Sub-13 de outra pessoa.
- Atletas de equipes Prata/Bronze são recusados pela API, mesmo com ID de candidato válido.
- Token expirado/revogado impede novas solicitações; contexto previamente aprovado continua sujeito às regras da edição.
- Dupla submissão do mesmo contexto produz conflito, sem segundo voto gravado.
- Edição ainda não aberta ou encerrada bloqueia candidatos e submissão.
- Comprovante após envio exibe escolhas registradas e não expõe totais dos demais treinadores.

**Ordem de deploy:** backend antes do frontend. Não remover os fluxos V1/V2 legados nesta etapa; a remoção está prevista para a Fase 6.
