# DNA Futsal Backend

Backend do aplicativo DNA Futsal, implementado como monólito modular com Java 17 e Spring Boot 4.1.0. O projeto cobre cadastro e confirmação de e-mail, login por e-mail ou telefone, sessões JWT, perfil protegido por senha, redefinição de senha, dados esportivos vindos do scraper, notícias de uma aplicação externa, Redis e entrega de e-mail tolerante a falhas.

## Decisões de arquitetura

O MVP é mantido em um único deploy, mas o código está separado em quatro módulos de negócio:

- `identity`: cadastro, confirmação, login, sessões, perfil e senha;
- `sports`: catálogo, jogos, classificação, artilharia e atualização do cache;
- `news`: fachada HTTP para a aplicação que publica as notícias;
- `mail`: outbox transacional, Brevo e fallback SMTP.

Essa divisão evita a complexidade operacional de microsserviços para uma equipe de uma pessoa e mantém portas claras para extrair um módulo no futuro. O backend **não acessa diretamente o banco da aplicação de notícias**: o contrato HTTP impede acoplamento de schema, credenciais compartilhadas e deploys coordenados.

```mermaid
flowchart TD
    App["App mobile / web"] --> API["DNA Futsal API"]
    API --> PG[(PostgreSQL)]
    API --> Redis[(Redis)]
    API --> Scraper["API de scraping"]
    API --> News["API de notícias"]
    API --> Outbox["Outbox de e-mail"]
    Outbox --> Brevo["Brevo"]
    Outbox --> SMTP["SMTP fallback"]
```

## Requisitos implementados

- Cadastro obrigatório com nome, e-mail, telefone e senha; Instagram do filho, categoria, divisão e time são opcionais.
- Confirmação obrigatória por e-mail antes do primeiro login.
- Login com e-mail **ou** telefone e senha.
- Access token JWT de 15 minutos e refresh token rotativo de 30 dias.
- Perfil em cache e edição integral mediante confirmação da senha atual.
- Troca de e-mail exige nova confirmação e revoga as sessões existentes.
- Redefinição de senha por link de uso único, com no máximo três solicitações por usuário/dia no fuso `America/Sao_Paulo`.
- Respostas genéricas no pedido de redefinição para não revelar quais usuários existem.
- Cache Redis para perfil, versão de segurança, partidas, classificação e artilharia.
- Endpoints separados para partidas encerradas e futuras.
- Preferências do perfil usadas quando o front não envia filtros esportivos.
- Webhook interno idempotente para atualizar snapshots somente após jogo concluído.
- Proxy autenticado da aba de notícias para outra aplicação/banco.
- Brevo como provedor primário, SMTP como fallback e outbox com até dez tentativas e backoff.
- Flyway, health checks, métricas Prometheus, CORS restritivo e erros em `application/problem+json`.

### O que não vai para o Redis

Senha, hash de senha, token de confirmação e token de redefinição não são cacheados. “Cachear credenciais” aumentaria o impacto de um vazamento e não melhora o desenho de autenticação. O Redis guarda somente o perfil não secreto, a versão/status usada para revogar JWTs e contadores de proteção contra força bruta. Tokens de uso único são persistidos no PostgreSQL somente como SHA-256; refresh tokens também são persistidos somente como hash.

## Endpoints

| Método | Caminho | Acesso | Finalidade |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Público | Cadastrar conta pendente |
| `GET` | `/api/v1/auth/email-verification/confirm?token=` | Público | Confirmar e-mail |
| `POST` | `/api/v1/auth/email-verification/resend` | Público | Reenviar confirmação pendente |
| `POST` | `/api/v1/auth/login` | Público | Login por e-mail ou telefone |
| `POST` | `/api/v1/auth/refresh` | Público | Rotacionar refresh token |
| `POST` | `/api/v1/auth/logout` | Público | Revogar refresh token |
| `POST` | `/api/v1/auth/password-reset/request` | Público | Solicitar alteração de senha |
| `POST` | `/api/v1/auth/password-reset/confirm` | Público | Confirmar nova senha |
| `GET`, `PUT` | `/api/v1/me` | Bearer | Consultar/alterar perfil |
| `GET` | `/api/v1/public/catalog/categories` | Público | Categorias disponíveis |
| `GET` | `/api/v1/public/catalog/divisions` | Público | Divisões da categoria |
| `GET` | `/api/v1/public/catalog/teams` | Público | Times da categoria/divisão |
| `GET` | `/api/v1/matches/played` | Bearer | Jogos já encerrados |
| `GET` | `/api/v1/matches/upcoming` | Bearer | Jogos futuros |
| `GET` | `/api/v1/standings` | Bearer | Classificação |
| `GET` | `/api/v1/top-scorers` | Bearer | Artilharia |
| `GET` | `/api/v1/news` | Bearer | Notícias publicadas |
| `GET` | `/api/v1/news/{slug}` | Bearer | Notícia completa |
| `POST` | `/api/v1/internal/sports/match-completed` | `X-Internal-Api-Key` | Atualizar cache após jogo |

