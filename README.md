# Blaze Event Hub

Hub de giveaways para criadores da [Blaze.stream](https://blaze.stream). O criador conecta sua conta, configura um evento e um comando; o backend acompanha o chat e monta automaticamente o pool usado no sorteio.

## Fluxo do MVP

`Criar rascunho -> Abrir captura -> Capturar participantes -> Finalizar e sincronizar -> Sortear`

1. O criador entra com OAuth da Blaze e registra título, prêmio, canal e comando de entrada.
2. Ao abrir o evento, o polling do backend passa a observar o canal do próprio criador.
3. Cada usuário Blaze que envia exatamente o comando entra uma única vez no pool.
4. Ao finalizar, o backend registra um cutoff, faz a última sincronização até esse instante e congela quantidade e hash do pool.
5. O sorteio uniforme é persistido; repetir a chamada devolve o mesmo resultado.

Não fazem parte deste produto: respostas de bot, votos, inscrições, pesos por ação, entrada manual ou overlays para OBS.

## Stack

- Java 21, Spring Boot e Maven Wrapper
- React 18, TypeScript e Vite
- JDBC e Flyway
- H2 em desenvolvimento/testes
- PostgreSQL em produção, preparado para Supabase
- Docker multi-stage

## Rodar localmente

Requisitos: Java 21 e Node.js 20. Não instale Maven globalmente; o projeto fixa sua versão pelo wrapper.

1. Crie um `.env` local a partir de `.env.example` e preencha somente valores da sua aplicação Blaze. Nunca versione esse arquivo.
2. Valide a configuração e inicie o backend na porta `9090`:

```powershell
.\scripts\check-env.ps1
.\scripts\run-local.ps1
```

O script exige Java 21, usa o perfil `local` e deixa o Spring carregar o `.env` sem colocar segredos na linha de comando.

3. Em outro terminal, inicie o frontend:

```powershell
cd frontend
npm ci
npm run dev
```

O Vite abre `http://localhost:5173` e encaminha `/api` para `http://localhost:9090`. O backend usa H2 local e aplica as migrations comuns do Flyway automaticamente.

## Testes

```powershell
.\mvnw.cmd clean test
cd frontend
npm ci
npm test
npm run build
```

## Produção com PostgreSQL/Supabase

O perfil `prod` exige PostgreSQL. A imagem Docker já define `SPRING_PROFILES_ACTIVE=prod` e executa como usuário sem privilégios.

Variáveis que devem ser configuradas no provedor, nunca no repositório:

| Variável | Finalidade |
|---|---|
| `BLAZE_CLIENT_ID` | Identifica a aplicação OAuth Blaze |
| `BLAZE_CLIENT_SECRET` | Segredo OAuth usado somente pelo backend |
| `BLAZE_REDIRECT_URI` | Callback HTTPS cadastrado exatamente na Blaze |
| `BLAZE_CHAT_MESSAGE_LIMIT` | Mensagens por página, entre 10 e 100; padrão 100 |
| `BLAZE_CHAT_MAX_PAGES_PER_POLL` | Teto de páginas por ciclo; backfills maiores continuam do checkpoint; padrão 20 |
| `BLAZE_CHAT_HISTORY_COVERAGE_MAX_AGE_MS` | Janela conservadora para comprovar o início do histórico sem cursor anterior; padrão 7 h |
| `BLAZE_CHAT_POLL_INTERVAL_MS` | Intervalo entre ciclos de captura; padrão 2000 ms |
| `EVENTHUB_CREDENTIAL_ENCRYPTION_KEY` | Chave Base64 de 32 bytes para cifrar tokens persistidos |
| `EVENTHUB_DB_URL` | URL JDBC PostgreSQL |
| `EVENTHUB_DB_USER` | Usuário do banco |
| `EVENTHUB_DB_PASSWORD` | Senha do banco |
| `EVENTHUB_API_KEY` | Chave opcional para integração interna server-to-server |

Em Render + Supabase, use a conexão IPv4/session pooler indicada pelo Supabase e TLS. O banco deve começar vazio: deixe o Flyway criar `flyway_schema_history` e aplicar, em ordem, as migrations de `db/migration/common` e `db/migration/postgresql`. Não aplique as mesmas migrations manualmente antes do primeiro start sem uma estratégia explícita de baseline.

Build e execução local da imagem:

```powershell
docker build -t blaze-event-hub .
docker run --rm -p 10000:10000 --env-file .env.docker blaze-event-hub
```

Use nesse smoke um arquivo `.env.docker` não versionado com PostgreSQL e callback de produção; não reutilize a configuração H2 local. O container falha no início quando a conexão PostgreSQL de produção está ausente e não cai silenciosamente para H2. Credenciais Blaze ausentes aparecem como integração não configurada até serem fornecidas com segurança.

## API principal

| Área | Rotas |
|---|---|
| Operação | `GET /api/health`, `GET /api/status` |
| OAuth | `GET /api/blaze/oauth/start`, `GET /api/blaze/oauth/callback`, `GET /api/blaze/oauth/session`, `POST /api/blaze/oauth/refresh`, `POST /api/blaze/oauth/disconnect` |
| Canal | `GET /api/blaze/channels/resolve?slug=...` |
| Eventos públicos | `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/stats`, `GET /api/events/{id}/winner` |
| Gestão autenticada | `POST /api/events`, `PUT /api/events/{id}`, `POST /api/events/{id}/open`, `POST /api/events/{id}/finalize`, `POST /api/events/{id}/cancel`, `POST /api/events/{id}/draw` |
| Área do criador | `GET /api/events/my/history`, `GET /api/events/{id}/participants`, `GET /api/members/me` |

Rascunhos e participantes não são expostos a outros usuários. O header histórico `X-Nollen-Api-Key` existe apenas para compatibilidade de integrações internas e nunca deve ser enviado pelo frontend.

## Segurança

- OAuth e refresh acontecem no backend; tokens nunca fazem parte dos DTOs públicos.
- Access e refresh tokens persistidos usam AES-256-GCM com chave externa ao banco.
- A sessão do navegador usa cookie `HttpOnly`, `Secure` em produção e `SameSite=Lax`.
- Mutações de navegador exigem prova de mesma origem; respostas recebem CSP e headers defensivos.
- `.env`, bancos locais, builds, logs, cookies e estados de navegador são ignorados pelo Git e pelo contexto Docker.
- Qualquer secret exibido em commit, print, chat ou log deve ser rotacionado antes da publicação.

## Branches

As branches permanentes são:

- `dev`: integração e validação do próximo conjunto de mudanças;
- `main`: versão publicada e pronta para produção.

O fluxo é `dev -> main` depois de testes backend, frontend e smoke de produção. Não envie credenciais, `.env`, `target`, `frontend/dist` ou dados locais para nenhuma branch.

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
