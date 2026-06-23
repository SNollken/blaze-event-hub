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
- Proximo incremento: evoluir configuracao OAuth/canais e UX de diagnostico sem segredos.

## Fase 3: overlay studio visual

- Editor visual de layers.
- Preview por overlay.
- Controles de texto, imagem, shape e ordenacao.

## Fase 4: upload assets avancado

- Storage persistente.
- Validacao de dimensoes.
- Antivirus/scan opcional.
- CDN ou serving seguro.

## Fase 5: smoke E2E real com Blaze

- Credenciais reais fora do repo.
- OAuth completo.
- Primeira chamada REST.
- Primeiro session welcome e subscription real.

## Fase 6: persistencia real de tokens

- DB cifrado ou secret storage.
- Rotacao de refresh token.
- Auditoria de 401/403.
