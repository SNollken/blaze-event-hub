# Blaze Event Hub

Backend limpo do Blaze Event Hub para integracao server-side com blaze.stream, OAuth, REST APIs, Events e overlays publicos por manifesto.

## Stack

- Java 21
- Spring Boot 3.5.3
- Maven Wrapper
- Spring Web, Validation, Actuator, JDBC
- H2 para dev/testes
- RestClient para HTTP
- Dashboard Shell MVP estatico em `src/main/resources/static/dashboard.html` (fallback em `/dashboard`)
- Frontend React + Vite em `frontend/` — build e embutido no jar e servido pelo Spring em `/`
- Storage H2 para Alerts, Events log, Canal Blaze, Live Events, Giveaways e Overlays

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

O backend exige API key nos endpoints administrativos de `/api/**`, exceto health/status, callback OAuth e rotas publicas (`/api/public/**`, `/overlay/**`, `/assets/**`, `/vite.svg`). Em dev, se `BEH_API_KEY` nao estiver definida, a chave padrao e `dev-local-key`.

```powershell
$headers = @{ 'X-BEH-Api-Key' = 'dev-local-key' }
Invoke-WebRequest http://localhost:8080/api/alerts/stats -Headers $headers -UseBasicParsing
```

Para o frontend React:

```powershell
cd frontend
npm install
npm run dev
```

O Vite roda em `http://localhost:5173` e faz proxy de `/api` para `http://localhost:8080`.

### Build unico (jar com o frontend)

O build de producao embute o React (`frontend/dist`) dentro do jar do Spring Boot:

```bash
cd frontend && npm install && npm run build && cd ..
./mvnw clean package
java -jar target/eventhub-0.0.1-SNAPSHOT.jar
```

O Maven copia `frontend/dist` para `target/classes/static` na fase `process-resources` (plugin `maven-resources-plugin`, execution `copy-frontend-dist`). Se `frontend/dist` nao existir, o build do backend segue normalmente e `/` mostra uma pagina pedindo o build do frontend.

**Atencao (producao):** o SPA envia a API key no header `X-BEH-Api-Key` (ver `frontend/src/api/client.ts`). Em producao, defina `VITE_BEH_API_KEY` no ambiente de **build** (ex.: env vars da Render) com o MESMO valor de `BEH_API_KEY` do backend. Sem isso, o build embute o fallback `dev-local-key` e toda a UI recebe 401 do `ApiKeyFilter`. Essa chave nao e um segredo real (qualquer visitante a ve no header das requests do browser) — ela so bloqueia clientes nao-browser; os segredos de verdade (`BLAZE_CLIENT_SECRET`, tokens OAuth) ficam exclusivamente no backend.

Fontes sao self-hosted via `@fontsource` (Funnel Display/Sans e JetBrains Mono, OFL): nenhum request externo para Google Fonts, de acordo com o CSP `default-src 'self'` + `font-src 'self' data:`.

### Deploy na Render (Docker)

O servico na Render roda via `Dockerfile` multi-stage (Node 20 builda o frontend, Maven builda o jar, JRE 21 roda). Dois pontos criticos:

1. **`VITE_BEH_API_KEY` e obrigatorio no BUILD**: a chave e embutida no bundle em build time. No Render, marque a variavel como disponivel no build; ela chega ao Dockerfile como `--build-arg` (declarado no stage do frontend). **Sem ela o build FALHA de proposito** (fail-fast) — antes o bundle era gerado com o fallback `dev-local-key` e toda a UI recebia 401 em silencio.
2. **Porta**: o Render injeta `PORT` (ex.: 10000) no container; o app obedece na ordem `SERVER_PORT` > `PORT` > `8080` (`application.yml`). O health check do container usa a mesma porta.

Build local da imagem (o build-arg e obrigatorio):

```bash
docker build --build-arg VITE_BEH_API_KEY=<mesmo valor de BEH_API_KEY> -t blaze-event-hub .
docker run --rm -e PORT=8080 -p 8080:8080 blaze-event-hub
```

