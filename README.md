# NollenBlaze

Backend limpo do NollenBlaze para integracao server-side com blaze.stream, OAuth, REST APIs, Events e overlays publicos por manifesto.

## Stack

- Java 21
- Spring Boot 3.5.3
- Maven Wrapper
- Spring Web, Validation, Actuator
- RestClient para HTTP
- Storage inicial in-memory

## Como rodar

Use Java 21 apenas na sessao atual:

```powershell
$env:JAVA_HOME='C:\Users\sofia\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean compile
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Use as variaveis de `.env.example` como referencia se for testar Blaze real. Nao exponha valores reais em docs, logs, vault, prints ou respostas publicas.

## MVP 1 - Painel provisorio

O MVP 1 inclui uma tela funcional minima servida pelo proprio Spring Boot. Ela existe para testar fluxo, estados e endpoints; o design final sera feito depois no OpenDesign/opencode.

- Tela inicial: `http://localhost:8080/`
- Dashboard: `http://localhost:8080/dashboard`
- Health usado pela tela: `GET /api/health`
- Status seguro usado pela tela: `GET /api/status`
- Events usados pela tela: `GET /api/blaze/events/status`, `POST /api/blaze/events/start`, `POST /api/blaze/events/stop`, `POST /api/blaze/events/subscriptions/sync`
- OAuth usado pela tela: `POST /api/blaze/oauth/start`, `GET /api/blaze/oauth/session`, `POST /api/blaze/oauth/refresh`, `POST /api/blaze/oauth/disconnect`
- Overlays usados pela tela: `GET /api/overlay-profiles`, `GET /api/overlay-profiles/{profileId}/overlays`, `GET /api/public/overlays/{publicToken}/manifest`

A tela mostra apenas flags e respostas sanitizadas. Ela nao deve exibir `clientSecret`, `accessToken`, `refreshToken` ou valores reais de credenciais.

## MVP 2 - Configuracao assistida da Blaze

O MVP 2 adiciona uma area de configuracao no mesmo dashboard provisorio e o endpoint seguro `GET /api/blaze/setup`. O objetivo e orientar App Setup, OAuth, scopes, canal monitorado e Events sem exigir credenciais reais no repositorio.

- Endpoint de setup: `GET /api/blaze/setup`
- Botao para copiar Redirect URI: usa o valor configurado no backend e nao deve ser registrado em logs ou relatorios.
- Botao para copiar scopes atuais: `users.read,offline.access`
- Botao para copiar um exemplo `.env` com placeholders
- Botao `Iniciar OAuth`, que continua falhando com erro amigavel quando `BLAZE_CLIENT_ID`, `BLAZE_CLIENT_SECRET` ou `BLAZE_REDIRECT_URI` nao estiverem configurados
- Links oficiais usados pela tela: App Setup, OAuth, Scopes e Events em `https://dev.blaze.stream/docs`

O contrato de `/api/blaze/setup` devolve flags, itens de checklist, scopes recomendados, proximos passos, links oficiais e valores mascarados. Ele nao devolve nomes ou valores de `clientSecret`, `accessToken`, `refreshToken`, `codeVerifier` nem variantes snake_case desses campos.

Para o proximo MVP de OAuth/perfil, use privilegio minimo:

```env
BLAZE_SCOPES=users.read,offline.access
```

Scopes como `channel.moderate` e `users.bot` ficam reservados para fases futuras de chat/moderacao/bot, depois de haver necessidade real.

Se `/` ou `/dashboard` retornar 500, confirme primeiro se a branch ativa e `dev`, se o app foi reiniciado depois do checkout e se `src/main/resources/static/dashboard.html` esta empacotado. O smoke minimo do dashboard deve validar:

```powershell
Invoke-WebRequest http://localhost:8080/ -UseBasicParsing
Invoke-WebRequest http://localhost:8080/dashboard -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/setup -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/oauth/session -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/events/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/overlay-profiles -UseBasicParsing
```

## OAuth produto

O modulo OAuth finaliza a experiencia de conta conectada no dashboard provisorio:

