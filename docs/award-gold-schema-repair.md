# Prêmio DNA — diagnóstico e recuperação do schema da Chave Ouro

Este ajuste **complementa** a Fase 1. A V26 continua imutável.
A nova V27 cria **somente se ausentes** as tabelas `award_gold_eligibility` e
`award_gold_eligible_teams`. Se já existirem, não as recria nem apaga dados.

## Antes de concluir que o banco está quebrado

O projeto já cria `award_candidates` e `award_editions` na **V4**. Se essas
tabelas também estiverem ausentes, o problema não é a V26: verifique a
conexão com o banco e o schema, sem executar scripts de criação manual.

No mesmo banco utilizado por `DATABASE_URL`, execute **somente consultas**:

```sql
SELECT current_database() AS banco, current_schema() AS schema_atual,
       current_setting('search_path') AS search_path;

SELECT to_regclass('award_editions') AS award_editions,
       to_regclass('award_candidates') AS award_candidates,
       to_regclass('award_gold_eligibility') AS award_gold_eligibility,
       to_regclass('award_gold_eligible_teams') AS award_gold_eligible_teams,
       to_regclass('flyway_schema_history') AS flyway_schema_history;
```

Se `flyway_schema_history` existir, verifique:

```sql
SELECT installed_rank, version, description, success
  FROM flyway_schema_history
 WHERE version IN ('4', '26', '27')
 ORDER BY installed_rank;
```

## Procedimento de aplicação

1. Confirme que a migration **V26** entregue na Fase 1 está presente no
   diretório `src/main/resources/db/migration`. Não edite a V26 se ela
   já tiver sido aplicada: o Flyway valida o checksum das migrations.
2. Aplique o patch complementar ao `dna_backend`, que adiciona a **V27**.
3. Faça backup do banco antes de qualquer implantação.
4. Inicie o backend com o perfil e banco corretos. No `application-prod.yml`
   do projeto, `spring.flyway.enabled: true` já está habilitado; a V27
   deve ser executada automaticamente no início da aplicação.
5. Repita as consultas acima: V26 e V27 devem ter `success = true`, e as
   duas novas tabelas devem aparecer na conexão utilizada pela API.

**Importante:** se o IntelliJ apenas marcar `Cannot resolve table` nas
strings SQL, mas a aplicação executar normalmente, isso pode ser inspeção
estática da IDE. Associe o arquivo SQL a um data source PostgreSQL com o
schema correto e sincronize o banco no painel Database. Uma migration não
atualiza automaticamente a introspecção da IDE.

**Não use** `flyway repair`, `flyway clean`, `DROP TABLE` ou
`spring.jpa.hibernate.ddl-auto=create` como tentativa de conserto: esses
comandos podem ocultar divergências ou destruir dados.

A V27 não restaura linhas eventualmente perdidas se as tabelas tiverem
sido apagadas. Nesse caso, recupere o backup e confira os candidatos
preparados antes de reabrir qualquer votação.
