# AGENTS

- Produto: Blaze Event Hub (BEH). Nome historico `NollenBlaze` foi aposentado: nao usar em codigo, branding, commits ou docs novas. `BlazeBot` so aparece em contexto historico/migracao.
- Package Java principal: `com.blaze.eventhub`. Prefixo de config: `beh.*` (ex.: `beh.blaze.*`, `beh.security.*`).
- Frontend: React 18 + TypeScript + Vite em `frontend/`, servido pelo proprio Spring via `static/` (build copiado para o jar no package). i18n zero-dependency em `frontend/src/i18n/` com locales `en` e `pt-BR`; toda chave nova deve existir nos DOIS locales.
- Nao commitar vault, prints, logs, `target`, `frontend/node_modules`, `frontend/dist`, `.env`, cookies, storage state, `.hermes`, temporarios ou credenciais.
- Nao commitar `clientSecret`, `accessToken`, `refreshToken`, cookies ou qualquer segredo real.
- Usar Java 21 sem alterar PATH global. Preferir `JAVA_HOME` apenas por processo/sessao.
- Usar Maven Wrapper. Nao instalar Maven global para este projeto.
- Microfeatures futuras devem ir para branch longa de feature e so integrar blocos completos e validados.
- Manter historico Git limpo. Evitar merge commit desnecessario.
- `/api/blaze/setup` deve devolver flags, checklist, scopes recomendados, proximos passos, links oficiais e exemplos com placeholders; nunca devolver valores reais nem campos publicos `clientSecret`, `client_secret`, `accessToken`, `access_token`, `refreshToken`, `refresh_token`, `codeVerifier` ou `code_verifier`.
- Scopes padrao devem ficar em privilegio minimo: `users.read,offline.access`. `channel.moderate` e `users.bot` so entram quando houver feature de chat/moderacao/bot validada.
- Sempre validar `/`, `/dashboard` (redirect SPA), `/api/health`, `/api/status`, `/api/blaze/setup`, `/api/blaze/events/status`, `/api/overlay-profiles`, `/api/public/overlays/demo-overlay-obs-mvp/manifest` e `/overlay/demo-overlay-obs-mvp` no smoke do dashboard/runtime.
- O app deve continuar resiliente sem credenciais reais, sem token e sem Events rodando.
- Runtime publico de overlay OBS deve ser servido em `/overlay/{publicToken}`, sem dashboard, sem navbar, com fundo transparente e sem expor secrets/tokens.
- Ao testar runtime local, fechar o backend iniciado pela tarefa e confirmar porta 8080 livre ao final.
- Nao abrir PR automatico. Commits e pushes seguem a branch combinada.
- Features devem usar `feature/*` e integrar na branch principal quando validadas.