- `GET /api/blaze/oauth/session`: retorna flags seguras de sessao, perfil seguro, datas e proxima acao recomendada.
- `GET /api/blaze/oauth/callback`: retorna HTML amigavel no navegador e JSON seguro quando `Accept: application/json`.
- `POST /api/blaze/oauth/refresh`: renova a sessao com a credencial de renovacao local e preserva a credencial anterior se a Blaze nao devolver outra.
- `POST /api/blaze/oauth/disconnect`: limpa token, resumo de perfil e states pendentes neste backend local.
- Dashboard: bloco `Conta Blaze` com estados desconectado, conectado sem perfil, conectado com perfil, atualizar sessao e desconectar.

Depois do token exchange, o backend tenta chamar `GET /v1/users/profile` na Blaze com `users.read` para salvar apenas um resumo seguro: `id`, `username`, `displayName`, `avatarUrl` e data de sincronizacao. Payload bruto, headers e tokens nao sao persistidos nem expostos.

Limites atuais: storage in-memory, sem revoke remoto confirmado e sem criptografia definitiva de token. Esses pontos ficam para persistencia segura.

## Endpoints principais

- `GET /api/health`
- `GET /api/status`
- `GET /api/blaze/setup`
- `POST /api/blaze/oauth/start`
- `GET /api/blaze/oauth/callback`
- `GET /api/blaze/oauth/session`
- `POST /api/blaze/oauth/refresh`
- `POST /api/blaze/oauth/disconnect`
- `GET /api/blaze/users/profile`
- `GET /api/blaze/channels?slug=...`
- `GET /api/blaze/chats/messages?channelId=...`
- `POST /api/blaze/chats/messages`
- `GET /api/blaze/events/status`
- `POST /api/blaze/events/start`
- `POST /api/blaze/events/stop`
- `POST /api/blaze/events/subscriptions/sync`
- `GET /api/overlay-profiles`
- `POST /api/overlay-profiles`
- `GET /api/overlay-profiles/{profileId}/overlays`
- `POST /api/overlay-profiles/{profileId}/overlays`
- `GET /api/overlays/{overlayId}`
- `PUT /api/overlays/{overlayId}`
- `DELETE /api/overlays/{overlayId}`
- `GET /api/overlays/{overlayId}/layers`
- `POST /api/overlays/{overlayId}/layers`
- `PUT /api/overlays/{overlayId}/layers/{layerId}`
- `DELETE /api/overlays/{overlayId}/layers/{layerId}`
- `POST /api/overlays/{overlayId}/assets`
- `GET /api/public/overlays/{publicToken}/manifest`

## Arquitetura de overlays

Perfil e apenas um grupo organizacional. Overlay e a entidade de runtime, tem `publicToken` unico, config, layers e assets. O runtime publico consome `GET /api/public/overlays/{publicToken}/manifest`.

Overlay desativada retorna manifesto seguro com `enabled=false` e listas vazias. Atualizacoes comuns preservam o `publicToken`.

## Blaze OAuth, REST e Events

OAuth e server-side. Client credential, authorization code, state real, PKCE verifier, access token e refresh token ficam apenas no backend. O token store inicial e in-memory e troca refresh token de forma atomica ao receber um novo snapshot.

REST usa `RestClient`, header `client-id`, bearer token e tratamento explicito para erros HTTP da Blaze.

Events esta preparado como abstracao segura. A implementacao atual nao abre Socket.IO real; ela oferece runner, status, captura de `session_welcome`, `sessionId` e sincronizacao de subscriptions in-memory para uma proxima fase com cliente Socket.IO Java validado.

## Testes

```powershell
.\mvnw.cmd test
```

A suite cobre health/status, propriedades seguras, OAuth, sessao conectada, profile seguro, refresh, disconnect, REST client, Events e overlays.

## Proximos passos

- Persistir tokens e overlays em banco seguro.
- Integrar cliente Socket.IO real depois de validar biblioteca e reconexao.
- Fazer smoke E2E de Events real com credenciais Blaze reais fora do repositorio.
- Evoluir o dashboard/frontend final em fase separada.