### Producao (Supabase/PostgreSQL)

O jar inclui o driver PostgreSQL (scope `runtime`). Para apontar para o Supabase, defina no ambiente de **runtime** (ex.: Render):

| Variavel | Valor |
|----------|-------|
| `BEH_DB_URL` | `jdbc:postgresql://<projeto>.pooler.supabase.com:6543/postgres?sslmode=require` — **`sslmode=require` e obrigatorio**; nao ha outra camada que force TLS |
| `BEH_DB_USER` | usuario do Supabase (ATENCAO: e `BEH_DB_USER`, nao `BEH_DB_USERNAME`) |
| `BEH_DB_PASSWORD` | senha do Supabase |
| `BEH_API_KEY` | chave real (o default `dev-local-key` NAO deve ir para prod) |
| `VITE_BEH_API_KEY` | **ambiente de BUILD**, mesmo valor de `BEH_API_KEY` (ver acima) |
| `SESSION_COOKIE_SECURE` | `true` |
| `BLAZE_CLIENT_ID` / `BLAZE_CLIENT_SECRET` / `BLAZE_REDIRECT_URI` | credenciais OAuth da Blaze |

O schema e criado pelo proprio app no boot (`spring.sql.init.mode=always` roda `schema.sql`, incluindo indices). RLS do Supabase nao e usado nem necessario: o frontend nao acessa o Supabase; todo acesso a dados passa pelo backend com credencial de servico unica.

## Dashboard Shell MVP

O dashboard servido pelo Spring Boot e um Dashboard Shell MVP provisorio em HTML/CSS/JS simples. Ele existe para organizar a navegacao real do produto ate o design final no OpenDesign, sem criar backend pesado nem mexer no fluxo OAuth.

- Tela inicial: `http://localhost:8080/` (SPA React quando embutido no jar; shell MVP como fallback)
- Dashboard MVP: `http://localhost:8080/dashboard`
- Frontend dev: `http://localhost:5173/`
- Arquivos do shell: `src/main/resources/static/dashboard.html`, `dashboard.css` e `dashboard.js`
- Health usado pela tela: `GET /api/health`
- Status seguro usado pela tela: `GET /api/status`
- Setup usado pela tela: `GET /api/blaze/setup`
- Sessao OAuth usada pela tela: `GET /api/blaze/oauth/session`
- Events usados pela tela: `GET /api/blaze/events/status`, `POST /api/blaze/events/start`, `POST /api/blaze/events/stop`, `POST /api/blaze/events/subscriptions/sync`
- OAuth usado pela tela: `POST /api/blaze/oauth/start`
- Canal usado pela tela: `GET/POST /api/blaze/channel`, `GET/PUT/DELETE /api/blaze/channel/{id}`
- Live Events usados pela tela: `GET /api/live-events`, `GET /api/live-events/stats`, `POST /api/live-events/simulate`
- Alertas usados pela tela: `GET/POST /api/alerts/rules`, `PUT/DELETE /api/alerts/rules/{id}`, `GET /api/alerts/history`, `GET /api/alerts/active`, `POST /api/alerts/acknowledge/{id}`
- Sorteios usados pela tela: `GET/POST /api/giveaways`, `POST /api/giveaways/{id}/open`, `POST /api/giveaways/{id}/close`, `POST /api/giveaways/{id}/enter`, `POST /api/giveaways/{id}/draw`
- Overlays usados pela tela: `GET /api/overlay-profiles`, `GET /api/overlay-profiles/{profileId}/overlays`, `GET /api/public/overlays/{publicToken}/manifest`
- Runtime publico OBS: `GET /overlay/{publicToken}`

O shell tem sidebar esquerda com Visao geral, Conta Blaze, Canal monitorado, Events, Live Events, Alertas, Sorteios, Overlays, Configuracoes e Diagnostico. Endpoints ausentes, protegidos ou com erro sao tratados como "nao disponivel" ou "erro ao carregar", sem jogar JSON bruto na tela.

