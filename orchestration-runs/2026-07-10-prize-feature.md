# Orquestração: Feature "Prêmio do Evento" — Blaze Event Hub

Data: 2026-07-10
Branch: feature/blaze-event-hub-mvp
Modo: fable-mode + ultracode-workflow (orquestração com subagentes paralelos)

## Descoberta (Stage 1 — ENTENDER)
- Backend JÁ suporta prêmios 100%:
  - `Event.java`: campos `prizeType`, `prizeDescription`.
  - `CreateEventRequest.java` / `UpdateEventRequest.java`: `prizeType` (max 80), `prizeDescription` (max 2000).
  - `EventResponse.java`: `prizeType`, `prizeDescription` expostos.
  - `JdbcEventStore.java`: MERGE e SELECT incluem `prize_type`/`prize_description`.
  - `EventService.java`: create/update já propagam os campos.
  - `schema.sql`: `prize_type VARCHAR(80)`, `prize_description CLOB`.
- GAP exclusivamente FRONTEND (4 arquivos):
  - `CreateEvent.tsx`: não tem UI nem envia prizeType/prizeDescription.
  - `EditEvent.tsx`: não carrega nem envia.
  - `EventDetail.tsx`: não exibe.
  - `translations.ts`: não tem as chaves de prêmio (pt-BR/en/es).

## Mapa de Estágios
- Stage 2 [subagente 1]: `translations.ts` — adicionar chaves em pt-BR E en (type-check exige en cobrir pt-BR via `EnglishCoversPortuguese`).
- Stage 3 [subagente 2]: `CreateEvent.tsx` — estado + UI (select tipo + descrição) + payload.
- Stage 4 [subagente 3]: `EditEvent.tsx` (carregar/enviar) + `EventDetail.tsx` (exibir).
- Stage 5 [verificação]: `npm run build` (tsc -b && vite build) deve passar; `npm test` (vitest) opcional.
- Stage 6 [relatório]: commit, push, vault.

## Contrato de chaves i18n (FONTE ÚNICA — subagente 1 cria, 2 e 3 SÓ consomem)
IMPORTANTE: `TranslationKey = keyof typeof translations['pt-BR']` e o type
`EnglishCoversPortuguese` exige que TODA chave de pt-BR exista em en. Por isso
cada chave abaixo DEVE ser adicionada em pt-BR E en. es é opcional (não faz parte
do type `Lang`).

Lista final (pt-BR / en / es):
- prizeSection:        "Prêmio" / "Prize" / "Premio"
- prizeType:           "Tipo do prêmio" / "Prize type" / "Tipo de premio"
- prizeTypePh:         "Ex: Bits, Pix, Steam Key" / "Ex: Bits, Pix, Steam Key" / "Ej: Bits, Pix, Steam Key"
- prizeTypeOther:      "Outro" / "Other" / "Otro"            (label da opção "outro")
- prizeTypeOtherPh:    "Descreva o tipo do prêmio" / "Describe the prize type" / "Describe el tipo de premio"
- prizeDescription:    "Descrição do prêmio" / "Prize description" / "Descripción del premio"
- prizeDescPh:         "O que o ganhador vai receber e como será entregue" / "What the winner gets and how it is delivered" / "Qué recibe el ganador y cómo se entrega"
- prizeOptBits:        "Bits" / "Bits" / "Bits"
- prizeOptPix:         "Pix" / "Pix" / "Pix"
- prizeOptSteam:       "Steam Key" / "Steam Key" / "Steam Key"
- prizeOptGiftcard:    "Gift Card" / "Gift Card" / "Gift Card"
- prizeOptPhysical:    "Produto físico" / "Physical item" / "Artículo físico"

Opções do select prizeType (value → labelKey):
  bits → prizeOptBits
  pix → prizeOptPix
  steam → prizeOptSteam
  giftcard → prizeOptGiftcard
  physical → prizeOptPhysical
  outro → prizeTypeOther   (selecionar "outro" revela input livre prizeTypeOther)

Semântica de envio (Create/Edit):
  prizeType = (select === 'outro') ? prizeTypeOther.trim() : (select || undefined)
  prizeDescription = prizeDescription.trim() || undefined

## Regras para subagentes
- NÃO mexer em backend (não precisa).
- Subagente 2 e 3 NÃO editam translations.ts — usam SÓ as chaves acima.
- Manter estilo do projeto: classes CSS `.form-section`, `.form-label`, `.form-field`, `.card`, `.section-label`, `t('...')`.
- prizeType enviado: se "outro", usa o texto livre (trim); se vazio, undefined.
- prizeDescription enviado: trim() || undefined.
- Em EditEvent, detectar "outro": se event.prizeType não está nas opções conhecidas, selecionar "outro" e preencher o texto livre com o valor.
- Em EventDetail, exibir seção "Prêmio" somente se prizeType ou prizeDescription existirem.
