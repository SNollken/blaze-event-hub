# Roadmap

## Fase 1: backend base

- Spring Boot limpo.
- Health/status.
- Blaze OAuth server-side.
- Blaze REST client.
- Events abstraido.
- Overlays com public manifest.
- Testes automatizados.

## Fase 2: frontend/dashboard

- Frontend React unificado concluido: dashboard, live events, Blaze Channel, Alerts, Giveaways e Overlays em Vite.
- Rotas `/`, `/dashboard` e rotas legadas servem shell React sem depender de HTML estatico paralelo.
- MVP 2 concluido: configuracao assistida da Blaze no dashboard e `GET /api/blaze/setup` com checklist, Redirect URI, scopes minimos, exemplo `.env`, links oficiais, proximos passos e contrato sem segredos/tokens.
- Overlay Runtime OBS MVP integrado: `GET /overlay/{publicToken}` consome manifest publico e renderiza camadas simples em 16:9 transparente para OBS.

## Fase 3: OAuth real e perfil

- OAuth real bruto validado com `users.read,offline.access`.
- OAuth produto concluido no backend/dashboard provisorio: sessao segura, callback amigavel, refresh manual, disconnect local e resumo seguro de perfil.
- `GET /api/blaze/oauth/session` reflete conta conectada, perfil, datas e proxima acao recomendada sem retornar tokens ou segredos.
- `GET /v1/users/profile` e a chamada REST usada para sincronizar resumo seguro do usuario conectado.
- Manter scopes de chat/moderacao/bot fora ate existir feature correspondente.
- Limite atual: token e perfil seguem em storage in-memory ate a fase de persistencia segura.

## Fase 4: Events real e canais

- Configurar canal monitorado real.
- Usar a conta conectada e `READY_FOR_EVENTS` como pre-condicao operacional.
- Validar biblioteca Socket.IO Java e reconexao.
- Capturar `session_welcome`, obter `sessionId` e sincronizar subscriptions reais.
- Auditar erros 401/403 sem expor tokens.
- Alert Engine ja recebe envelopes aceitos pelo runner via `BlazeEventsPipeline`.

## Fase 4.5: persistencia e admin MVP

- H2 dev/test integrado para alerts, events log, canal Blaze, live events, giveaways e overlays.
- API key basica integrada para endpoints administrativos.
- Persistencia OAuth segura segue pendente para uma fase dedicada, sem alterar o fluxo OAuth atual.

## Fase 5: overlay studio visual

- Runtime publico OBS ja existe como MVP; esta fase deve focar editor/preview visual, nao no HTML publico basico.
- Editor visual de layers.
- Preview por overlay.
- Controles de texto, imagem, shape e ordenacao.

## Fase 6: upload assets avancado

- Storage persistente.
- Validacao de dimensoes.
- Antivirus/scan opcional.
- CDN ou serving seguro.

## Fase 7: smoke E2E real com Blaze

- Credenciais reais fora do repo.
- OAuth completo.
- Primeira chamada REST.
- Primeiro session welcome e subscription real.

## Fase 8: persistencia real de tokens

- DB cifrado ou secret storage.
- Rotacao de refresh token.
- Auditoria de 401/403.