O frontend nao recebe `clientSecret`, `accessToken`, `refreshToken` ou valores reais de credenciais. As rotas antigas `/alerts-dashboard`, `/giveaways-dashboard`, `/live-events` e `/overlays-dashboard` servem a entrada da SPA quando o React esta embutido no jar (o React redireciona para `/alerts`, `/giveaways`, `/events` e `/overlays` no cliente); sem o build do frontend, continuam respondendo pelo shell MVP.

## Frontend React

O frontend React + Vite em `frontend/` e a UI principal do produto. Com `npm run build` + `./mvnw clean package`, o `dist` e copiado para `target/classes/static` e o Spring serve o SPA em `/` e em todas as rotas de pagina (`/events`, `/blaze`, `/alerts`, `/giveaways`, `/overlays`) — cada rota de pagina e mapeada explicitamente no `DashboardController`, que entrega o `index.html` da SPA. `/dashboard` e `/dashboard.html` mantem o shell MVP como painel de diagnostico. Se `frontend/dist` nao existir no momento do build, o backend segue funcionando e `/` mostra o shell MVP como fallback.

- Rotas legadas com redirect no cliente: `/channel` → `/blaze`, `/live-events` → `/events`, `/alerts-dashboard` → `/alerts`, `/giveaways-dashboard` → `/giveaways`, `/overlays-dashboard` → `/overlays`
- Tema escuro unico via CSS custom properties (`src/index.css`), sem dependencia de CDN externa
- Tipografia: Funnel Display (titulos), Funnel Sans (UI) e JetBrains Mono (codigo) self-hosted
- Layout responsivo auditado em 375px e 768px sem overflow horizontal
- Testes: `npm test` (vitest + jsdom + testing-library), typecheck `tsc --noEmit`

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

Se `/` ou `/dashboard` retornar 500, confirme primeiro se a branch ativa e `main` e se o app foi reiniciado depois do checkout. O smoke minimo do dashboard deve validar:

