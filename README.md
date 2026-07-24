# Blaze Event Hub

A giveaway hub for [Blaze.stream](https://blaze.stream) creators. Creators connect their accounts, configure an event with a keyword, and the backend automatically monitors the chat to build a participant pool for drawing winners.

## Core Flow (MVP)

`Create draft -> Open capture -> Capture participants -> Finalize & Sync -> Draw`

1. Creator signs in via Blaze OAuth and sets up the event (title, prize, channel, keyword).
2. Once opened, the backend starts polling the creator's channel.
3. Every Blaze user who types the exact keyword is entered into the pool exactly once.
4. When finalizing, the backend registers a cutoff timestamp, performs a final sync up to that exact moment, and freezes the pool.
5. A uniform draw is executed and persisted; subsequent requests for the winner will return the same result.

Out of scope for this MVP: bot replies, voting systems, subscriptions, weighted actions, manual entries, or OBS overlays.

## Stack

- Java 21, Spring Boot, and Maven Wrapper
- React 18, TypeScript, and Vite
- JDBC and Flyway
- H2 for development/testing
- PostgreSQL for production (Supabase-ready)
- Multi-stage Docker build

## Running Locally

Requirements: Java 21 and Node.js 20. Do not install Maven globally; the project uses the included wrapper to pin the version.

1. Create a local `.env` file based on `.env.example` and fill in your Blaze application credentials. Never commit this file.
2. Validate the configuration and start the backend on port `9090`:

```powershell
.\scripts\check-env.ps1
.\scripts\run-local.ps1
```

The script requires Java 21, activates the `local` profile, and allows Spring to load the `.env` without exposing secrets via the command line.

3. In another terminal, start the frontend:

```powershell
cd frontend
npm ci
npm run dev
```

Vite will start on `http://localhost:5173` and proxy `/api` requests to `http://localhost:9090`. The backend uses an in-memory H2 database and applies Flyway migrations automatically.

## Testing

```powershell
.\mvnw.cmd clean test
cd frontend
npm ci
npm test
npm run build
```

## Production with PostgreSQL/Supabase

The `prod` profile requires PostgreSQL. The provided Docker image sets `SPRING_PROFILES_ACTIVE=prod` by default and runs as an unprivileged user.

Variables that must be configured in your hosting provider (never in the repository):

| Variable | Purpose |
|---|---|
| `BLAZE_CLIENT_ID` | Identifies the Blaze OAuth application |
| `BLAZE_CLIENT_SECRET` | OAuth secret (backend only) |
| `BLAZE_REDIRECT_URI` | HTTPS callback registered exactly on Blaze |
| `BLAZE_CHAT_MESSAGE_LIMIT` | Messages per page, between 10 and 100; default 100 |
| `BLAZE_CHAT_MAX_PAGES_PER_POLL` | Max pages per cycle; large backfills resume from checkpoints; default 20 |
| `BLAZE_CHAT_HISTORY_COVERAGE_MAX_AGE_MS` | Conservative window to prove history start without a previous cursor; default 7 h |
| `BLAZE_CHAT_POLL_INTERVAL_MS` | Delay between capture cycles; default 2000 ms |
| `EVENTHUB_CREDENTIAL_ENCRYPTION_KEY` | 32-byte Base64 key to encrypt persisted tokens |
| `EVENTHUB_DB_URL` | JDBC PostgreSQL URL |
| `EVENTHUB_DB_USER` | Database user |
| `EVENTHUB_DB_PASSWORD` | Database password |
| `EVENTHUB_API_KEY` | Optional key for internal server-to-server integration |

For Render + Supabase, use the IPv4 connection / session pooler provided by Supabase over TLS. The database must start empty: let Flyway create `flyway_schema_history` and apply the migrations from `db/migration/common` and `db/migration/postgresql` in order. Do not apply these migrations manually prior to the first startup without an explicit baseline strategy. Since the application accesses PostgreSQL solely through Spring/JDBC, keep the Supabase Data API disabled in the dashboard; the PostgreSQL migration also enables RLS and revokes Data API roles as defense-in-depth.

Local build and smoke test of the image:

```powershell
docker build -t blaze-event-hub .
docker run --rm -p 10000:10000 --env-file .env.docker blaze-event-hub
```

For this smoke test, use an unversioned `.env.docker` file configured with PostgreSQL and a production callback; do not reuse the local H2 configuration. The container will fail to start if the production PostgreSQL connection is unavailable and will not silently fall back to H2. Missing Blaze credentials will manifest as an unconfigured integration until safely provided.

## Main API

| Scope | Routes |
|---|---|
| Operation | `GET /api/health`, `GET /api/status` |
| OAuth | `GET /api/blaze/oauth/start`, `GET /api/blaze/oauth/callback`, `GET /api/blaze/oauth/session`, `POST /api/blaze/oauth/refresh`, `POST /api/blaze/oauth/disconnect` |
| Channel | `GET /api/blaze/channels/resolve?slug=...` |
| Public Events | `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/stats`, `GET /api/events/{id}/winner` |
| Auth Management | `POST /api/events`, `PUT /api/events/{id}`, `POST /api/events/{id}/open`, `POST /api/events/{id}/finalize`, `POST /api/events/{id}/cancel`, `POST /api/events/{id}/draw` |
| Creator Area | `GET /api/events/my/history`, `GET /api/events/{id}/participants`, `GET /api/members/me` |

Drafts and participants are never exposed to other users. The legacy `X-Nollen-Api-Key` header exists solely for internal integration compatibility and should never be sent by the frontend.

## Security

- OAuth flows and token refreshes happen entirely in the backend; tokens are never included in public DTOs.
- Persisted access and refresh tokens are encrypted using AES-256-GCM with a key external to the database.
- Browser sessions rely on `HttpOnly` cookies, flagged `Secure` in production with `SameSite=Lax`.
- Browser mutations require same-origin proof; responses include CSP and defensive headers.
- `.env`, local databases, builds, logs, cookies, and browser states are ignored by Git and the Docker context.
- Any secret leaked in a commit, print, chat, or log must be rotated before publication.

## Branches

Permanent branches:

- `dev`: integration and validation of the upcoming set of changes;
- `main`: published version ready for production.

The flow is `dev -> main` following backend, frontend, and production smoke tests. Never push credentials, `.env`, `target`, `frontend/dist`, or local data to any branch.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Roadmap](docs/ROADMAP.md)
