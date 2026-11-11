# Prêmio DNA — Fase 1: elegibilidade da Chave Ouro

O backend exige uma relação **preparada e congelada por edição + evento + divisão + categoria** antes de listar atletas ou aceitar votos em categorias do tipo `ATHLETE`. A restrição é aplicada tanto em `coach-voting` quanto em `coach-voting-v2`, inclusive na submissão direta de cédulas.

## Regras

- A origem é o snapshot oficial do evento (`SportsSnapshotService`) e o catálogo de equipes (`SportsCatalogService`).
- Somente equipes vinculadas a `Chave Ouro` ou `Grupo Ouro`, explicitamente identificadas em classificação ou fase das partidas, são candidatas. Não se presume que `Grupo Único`, `Grupo A` ou grupos sem identificação sejam Chave Ouro.
- Se o mesmo clube aparecer em Ouro e Prata/Bronze no mesmo estágio, ou houver identificador de equipe ausente ou desconhecido, a preparação é bloqueada para revisão.
- Os estágios `PAULISTA` e `UNIAO` são tratados separadamente. A edição/temporada e os IDs de divisão, categoria e evento são conferidos antes da preparação.
- A operação de preparação só é aceita enquanto a edição está em `DRAFT`. Todos os elencos elegíveis são importados na mesma transação: se um elenco estiver vazio ou indisponível, a preparação inteira sofre rollback.
- A relação de times fica imutável. Em votação aberta, as consultas de atletas usam os candidatos importados; não atualizam automaticamente o elenco a cada consulta.
- O atributo `athleteEligible` em cada equipe do contexto de votação indica ao frontend quais clubes podem ser selecionados para categorias de premiação do tipo `ATHLETE`. O frontend atual pode ainda exibir clubes inelegíveis na lista, mas a API os rejeita. A adequação visual completa pertence à fase de interface.

## Procedimento administrativo

Requer usuário administrador autenticado, com permissão de acesso às rotas `/api/v1/admin/awards/**`.

1. Pré-visualizar (somente leitura):
   `GET /api/v1/admin/awards/editions/{editionId}/gold-eligibility/preview?eventId=904&divisionId=3&categoryId=7&stage=PAULISTA`
2. Conferir `teams`, `ambiguousTeamIds` e `unresolvedTeams`. Não preparar a categoria se houver anomalias ou se a lista estiver incompleta.
3. Preparar, ainda no estado `DRAFT`:
   `POST /api/v1/admin/awards/editions/{editionId}/gold-eligibility`
   com JSON `{ "eventId": 904, "divisionId": 3, "categoryId": 7, "stage": "PAULISTA" }`.
4. Conferir a relação imutável e a quantidade de atletas por equipe:
   `GET /api/v1/admin/awards/editions/{editionId}/gold-eligibility?eventId=904&divisionId=3&categoryId=7`
5. Repetir para cada categoria e divisão que serão abertas à votação dos técnicos.

**Atenção ao deploy:** quando a Fase 1 estiver ativa, o backend rejeitará votos em atletas para categorias ainda não preparadas (`AWARD_GOLD_NOT_PREPARED`). Não publicar em plena votação aberta sem plano de transição. A migração V26 **somente cria tabelas**; ela não apaga inscrições, votos ou candidatos anteriores.

## Validação

- `git apply --check dna_futsal_fase1_chave_ouro_backend.patch`
- `git apply dna_futsal_fase1_chave_ouro_backend.patch`
- `./mvnw test` (se houver Maven Wrapper) ou `mvn test`, com PostgreSQL de teste configurado conforme o projeto.

Este patch altera somente `dna_backend`. O `dna_scrapper` continua sendo a fonte oficial existente. A interface definitiva de votação e os links individuais serão tratados nas próximas fases.
