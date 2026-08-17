# Contratos das integrações

O backend depende de duas APIs externas e as consome sem headers de autenticação.

## API esportiva / scraper

Configuração:

- `SPORTS_API_BASE_URL`, padrão `https://dna-scrapper.onrender.com`
- `SPORTS_CONNECT_TIMEOUT`, padrão `PT5S`
- `SPORTS_READ_TIMEOUT`, padrão `PT60S`
- `SPORTS_EVENT_CACHE_TTL`, padrão `PT1H`
- `SPORTS_TEAM_CACHE_TTL`, padrão `PT1H`
- `SPORTS_SNAPSHOT_CACHE_TTL`, padrão `PT10M`
- `SPORTS_CACHE_MAX_ENTRIES`, padrão `2000` por cache
- `SPORTS_INCLUDE_PERSONAL_DATA`, padrão `false`

Rotas consumidas no scraper:

| Método | Caminho | Uso |
| --- | --- | --- |
| `GET` | `/api/v1/events/search` | Pesquisa por temporada, título, divisão e categoria |
| `GET` | `/api/v1/events/{eventId}` | Metadados da competição |
| `GET` | `/api/v1/events/{eventId}/teams` | Equipes participantes |
| `GET` | `/api/v1/events/{eventId}/snapshot` | Jogos, classificação, equipes e artilharia |
| `GET` | `/api/v1/events/{eventId}/scorers` | Nomes de atletas, somente quando habilitados |

O backend converte o snapshot do scraper para seu contrato próprio. Nomes de equipes são normalizados para recuperar `teamId`, jogos são separados entre `FINISHED` e `SCHEDULED`, datas locais são convertidas usando `APP_TIMEZONE` e métricas ausentes permanecem nulas.

Exemplo normalizado de partida:

```json
{
  "id": "12345",
  "eventId": 917,
  "competitionName": "Campeonato Paulista",
  "season": 2026,
  "category": "Principal",
  "division": "A1",
  "phase": "1ª Fase",
  "homeTeam": {
    "id": "10970",
    "name": "Time A",
    "shortName": null,
    "logoUrl": "https://eventos.admfutsal.com.br/time-a.png"
  },
  "awayTeam": {
    "id": "10971",
    "name": "Time B",
    "shortName": null,
    "logoUrl": "https://eventos.admfutsal.com.br/time-b.png"
  },
  "homeScore": 3,
  "awayScore": 2,
  "scheduledAt": "2026-04-10T22:30:00Z",
  "status": "FINISHED",
  "walkover": false,
  "venue": "Ginásio A",
  "matchSheetUrl": "https://admfutsal.com.br/sumula_online/sumula_imprimir.php?id_jogo=12345"
}
```

A atualização ocorre exclusivamente pelos TTLs do cache Caffeine local. As entradas não são compartilhadas
entre instâncias e são descartadas quando a aplicação reinicia; a consulta seguinte recarrega os dados do scraper.

### Diagnóstico de falhas

Toda resposta de erro contém `requestId`, também devolvido no header `X-Request-ID`. Use esse valor para localizar o log correspondente, que registra a operação do scraper, `eventId` ou temporada, status HTTP externo e tipos das exceções, sem registrar o corpo da resposta externa.

| Situação | Status da API | Código |
| --- | --- | --- |
| Filtro rejeitado pelo scraper | `400` | `SPORTS_FILTER_INVALID` |
| Competição inexistente | `404` | `SPORTS_EVENT_NOT_FOUND` |
| Payload ou JSON inválido | `502` | `SPORTS_DATA_INVALID` |
| Acesso recusado | `503` | `SPORTS_API_ACCESS_DENIED` |
| Limite externo atingido | `503` | `SPORTS_API_RATE_LIMITED` |
| Conexão ou serviço externo indisponível | `503` | `SPORTS_API_CONNECTION_FAILED` ou `SPORTS_DATA_UNAVAILABLE` |
| Timeout externo | `504` | `SPORTS_API_TIMEOUT` |

## API de notícias

Configuração:

- `NEWS_API_BASE_URL`

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
