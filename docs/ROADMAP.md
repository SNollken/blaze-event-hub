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

- MVP 1 concluido: painel provisorio em `/` e `/dashboard` para health/status, OAuth start, Events noop/status, canal monitorado, overlay profiles e manifest demo.
- Correcao de regressao: `/` e `/dashboard` devem servir a tela provisoria sem credenciais reais; o JS deve mostrar estados indisponiveis em vez de travar se algum endpoint de status falhar.
- MVP 2 concluido: configuracao assistida da Blaze no dashboard e `GET /api/blaze/setup` com checklist, Redirect URI, scopes minimos, exemplo `.env`, links oficiais, proximos passos e contrato sem segredos/tokens.

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

## Fase 5: overlay studio visual

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
