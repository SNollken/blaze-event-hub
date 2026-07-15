# Arquitetura do Blaze Event Hub

## Objetivo

O sistema centraliza giveaways da Blaze.stream. O criador define o evento e o comando de entrada; o backend captura identidades do chat, congela o pool quando o evento é finalizado e persiste um único resultado com registro técnico.

## Fluxo principal

1. **OAuth:** a Blaze entrega `code` e `state`; o backend valida a sessão, troca o código e persiste a credencial cifrada.
2. **Criação:** o backend deriva o slug do membro autenticado, valida a credencial OAuth persistente e resolve automaticamente o canal da conta Blaze conectada; o browser não escolhe o canal.
3. **Abertura:** o evento passa de `draft` para `open` e habilita a captura.
4. **Captura automática:** a cada ciclo, o backend lê o chat, normaliza as mensagens e registra uma entrada por usuário Blaze.
5. **Finalização:** o evento entra em `finalizing` com um cutoff persistido; uma última sincronização aceita apenas mensagens até esse instante antes de congelar quantidade e SHA-256 do pool.
6. **Sorteio:** o backend trava o evento, confere o snapshot, escolhe uniformemente um participante e grava o resultado antes de marcar o evento como `completed`.

## Componentes

| Área | Responsabilidade |
|---|---|
| `frontend/` | SPA React/Vite para descoberta e gestão dos giveaways |
| `oauth` | PKCE, state por sessão, troca/refresh e credenciais persistentes cifradas |
| `member` | Identidade local vinculada ao usuário Blaze |
| `blaze` | Cliente REST, headers autenticados e resolução segura de canal |
| `event` | Rascunho, edição, abertura, finalização, cancelamento e visibilidade |
| `event.participant` | Parsing, comando exato, deduplicação e hash do pool |
| `events` | Polling de chat e cursor persistente por criador/canal |
| `event.draw` | Sorteio uniforme, auditoria e idempotência do resultado |
| `common` | Autenticação da API, CSRF, headers, erros e IDs |
| `health` / `status` | Sinais operacionais sem exposição de segredos |

O `SpaController` entrega o build do frontend pelo Spring Boot em produção. No desenvolvimento, o Vite usa proxy para o backend na porta `9090`.

## Modelo de estados

```text
draft --open--> open --finalize--> finalizing --sync--> closed --draw--> completed
  |                 |
  +----cancel-------+-----------------------------------------------> cancelled
```

Regras centrais:

- somente o criador altera, abre, finaliza, cancela ou sorteia seu evento;
- ao criar, o canal vem exclusivamente do perfil e da credencial persistente do membro autenticado; ID de usuário e ID de canal são identidades distintas;
- rascunhos só são visíveis ao dono;
- um canal não pode ter dois giveaways em captura ao mesmo tempo;
- um usuário Blaze e uma mensagem só geram uma entrada por evento;
- `messages: []` representa chat vazio; payload nulo ou incompatível interrompe a sincronização sem fingir sucesso;
- durante `finalizing`, apenas mensagens enviadas até o cutoff persistido são aceitas;
- entradas posteriores ao cutoff ou ao fechamento são rejeitadas;
- o sorteio só usa um pool cujo total e hash ainda correspondem ao snapshot;
- `event_id` é único nos resultados, portanto não existe reroll pela API.

## Persistência e Flyway

As migrations comuns criam:

- `members`;
- `events`;
- `oauth_credentials`;
- `event_participants`;
- `event_draw_results`;
- `chat_polling_cursors`.

O perfil `local` usa H2 em modo compatível com PostgreSQL. O perfil `prod` usa PostgreSQL e acrescenta a migration de hardening para Supabase, com RLS e revogação de acesso dos papéis públicos.

Flyway é o único dono do versionamento do schema. Em um banco novo, o primeiro start do app cria `flyway_schema_history` e aplica todas as migrations comuns e PostgreSQL em ordem de versão. Um schema preenchido externamente sem histórico exige baseline planejado; não deve ser corrigido com SQL manual improvisado.

## Concorrência e idempotência

- `SELECT ... FOR UPDATE` serializa captura versus fechamento e sorteios concorrentes.
- Updates de estado possuem condição no `WHERE`, evitando transições sobre estado antigo.
- Constraints únicas protegem participante, mensagem, captura ativa e resultado.
- O cursor reduz reprocessamento; as constraints tornam reentrega segura.
- Backfills extensos persistem cursor de continuação e âncora. A âncora só é promovida após alcançar o cursor anterior ou o início comprovável do evento, seguida por uma nova leitura do topo.
- Locks em memória serializam polling e sincronização final de um mesmo alvo em uma instância.

Enquanto não houver lock distribuído, a implantação deve usar uma única réplica responsável pelo polling.

## Segurança

- O browser autentica por sessão, não por API key exposta no bundle.
- O header `X-Nollen-Api-Key` é compatibilidade restrita a chamadas server-to-server.
- Cookies de produção são `HttpOnly`, `Secure` e `SameSite=Lax`.
- Mutações autenticadas por sessão exigem `Origin`, `Sec-Fetch-Site` ou `Referer` de mesma origem.
- CSP, HSTS em HTTPS, anti-clickjacking e políticas de conteúdo são aplicados por filtro.
- Access e refresh tokens ficam cifrados por AES-256-GCM; a chave permanece fora do banco.
- Erros remotos são sanitizados antes de chegar ao cliente.
- Rascunhos, participantes e dados do criador respeitam ownership no backend.

## Deploy

A imagem possui três estágios:

1. Node 20 executa `npm ci` e gera o frontend;
2. Java 21 usa o Maven Wrapper para empacotar o Spring Boot com os assets;
3. somente JRE e o JAR chegam ao runtime, executado por usuário sem privilégios.

O container ativa `prod` por padrão e respeita `PORT` do Render. Banco, OAuth e chave de criptografia entram apenas como variáveis de runtime.

## Limites atuais

- A API oficial oferece cursor, até 100 mensagens por página e a janela da sessão ao vivo atual ou de cerca de 8 horas offline. O polling percorre páginas até o cursor persistido ou o início do evento; se atingir o teto por ciclo, persiste um checkpoint e retoma dali. A finalização permanece bloqueada até uma leitura adicional do topo comprovar cobertura contínua.
- Se o histórico terminar antes do cursor anterior ou fora da janela conservadora configurada, o estado vira `CHAT_HISTORY_GAP`/`DEGRADED` e o pool não é congelado silenciosamente.
- Finalizações interrompidas voltam a `open` pelo mecanismo de recuperação; isso deve ser observado em produção.
- Escala horizontal do polling requer coordenação distribuída.
- OAuth real, rotação de secrets, Supabase e deploy precisam de smoke E2E fora do repositório.