O contrato detalhado está em [`docs/openapi.yaml`](docs/openapi.yaml). Os formatos esperados das APIs de scraping e notícias estão em [`docs/integrations.md`](docs/integrations.md).

## Executar localmente

Pré-requisitos: JDK 17+, Docker e Docker Compose.

```bash
cp .env.example .env
docker compose up -d postgres redis mailpit
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Mailpit: `http://localhost:8025`

Para executar tudo em containers:

```bash
docker compose --profile app up --build
```

O perfil `prod` impede a inicialização se `JWT_SECRET` ou `INTERNAL_API_KEY` continuarem com os valores inseguros de exemplo.

## Testes e build

```bash
./mvnw test
./mvnw clean package
```

Os testes unitários cobrem normalização de telefone, tokens opacos, fallback de e-mail e preservação do snapshot esportivo quando o scraper falha. O teste de integração usa Testcontainers/PostgreSQL e valida Flyway versus entidades; ele é ignorado automaticamente quando Docker não está disponível.

## Política do cache esportivo

1. A primeira consulta de uma combinação categoria/divisão/time cria o snapshot inicial.
2. Leituras seguintes usam o mesmo snapshot sem TTL.
3. Ao terminar uma partida, o scraper envia um `eventId` único ao endpoint interno.
4. O backend busca **todos** os conjuntos novos antes de substituir o cache.
5. Se a origem falhar, o snapshot anterior permanece disponível.
6. Eventos repetidos são ignorados pela chave primária em `processed_sports_events`.

Essa política atende à atualização pós-jogo. Porém, alteração de horário, W.O., punição ou correção da federação também muda dados fora do fim de uma partida. Antes da produção, o scraper deve emitir o mesmo tipo de evento para essas correções ou o contrato deve ganhar eventos específicos; depender literalmente apenas de “jogo concluído” cria dados stale inevitáveis.

## E-mail e recuperação de desastre

Cada e-mail nasce na mesma transação do cadastro ou da redefinição de senha. O dispatcher tenta Brevo e, se ele falhar, tenta SMTP imediatamente. Quando os dois falham, a outbox agenda novas tentativas com backoff exponencial; após o limite, a mensagem fica `DEAD` para alerta e intervenção.

Em produção, Brevo e SMTP devem ser infraestruturas independentes. Configurar como fallback outro caminho que usa o mesmo domínio, conta ou relay elimina boa parte da tolerância a falhas.

## Segurança e LGPD

- Use TLS em todas as conexões externas e Redis/PostgreSQL privados.
- Rotacione `JWT_SECRET`, chaves internas e credenciais de provedores por secret manager.
- O Instagram do filho é dado pessoal de menor. Ele não aparece em endpoints esportivos nem em logs, mas a base legal, consentimento verificável do responsável, retenção e exclusão precisam ser definidos com jurídico antes do lançamento.
- O conteúdo de e-mails pendentes contém links temporários e fica no banco somente até envio ou esgotamento das tentativas; restrinja acesso à tabela `mail_outbox`.
- Configure alertas para mensagens `DEAD`, falhas da API esportiva, falhas da API de notícias e crescimento de respostas `401/429`.

## Próximas fronteiras do produto

O contexto mais amplo do DNA Futsal ainda prevê notificações configuráveis, marketplace, vídeos e painel administrativo. Eles não foram misturados nesta entrega porque exigem decisões próprias de domínio (provedor push, catálogo/pagamento, moderação e papéis administrativos). A estrutura modular atual permite adicioná-los sem reescrever autenticação e esportes.
