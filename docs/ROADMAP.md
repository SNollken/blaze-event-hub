# Roadmap do Blaze Event Hub

## MVP implementado

- [x] OAuth Blaze server-side com PKCE, state por sessão, refresh e logout.
- [x] Perfil do criador e credenciais OAuth cifradas em repouso.
- [x] Criação e edição de giveaway em rascunho.
- [x] Validação do canal da conta Blaze conectada.
- [x] Abertura da captura por comando exato no chat.
- [x] Participante único por usuário Blaze e mensagem.
- [x] Estado `finalizing`, cutoff persistido e rejeição de mensagens posteriores ao clique.
- [x] Sincronização final e snapshot com quantidade/hash do pool.
- [x] Sorteio uniforme, persistido, idempotente e com registro técnico.
- [x] SPA React responsiva para descoberta e gestão do ciclo completo.
- [x] Flyway para H2 local e PostgreSQL/Supabase em produção.
- [x] Sessão segura, proteção CSRF, CSP e headers defensivos.
- [x] Docker multi-stage com runtime sem privilégios.

## Antes de publicar

- [ ] Rotacionar todo client secret que já apareceu em commit, print, chat ou log.
- [ ] Confirmar que `.env` e o valor antigo não existem em nenhum ref do Git remoto.
- [ ] Criar o PostgreSQL/Supabase vazio e deixar o Flyway aplicar as migrations.
- [ ] Configurar Render com OAuth, chave de criptografia e conexão PostgreSQL via TLS.
- [ ] Fazer smoke E2E: login, criar, abrir, capturar usuário real, finalizar e sortear.
- [ ] Validar em produção cookies, callback HTTPS, RLS e ausência de segredos nas respostas.
- [ ] Confirmar operação com uma única réplica enquanto o polling usar locks locais.
- [ ] Manter apenas `main` e `dev` como branches permanentes e promover `dev -> main` após a validação.

## Confiabilidade da captura

- [x] Confirmar no portal oficial cursor, limite de 100 mensagens e janela da sessão ao vivo ou de cerca de 8 horas offline em `GET /v1/chats/messages`.
- [x] Percorrer páginas por cursor até o último cursor persistido ou o início do evento, com checkpoint retomável e prova de cobertura antes da finalização.
- [x] Diferenciar chat vazio válido de resposta Blaze nula ou incompatível.
- [x] Persistir um cutoff no clique de finalizar e aceitar somente mensagens até esse instante.
- [ ] Adicionar métricas para atraso do polling, mensagens vistas, entradas aceitas e falhas.
- [x] Recusar finalização sem participantes e reabrir a captura com mensagem acionável.

## Escala e operação

- [ ] Substituir locks locais por coordenação PostgreSQL/distribuída antes de usar múltiplas réplicas.
- [ ] Proteger refresh concorrente entre instâncias.
- [ ] Adicionar rate limiting para OAuth e mutações sensíveis.
- [ ] Testar migrations e concorrência em PostgreSQL real via Testcontainers.
- [ ] Criar alertas de readiness para credencial expirada, polling parado e schema incompatível.
- [ ] Definir backup, retenção e restauração do banco.

## Evolução de produto

- [ ] Permitir canais moderados quando a Blaze oferecer uma forma confiável de comprovar permissão.
- [ ] Melhorar histórico e transparência pública do resultado sem expor dados desnecessários.
- [ ] Ampliar testes E2E, acessibilidade e estados de erro da interface.
- [ ] Documentar política de privacidade e retenção dos participantes.

## Fora de escopo

- Bot de respostas no chat.
- Votos, subs, follows ou pesos por ação.
- Entrada manual de participantes.
- Overlays e editor para OBS.
- Reroll do mesmo evento.
