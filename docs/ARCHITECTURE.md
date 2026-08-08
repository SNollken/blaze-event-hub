# Architecture

## Pacotes

- `config`: propriedades seguras (`ApiSecurityProperties`, `BlazeProperties`), clock e HTTP client.
- `common`: erros padronizados (`ApiErrorResponse`), exceptions, `GlobalExceptionHandler`, geracao de ids, `JsonData` e filtros (`ApiKeyFilter`, `BrowserSecurityFilter`, `RateLimitFilter`).
- `health`: health publico minimo.
- `status`: status operacional sem segredos.
- `oauth`: OAuth server-side, state store, token store, profile sync e gateway Blaze.
- `blaze`: REST client Blaze, headers e endpoints internos controlados.
- `events`: abstracao de Events, runner, session welcome, subscriptions, log, pipeline e simulacao.
- `intake`: normalizacao, deduplicacao e armazenamento de live events.
- `alert`: regras, historico, alerts ativos e avaliacao de eventos.
- `giveaway`: sorteios, entradas e draw minimo.
- `channel`: configuracao do canal Blaze monitorado (`BlazeChannelConfig`).
- `setup`: endpoint `GET /api/blaze/setup` com flags, checklist, scopes recomendados, proximos passos e links oficiais — nunca segredos.
- `overlays`: profiles, overlays, layers, assets, runtime OBS e manifest publico.
- `dashboard`: shell HTML provisorio (MVP) servido nas rotas da SPA e rotas legadas.

## Persistencia

O perfil `dev` usa H2 file em `./data/nollenblaze-dev` (MODE=PostgreSQL). Testes usam H2 in-memory, e ha testes de integracao com PostgreSQL real via Testcontainers. As tabelas sao criadas por `schema.sql` (`spring.sql.init.mode=always`) e cobrem: alert_rules, alerts, blaze_events_log, blaze_channels, event_subscriptions, live_events, giveaways, giveaway_entries, overlay_profiles, overlays, overlay_asset_bytes e runtime_overlay_configs. Stores JDBC preservam fallback in-memory para testes unitarios diretos.

Nota: `db/migration/common/V11__add_runtime_overlay_configs.sql` e um artefato historico da epoca em que o branch de producao usava Flyway. Este branch nao tem dependencia Flyway; o schema vem do `schema.sql`, que ja contem a tabela `runtime_overlay_configs`. O arquivo foi mantido apenas como referencia de DDL.

## API Security

`ApiKeyFilter` protege endpoints administrativos de `/api/**` com `X-Nollen-Api-Key` ou `Authorization: Bearer *** (comparacao em tempo constante via `MessageDigest.isEqual`). Rotas publicas continuam liberadas:

- `GET /api/health`
- `GET /api/status`
- `GET /api/blaze/oauth/callback`
- `GET /api/public/**`
- `GET /overlay/**`
- `GET /assets/**` e `/vite.svg` (assets estaticos do build da SPA)

`BrowserSecurityFilter` adiciona headers de seguranca: CSP, X-Frame-Options DENY, X-Content-Type-Options nosniff, Referrer-Policy, Permissions-Policy e Cache-Control no-store para `/api/`. `RateLimitFilter` aplica janela deslizante de 1 minuto por IP em `POST /api/blaze/oauth/*` (429 + Retry-After; configuravel via `NOLLEN_RATE_LIMIT_PER_MINUTE`, default 30).

## OAuth

1. `POST /api/blaze/oauth/start` chama o gateway Blaze `generate-auth-url`.
2. O backend guarda `state` e `codeVerifier`.
3. `GET /api/blaze/oauth/callback` valida `state`, troca `code` por token e salva snapshot seguro.
4. `POST /api/blaze/oauth/refresh` usa o refresh token atual e substitui pelo novo token retornado (refresh concorrente serializado por lock local com re-check de token).

Nenhum endpoint retorna token bruto.

## REST Blaze

`BlazeApiClient` centraliza chamadas para `https://api.blaze.stream/v1`. Toda chamada autenticada adiciona:

- `Authorization: Bearer ***
- `client-id: <clientId>`
- `content-type: application/json`

Erros HTTP viram `BlazeApiException` e chegam ao usuario como `ApiErrorResponse`.

## Events

Events nasce como abstracao segura:

- `BlazeEventsClient` define start/stop/isRunning.
- `BlazeEventsRunner` captura `session_welcome` e guarda `sessionId`.
- `EventSubscriptionService` so cria subscription quando ha `sessionId` (sync atomico via `@Transactional`).
- `BlazeEventsPipeline` recebe envelopes aceitos pelo runner, grava log e despacha para Live Event Intake e Alert Engine (dedupKey por sessao + tipo de mensagem).
- `POST /api/blaze/events/simulate` permite testar o pipeline sem conexao real.

A implementacao atual e `NoopBlazeEventsClient`. A conexao Socket.IO real fica pendente para fase posterior, para validar biblioteca, reconexao e limites oficiais antes de manter thread de rede em producao.

## Frontend

O frontend React fica em `frontend/` (Vite + TypeScript + Tailwind v4, i18n pt-BR/EN, lazy loading por rota e proxy `/api` para `localhost:8080` em desenvolvimento). Rotas da SPA: `/` (Dashboard), `/events`, `/blaze` (Blaze Channel), `/alerts`, `/giveaways` e `/overlays`; `/channel` e rota legada que redireciona para `/blaze`.

O `DashboardController` serve o shell HTML provisorio (`static/dashboard.html`) para `/`, `/dashboard`, `/events`, `/blaze`, `/channel`, `/alerts`, `/giveaways` e `/overlays`; paginas legadas `/live-events` e `/alerts-dashboard` tambem servem o shell. Em producao o build da SPA e servido pelo pipeline de deploy (Render) — ver "Decisoes pendentes".

## Overlays

Perfil e grupo. Overlay e runtime independente:

- cada overlay tem `publicToken`;
- updates preservam `publicToken`;
- layers pertencem a uma unica overlay;
- assets pertencem a uma unica overlay;
- manifest publico e servido por `publicToken`;
- overlay desativada retorna manifest seguro e vazio.

Nao existe endpoint `/overlay/live` como arquitetura principal.

## Decisoes pendentes

- Storage definitivo de tokens OAuth com criptografia em repouso.
- Persistencia de assets binarios fora do H2 dev.
- Cliente Socket.IO real e politica de reconexao.
- Teste E2E contra blaze.stream com credenciais reais.
- Reconciliacao do deploy de producao (Render) com este branch — o build live atual nao e reproduzivel a partir do repo (ver TODO.md, rodada 20).
