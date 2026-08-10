# Contratos das integrações

O backend depende de duas APIs externas. Os caminhos podem ser adaptados dentro dos gateways HTTP sem alterar controllers ou casos de uso.

## API esportiva / scraper

Configuração:

- `SPORTS_API_BASE_URL`
- `SPORTS_API_KEY`, enviado como `X-Api-Key` quando preenchido

Endpoints esperados:

| Método | Caminho | Parâmetros |
| --- | --- | --- |
| `GET` | `/api/v1/catalog/categories` | — |
| `GET` | `/api/v1/catalog/divisions` | `categoryId` |
| `GET` | `/api/v1/catalog/teams` | `categoryId`, `divisionId` |
| `GET` | `/api/v1/matches/played` | `categoryId`, `divisionId`, `teamId?` |
| `GET` | `/api/v1/matches/upcoming` | `categoryId`, `divisionId`, `teamId?` |
| `GET` | `/api/v1/standings` | `categoryId`, `divisionId`, `teamId?` |
| `GET` | `/api/v1/top-scorers` | `categoryId`, `divisionId`, `teamId?` |

Exemplo de partida:

```json
{
  "id": "match-123",
  "competitionName": "Campeonato Metropolitano",
  "categoryId": "sub-13",
  "categoryName": "Sub-13",
  "divisionId": "especial",
  "divisionName": "Divisão Especial",
  "round": "5ª rodada",
  "homeTeam": {
    "id": "corinthians",
    "name": "Sport Club Corinthians Paulista",
    "shortName": "Corinthians",
    "logoUrl": "https://cdn.example.com/corinthians.png"
  },
  "awayTeam": {
    "id": "magnus",
    "name": "Magnus Futsal",
    "shortName": "Magnus",
    "logoUrl": "https://cdn.example.com/magnus.png"
  },
  "homeScore": 3,
  "awayScore": 2,
  "scheduledAt": "2026-08-08T18:00:00Z",
  "status": "FINISHED",
  "venue": "Ginásio A"
}
```

Exemplo de classificação:

```json
{
  "position": 1,
  "team": { "id": "corinthians", "name": "Corinthians", "shortName": "COR", "logoUrl": null },
  "played": 8,
  "wins": 7,
  "draws": 1,
  "losses": 0,
  "goalsFor": 35,
  "goalsAgainst": 12,
  "goalDifference": 23,
  "points": 22
}
```

Exemplo de artilharia:

```json
{
  "position": 1,
  "athleteId": "athlete-88",
  "athleteName": "Atleta Exemplo",
  "team": { "id": "corinthians", "name": "Corinthians", "shortName": "COR", "logoUrl": null },
  "goals": 14,
  "matches": 8
}
```

### Notificação de jogo concluído

Depois de persistir um jogo como encerrado e recalcular os dados, o scraper chama:

```http
POST /api/v1/internal/sports/match-completed
X-Internal-Api-Key: <INTERNAL_API_KEY>
Content-Type: application/json
```

```json
{
  "eventId": "b1304767-66fa-4300-b680-a7d0d62514b1",
  "categoryId": "sub-13",
  "divisionId": "especial",
  "teamIds": ["corinthians", "magnus"],
  "occurredAt": "2026-08-08T19:40:00Z"
}
```

O `eventId` deve permanecer igual em retries. O scraper só deve considerar a entrega concluída após resposta `202`.

## API de notícias

Configuração:

- `NEWS_API_BASE_URL`
- `NEWS_API_KEY`, enviado como `X-Api-Key` quando preenchido

Endpoints esperados:

- `GET /api/v1/public/news?page=0&size=20&sort=publishedAt,desc`
- `GET /api/v1/public/news/{slug}`

Página esperada:

```json
{
  "content": [
    {
      "id": "news-1",
      "slug": "rodada-sub-13",
      "title": "Resumo da rodada do Sub-13",
      "summary": "Os principais resultados da rodada.",
      "content": "<p>Conteúdo sanitizado pela aplicação editorial.</p>",
      "coverImageUrl": "https://cdn.example.com/news-1.jpg",
      "authorName": "Redação DNA Futsal",
      "publishedAt": "2026-08-08T20:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

A aplicação editorial é responsável por retornar apenas conteúdo `PUBLISHED` e por sanitizar HTML. A API DNA Futsal não recebe credenciais do banco editorial e não conhece seu schema.
