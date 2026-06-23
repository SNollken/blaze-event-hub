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

Copie os nomes de variaveis de `.env.example` para um `.env` local se for testar Blaze real. Nunca commite `.env`.

## MVP 1 - Painel provisorio

O MVP 1 inclui uma tela funcional minima servida pelo proprio Spring Boot. Ela existe para testar fluxo, estados e endpoints; o design final sera feito depois no OpenDesign/opencode.

- Tela inicial: `http://localhost:8080/`
- Dashboard: `http://localhost:8080/dashboard`
- Health usado pela tela: `GET /api/health`
- Status seguro usado pela tela: `GET /api/status`
- Events usados pela tela: `GET /api/blaze/events/status`, `POST /api/blaze/events/start`, `POST /api/blaze/events/stop`, `POST /api/blaze/events/subscriptions/sync`
- OAuth usado pela tela: `POST /api/blaze/oauth/start`
- Overlays usados pela tela: `GET /api/overlay-profiles`, `GET /api/overlay-profiles/{profileId}/overlays`, `GET /api/public/overlays/{publicToken}/manifest`

A tela mostra apenas flags e respostas sanitizadas. Ela nao deve exibir `clientSecret`, `accessToken`, `refreshToken` ou valores reais de credenciais.

Se `/` ou `/dashboard` retornar 500, confirme primeiro se a branch ativa e `dev`, se o app foi reiniciado depois do checkout e se `src/main/resources/static/dashboard.html` esta empacotado. O smoke minimo do dashboard deve validar:

```powershell
Invoke-WebRequest http://localhost:8080/ -UseBasicParsing
Invoke-WebRequest http://localhost:8080/dashboard -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/events/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/overlay-profiles -UseBasicParsing
```

## Endpoints principais

- `GET /api/health`
- `GET /api/status`
- `POST /api/blaze/oauth/start`
- `GET /api/blaze/oauth/callback?code=...&state=...`
- `POST /api/blaze/oauth/refresh`
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

OAuth e server-side. `clientSecret`, `codeVerifier`, `accessToken` e `refreshToken` ficam apenas no backend. O token store inicial e in-memory e troca refresh token de forma atomica ao receber um novo snapshot.

REST usa `RestClient`, header `client-id`, bearer token e tratamento explicito para erros HTTP da Blaze.

Events esta preparado como abstracao segura. A implementacao atual nao abre Socket.IO real; ela oferece runner, status, captura de `session_welcome`, `sessionId` e sincronizacao de subscriptions in-memory para uma proxima fase com cliente Socket.IO Java validado.

## Testes

```powershell
.\mvnw.cmd test
```

A suite cobre health/status, propriedades seguras, OAuth, REST client, Events e overlays.

## Proximos passos

- Persistir tokens e overlays em banco seguro.
- Integrar cliente Socket.IO real depois de validar biblioteca e reconexao.
- Fazer smoke E2E com credenciais Blaze reais fora do repositorio.
- Criar dashboard/frontend em fase separada.
