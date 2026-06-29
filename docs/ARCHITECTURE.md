# Architecture

## Pacotes

- `config`: propriedades seguras, clock e HTTP client.
- `common`: erros padronizados, exceptions e geracao de ids.
- `health`: health publico minimo.
- `status`: status operacional sem segredos.
- `oauth`: OAuth server-side, state store, token store e gateway Blaze.
- `blaze`: REST client Blaze, headers e endpoints internos controlados.
- `events`: abstracao de Events Socket.IO, runner, session welcome, subscriptions, log e pipeline.
- `intake`: normalizacao, deduplicacao e armazenamento de live events.
- `alert`: regras, historico, alerts ativos e avaliacao de eventos.
- `giveaway`: sorteios, entradas e draw minimo.
- `overlays`: profiles, overlays, layers, assets, runtime OBS e manifest publico.
- `dashboard`: shell HTML para SPA React e rotas legadas.

## Persistencia

O perfil `dev` usa H2 file em `./data/nollenblaze-dev`. Testes usam H2 in-memory. As tabelas sao criadas por `schema.sql` e cobrem alerts, events log, canal Blaze, live events, giveaways e overlay/config stores que foram integrados ao JDBC. Stores ainda preservam fallback in-memory para testes unitarios diretos.

## API Security

`ApiKeyFilter` protege endpoints administrativos de `/api/**` com `X-Nollen-Api-Key` ou `Authorization: Bearer`. Rotas publicas continuam liberadas:

- `GET /api/health`
- `GET /api/status`
- `GET /api/blaze/oauth/callback`
- `GET /api/public/**`
- `GET /overlay/**`

## OAuth

1. `POST /api/blaze/oauth/start` chama o gateway Blaze `generate-auth-url`.
2. O backend guarda `state` e `codeVerifier`.
3. `GET /api/blaze/oauth/callback` valida `state`, troca `code` por token e salva snapshot seguro.
4. `POST /api/blaze/oauth/refresh` usa o refresh token atual e substitui pelo novo token retornado.

Nenhum endpoint retorna token bruto.

## REST Blaze

`BlazeApiClient` centraliza chamadas para `https://api.blaze.stream/v1`. Toda chamada autenticada adiciona:

- `Authorization: Bearer <token>`
- `client-id: <clientId>`
- `content-type: application/json`

Erros HTTP viram `BlazeApiException` e chegam ao usuario como `ApiErrorResponse`.

## Events

Events nasce como abstracao segura:

- `BlazeEventsClient` define start/stop/isRunning.
- `BlazeEventsRunner` captura `session_welcome` e guarda `sessionId`.
- `EventSubscriptionService` so cria subscription quando ha `sessionId`.
- `BlazeEventsPipeline` recebe envelopes aceitos pelo runner, grava log e despacha para Live Event Intake e Alert Engine.

A conexao Socket.IO real fica pendente para fase posterior, para validar biblioteca, reconexao e limites oficiais antes de manter thread de rede em producao.

## Frontend

O frontend React fica em `frontend/`, usa Vite, lazy loading por rota e proxy `/api` para `localhost:8080` em desenvolvimento. O shell Spring mantem compatibilidade para `/`, `/dashboard`, `/alerts`, `/giveaways`, `/events`, `/channel`, `/overlays` e rotas legadas de dashboards estaticos.

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
