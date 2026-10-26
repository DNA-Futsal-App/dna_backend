# Torneio União: API e interpretação

A edição 2026 é identificada como `stage=UNIAO` **dentro** do `eventId` do Paulista
quando a fase oficial começa por `Torneio União`. Edições com título próprio
(como a histórica de 2019) são reconhecidas pelo título oficial do evento.

- `GET /api/v1/sports/structure?eventId=918`: etapas, fases, grupos e trilhas publicados.
- `GET /api/v1/sports/matches?eventId=918&stage=UNIAO&phase=...`
- `GET /api/v1/sports/standings?eventId=918&stage=UNIAO&phase=...&group=...`
- `GET /api/v1/sports/top-scorers?eventId=918&stage=UNIAO&phase=...&limit=10`
- Também existem `/api/v1/sports/matches/played`, `/upcoming` e `/pending`.

Os endpoints originais (`/api/v1/matches`, `/standings`, `/top-scorers`)
continuam com as semânticas e os formatos anteriores.
A seleção de fase/grupo é por igualdade normalizada, **nunca por substring**.

A fonte de verdade dos nomes é o scraper; o servidor não calcula vencedores
ou classificações por agregação entre categorias. Se a FPFS não identificar
explicitamente uma etapa no rótulo da fase, os dados ficam conservadoramente
fora do recorte União até o parser disponibilizar tal distinção. Não inferir
chaves a partir da participação de um mesmo time em fases distintas.

Ordem recomendada: scraper → backend → frontend. Validar a estrutura dos
`eventId` reais de 2026, depois testar navegação e comparação com a FPFS.