```powershell
$headers = @{ 'X-BEH-Api-Key' = 'dev-local-key' }
Invoke-WebRequest http://localhost:8080/ -UseBasicParsing
Invoke-WebRequest http://localhost:8080/dashboard -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/health -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/status -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/setup -Headers $headers -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/blaze/events/status -Headers $headers -UseBasicParsing
Invoke-WebRequest http://localhost:8080/api/overlay-profiles -Headers $headers -UseBasicParsing
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

Saude, status e setup:

- `GET /api/health`
- `GET /api/status`
- `GET /api/blaze/setup`

OAuth:

- `GET /api/blaze/oauth/session`
- `POST /api/blaze/oauth/start`
- `GET /api/blaze/oauth/callback?code=...&state=...`
- `POST /api/blaze/oauth/refresh`
- `POST /api/blaze/oauth/disconnect`

Canal Blaze monitorado:

- `GET /api/blaze/channel`
- `GET /api/blaze/channel/{id}`
- `POST /api/blaze/channel`
- `PUT /api/blaze/channel/{id}`
- `DELETE /api/blaze/channel/{id}`

REST Blaze:

- `GET /api/blaze/users/profile`
- `GET /api/blaze/channels?slug=...`
- `GET /api/blaze/chats/messages?channelId=...`
- `POST /api/blaze/chats/messages`

Events:

- `GET /api/blaze/events/status`
- `POST /api/blaze/events/start`
- `POST /api/blaze/events/stop`
- `GET /api/blaze/events/log`
- `POST /api/blaze/events/simulate`
- `GET /api/blaze/events/capabilities`
- `POST /api/blaze/events/subscriptions/sync`

Live Events (intake):

- `GET /api/live-events`
- `POST /api/live-events`
- `GET /api/live-events/{id}`
- `GET /api/live-events/stats`
- `POST /api/live-events/simulate`

Alertas:

- `GET /api/alerts/rules`
- `POST /api/alerts/rules`
- `PUT /api/alerts/rules/{id}`
- `DELETE /api/alerts/rules/{id}`
- `GET /api/alerts/history`
- `GET /api/alerts/active`
- `POST /api/alerts/acknowledge/{id}`
- `POST /api/alerts/evaluate`
- `GET /api/alerts/stats`
- `GET /api/alerts/capabilities`

Sorteios:

- `GET /api/giveaways`
- `POST /api/giveaways`
- `GET /api/giveaways/stats`
- `GET /api/giveaways/capabilities`
- `GET /api/giveaways/{id}`
- `PUT /api/giveaways/{id}`
- `DELETE /api/giveaways/{id}`
- `POST /api/giveaways/{id}/open`
- `POST /api/giveaways/{id}/close`
- `POST /api/giveaways/{id}/enter`
- `POST /api/giveaways/{id}/draw`
- `GET /api/giveaways/{id}/results`

Overlays:

- `GET /api/overlay-profiles`
- `POST /api/overlay-profiles`
- `GET /api/overlay-profiles/{profileId}`
- `PUT /api/overlay-profiles/{profileId}`
- `DELETE /api/overlay-profiles/{profileId}`
- `GET /api/overlay-profiles/{profileId}/overlays`
- `POST /api/overlay-profiles/{profileId}/overlays`
- `GET /api/overlays/{overlayId}`
- `PUT /api/overlays/{overlayId}`
- `DELETE /api/overlays/{overlayId}`
- `GET /api/overlays/{overlayId}/layers`
- `POST /api/overlays/{overlayId}/layers`
- `PUT /api/overlays/{overlayId}/layers/{layerId}`
- `DELETE /api/overlays/{overlayId}/layers/{layerId}`
- `POST /api/overlays/{overlayId}/assets` (multipart)
- `GET /api/public/overlays/{publicToken}/manifest`
- `GET /api/public/overlays/{publicToken}/assets/{assetId}`
- `GET /overlay/{publicToken}`

Runtime overlays (config):

- `GET /api/overlay-runtimes`
- `GET /api/overlay-runtimes/{id}`
- `POST /api/overlay-runtimes`
- `PUT /api/overlay-runtimes/{id}`
- `DELETE /api/overlay-runtimes/{id}`

## Arquitetura de overlays

Perfil e apenas um grupo organizacional. Overlay e a entidade de runtime, tem `publicToken` unico, config, layers e assets. O runtime publico consome `GET /api/public/overlays/{publicToken}/manifest` pela pagina `GET /overlay/{publicToken}`.

Overlay desativada retorna manifesto seguro com `enabled=false` e listas vazias. Atualizacoes comuns preservam o `publicToken`. O manifesto inclui `publicUrl` para cada asset, permitindo que camadas de imagem carreguem uploads diretamente.

O runtime publico nao deve expor credenciais, tokens OAuth, stack traces ou JSON bruto grande. Estados de erro mostram mensagem discreta no navegador e ficam transparentes no OBS.

## Blaze OAuth, REST e Events

OAuth e server-side. `clientSecret`, `codeVerifier`, `accessToken` e `refreshToken` ficam apenas no backend. O token store inicial e in-memory e troca refresh token de forma atomica ao receber um novo snapshot.

REST usa `RestClient`, header `client-id`, bearer token e tratamento explicito para erros HTTP da Blaze.

Events esta preparado como abstracao segura. A implementacao atual nao abre Socket.IO real; ela oferece runner, status, captura de `session_welcome`, `sessionId` e sincronizacao de subscriptions. Envelopes aceitos pelo runner passam pelo pipeline de log, Live Event Intake e Alert Engine.

## Testes

```powershell
.\mvnw.cmd clean verify
cd frontend
npm test
npm run build
npm audit --audit-level=moderate
```

A suite cobre health/status, propriedades seguras, OAuth, REST client, Events, alerts, giveaways, overlays e smoke tests React. Inclui testes de integracao dos stores em PostgreSQL real via Testcontainers (14 casos), alem dos testes em H2.

## Proximos passos

- Integrar cliente Socket.IO real depois de validar biblioteca e reconexao.
- Fazer smoke E2E com credenciais Blaze reais fora do repositorio.
- Persistir tokens OAuth em storage seguro sem expor credenciais.
