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
- Runtime publico OBS: `GET /overlay/{publicToken}`

A tela mostra apenas flags e respostas sanitizadas. Ela nao deve exibir `clientSecret`, `accessToken`, `refreshToken` ou valores reais de credenciais.

## MVP 2 - Configuracao assistida da Blaze

O MVP 2 adiciona uma area de configuracao no mesmo dashboard provisorio e o endpoint seguro `GET /api/blaze/setup`. O objetivo e orientar App Setup, OAuth, scopes, canal monitorado e Events sem exigir credenciais reais no repositorio.

- Endpoint de setup: `GET /api/blaze/setup`
- Botao para copiar Redirect URI: `http://localhost:8080/api/blaze/oauth/callback`
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
Invoke-WebRequest http://localhost:8080/api/blaze/events/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/overlay-profiles -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/public/overlays/demo-overlay-obs-mvp/manifest -UseBasicParsing
Invoke-WebRequest http://localhost:8080/overlay/demo-overlay-obs-mvp -UseBasicParsing
```

## Overlay Runtime OBS

O runtime publico de overlay e servido em `GET /overlay/{publicToken}`. A pagina e HTML/CSS/JS estatico, nao tem dashboard, navbar, botoes visiveis nem dependencia de OAuth. Ela busca `GET /api/public/overlays/{publicToken}/manifest`, renderiza camadas em um canvas 16:9 e mantem fundo transparente para OBS Browser Source.

Demo local:

- Overlay: `http://localhost:8080/overlay/demo-overlay-obs-mvp`
- Manifest: `http://localhost:8080/api/public/overlays/demo-overlay-obs-mvp/manifest`

### Modo debug

Adicione `?debug=1` na URL para ativar o modo debug:

- `http://localhost:8080/overlay/demo-overlay-obs-mvp?debug=1`

No modo debug, o runtime mostra bordas tracejadas no canvas e camadas, painel de informacoes (token mascarado, quantidade de layers, canvas, status) e borda vermelha em mensagens de erro. O modo debug nunca expoe secrets ou tokens.

### Query params

- `?debug=1` ou `?debug=true` — ativa modo debug
- `?fit=contain` — modo de ajuste (futuro)

### OBS Browser Source

No OBS, adicione uma Browser Source apontando para a URL da overlay, com resolucao 1920x1080 ou 1280x720. O runtime suporta texto, imagens (quando o manifest trouxer URL publica), shapes simples, visibilidade, posicao, tamanho, opacidade, z-index e estilos basicos. Fundo transparente, sem scroll, sem interacao obrigatoria, reload seguro.

Limitacoes atuais:

- Sem realtime/polling — manifest e carregado uma vez no boot
- Sem animacoes
- Sem editor visual (Overlay Studio e futuro)
- Sem autenticacao na URL publica

## Endpoints principais

- `GET /api/health`
- `GET /api/status`
- `GET /api/blaze/setup`
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
- `GET /api/public/overlays/{publicToken}/assets/{assetId}`
- `GET /overlay/{publicToken}`

## Arquitetura de overlays

Perfil e apenas um grupo organizacional. Overlay e a entidade de runtime, tem `publicToken` unico, config, layers e assets. O runtime publico consome `GET /api/public/overlays/{publicToken}/manifest` pela pagina `GET /overlay/{publicToken}`.

Overlay desativada retorna manifesto seguro com `enabled=false` e listas vazias. Atualizacoes comuns preservam o `publicToken`. O manifesto inclui `publicUrl` para cada asset, permitindo que camadas de imagem carreguem uploads diretamente.

O runtime publico nao deve expor credenciais, tokens OAuth, stack traces ou JSON bruto grande. Estados de erro mostram mensagem discreta no navegador e ficam transparentes no OBS.

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
- Evoluir o dashboard/frontend final em fase separada.
