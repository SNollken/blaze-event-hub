# Architecture

## Pacotes

- `config`: propriedades seguras, clock e HTTP client.
- `common`: erros padronizados, exceptions e geracao de ids.
- `health`: health publico minimo.
- `status`: status operacional sem segredos.
- `oauth`: OAuth server-side, state store, token store e gateway Blaze.
- `blaze`: REST client Blaze, headers e endpoints internos controlados.
- `events`: abstracao de Events Socket.IO, runner, session welcome e subscriptions.
- `overlays`: profiles, overlays, layers, assets, repository in-memory e manifest publico.

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

A conexao Socket.IO real fica pendente para fase posterior, para validar biblioteca, reconexao e limites oficiais antes de manter thread de rede em producao.

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

- Storage definitivo de tokens com criptografia em repouso.
- Persistencia real de overlays e assets.
- Cliente Socket.IO real e politica de reconexao.
- Teste E2E contra blaze.stream com credenciais reais.
