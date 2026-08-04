# Patterns React/TypeScript Moderno — Síntese para Decisão

**Contexto:** projeto React + Vite + TypeScript, sem Next.js. Foco em **qual usar**, não tudo.

## 1. Server vs Client Components (mesmo sem Next.js)

- **React 19 Server Components são estáveis** mas requerem bundler/framework com RSC. No Vite puro, **não há RSC pronto** ([react.dev](https://react.dev/reference/rsc/server-components)).
- **Decisão Vite SPA:** tratar tudo como Client Component. RSC só entra via TanStack Start ou RR v7 Framework Mode. **Em SPA interno, não vale a pena** — overhead sem ganho.

## 2. Roteamento (escolha única)

- **React Router v7 (Data Mode)** é o padrão 2025: `createBrowserRouter` + `RouterProvider` + loaders/actions ([reactrouter.com](https://reactrouter.com/start/data/installation)). Substituiu Remix, file-based opcional via `@react-router/fs-routes`.
- **TanStack Router** é a alternativa type-safe, com search-params validados por schema. Vale a pena se você já usa TanStack Query/Table.
- **Decisão Vite SPA:** RR v7 Data Mode para 90% dos casos. TanStack Router só se type-safety end-to-end for requisito.

## 3. State Management — 3 camadas

| Escopo | Ferramenta | Quando usar |
|---|---|---|
| **Server state** | TanStack Query v5 | Qualquer fetch, cache, revalidação, optimistic updates ([tanstack.com](https://tanstack.com/query/latest/docs/framework/react/overview)) |
| **Global client** | **Zustand** | UI cross-page (tema, modais, carrinho). Hook store, sem provider, ~1KB ([github.com/pmndrs/zustand](https://github.com/pmndrs/zustand)) |
| **Atômico/derivado** | **Jotai** | Estado granular, evita re-renders. 2KB core ([jotai.org](https://jotai.org/docs/introduction)) |
| **Local** | `useState`/`useReducer` | Form fields, toggles — sem dependência |

- **Decisão:** comece só com `useState` + TanStack Query. Zustand só quando 2+ componentes não-irmãos precisam do mesmo estado. Jotai se profiler mostrar re-renders.

## 4. Forms: React Hook Form + Zod/Valibot

- **React Hook Form** é padrão: uncontrolled, `register()`, mínima re-renderização ([react-hook-form.com](https://react-hook-form.com/get-started)).
- **Validação schema:** use o **mesmo schema no form e na API** (Zod ou Valibot).
  - **Zod**: ecossistema maduro, inferência de tipos, padrão de fato.
  - **Valibot**: bundle até **95% menor** que Zod, mesma DX ([valibot.dev](https://valibot.dev/)). Vale a pena em apps com budget de KB crítico (mobile, serverless edge).
- **Decisão:** Zod para maioria. Valibot se você medir bundle e se importa com <1KB.

## 5. UI Components: shadcn/ui + Radix

- **shadcn/ui** não é lib: você **copia o código** para `src/components/ui/`. Tailwind + Radix por baixo ([ui.shadcn.com](https://ui.shadcn.com/docs/installation/vite)). Setup Vite oficial.
- **Decisão:** use em Vite. Componentes customizáveis, sem dep runtime. **Tailwind é pré-requisito.**

## 6. Data Fetching

- **TanStack Query v5** é mandatório em Vite SPA: cache, retry, dedupe, stale-while-revalidate, optimistic updates.
- **tRPC** com Vite puro = fricção alta. Pule.
- **Decisão:** TanStack Query + `fetch` no client. `useSuspenseQuery` com React 19 Suspense para loading limpo.

## Stack recomendada (Vite + TS, sem Next.js)

```
Vite + React 19 + TypeScript
├── Roteamento: react-router v7 (Data Mode)
├── Server state: @tanstack/react-query v5
├── Global state: zustand (se necessário)
├── Forms: react-hook-form + zod
├── UI: shadcn/ui (Tailwind + Radix)
└── Validação: zod (ou valibot se bundle crítico)
```

**Skipped:** Redux Toolkit, tRPC sem backend, Jotai até precisar, Context API global, Server Components sem framework RSC-ready.

**Add when:** TanStack Router se type-safety em rotas virar requisito; Valibot se bundle > 200KB e budget < 100KB; Jotai se profiler mostrar re-renders evitáveis.

## Fontes (8 URLs reais verificadas)

1. https://react.dev/reference/rsc/server-components — RSC estável React 19
2. https://reactrouter.com/start/data/installation — RR v7 Data Mode setup
3. https://tanstack.com/query/latest/docs/framework/react/overview — Query v5 overview
4. https://react-hook-form.com/get-started — RHF quick start
5. https://github.com/pmndrs/zustand — Zustand README, sem provider
6. https://jotai.org/docs/introduction — Jotai atomic state, 2KB
7. https://ui.shadcn.com/docs/installation/vite — shadcn/ui Vite setup
8. https://valibot.dev/ — Valibot modular, até 95% menor que Zod
