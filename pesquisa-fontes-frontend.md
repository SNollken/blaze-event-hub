# Pesquisa Tier 1 — Front-end (Vercel / Next.js / web.dev / W3C)

## URLs verificadas (8 reais, fetched 2026-08-03 via r.jina.ai)

1. https://skills.sh/ — Vercel Labs Agent Skills Directory ("The Open Agent Skills Ecosystem", install com `npx skills add <owner/repo>`)
2. https://nextjs.org/docs/app/getting-started/css — Next.js 16.3 App Router CSS (navegação)
3. https://nextjs.org/docs/app/getting-started/images — Next.js Image Optimization
4. https://nextjs.org/docs/app/getting-started/fonts — Next.js Font Optimization
5. https://nextjs.org/docs/app/api-reference/components/image — Next.js Image component API (alt required, width/height para aspect ratio, fill prop)
6. https://vercel.com/blog — Vercel blog (Vercel Agent, framework-defined infrastructure)
7. https://vercel.com/blog/framework-defined-infrastructure — Vercel engineering blog (framework abstractions mapeiam infra automaticamente)
8. https://vercel.com/geist/introduction — Geist Design System ("Vercel design system for building consistent web experiences", Foundations: Colors, Typography, Materials; 30+ Components)
9. https://web.dev/articles/lcp — Largest Contentful Paint (Core Web Vital, threshold 2.5s, 75th percentile)
10. https://web.dev/articles/cls — Cumulative Layout Shift (threshold 0.1, layout shift score = impact fraction × distance fraction)
11. https://web.dev/articles/inp — Interaction to Next Paint (responsiveness, 90% do tempo pós-load)
12. https://www.w3.org/WAI/WCAG22/quickref/ — WCAG 2.2 quickref (1.4.3 Contrast Min, 1.4.11 Non-text Contrast, 2.1.1 Keyboard, 2.4.7 Focus Visible, 2.5.5 Target Size)

## Citações literais por dimensão

### Dim 1 — Design Systems / UI Tokens
- "Vercel design system for building consistent web experiences" (Geist, vercel.com/geist/introduction)
- Foundations: Colors ("high contrast, accessible color system"), Typography, Materials
- Components: Avatar, Badge, Button, Checkbox, Combobox, Command Menu, Context Menu, Destructive Action Modal, Drawer... (30+)
- Skills.sh: "Skills are reusable capabilities for AI agents. Install them with a single command to enhance your agents with access to procedural knowledge" — modelo mental: skills = pacotes de conhecimento procedural reutilizável, não código runtime

### Dim 2 — Component Patterns / Composition
- Next.js Image: "The `alt` property is used to describe the image for screen readers and search engines. It is also the fallback text if images have been disabled or an error occurs" (api-reference/components/image)
- Next.js Image: "The `width` and `height` properties represent the intrinsic image size in pixels. This property is used to infer the correct **aspect ratio** used by browsers to reserve space for the image and avoid layout shift during loading"
- Padrão fill: "A boolean that causes the image to expand to the size of the parent element. The parent element **must** assign `position: relative, fixed, absolute`"
- Props table explícita: src (Required), alt (Required), width, height, fill, sizes, quality, priority, placeholder, loading, blurDataURL, decoding — composição explícita, sem magia

### Dim 3 — Performance / Core Web Vitals / SSR-SSG-ISR-RSC
- LCP (web.dev/lcp): "sites should strive to have Largest Contentful Paint of **2.5 seconds** or less. To ensure you're hitting this target for most of your users, a good threshold to measure is the **75th percentile** of page loads"
- LCP elements: img, image inside svg, video, element with background-image, block-level text nodes
- CLS (web.dev/cls): "Good CLS values are 0.1 or less. Poor values are greater than 0.25". "layout shift score = impact fraction × distance fraction"
- INP (web.dev/inp): "Chrome usage data shows that 90% of a user's time on a page is spent _after_ it loads". INP = "longest interaction observed, ignoring outliers" (1 per 50 descartado)
- Next.js docs estrutura: Getting Started → Server/Client Components, Caching, Revalidating, ISR, Cache Components, PPR — uma taxonomia explícita de rendering modes
- Vercel blog: "Framework-defined infrastructure abstracts over cloud primitives such as servers, message queues, and serverless functions, making them mere implementation details of the frameworks' concepts" — Next.js → infra automática, sem config manual

### Dim 4 — Acessibilidade / WCAG / Keyboard / ARIA
- Geist: "A high contrast, accessible color system" (claim oficial sobre tokens de cor)
- WCAG 2.2 quickref estrutura: 4 princípios (Perceivable, Operable, Understandable, Robust)
  - 1.1.1 Non-text Content (alt text)
  - 1.3.1 Info and Relationships (semântica)
  - 1.4.3 Contrast (Minimum) AA = 4.5:1 texto normal
  - 1.4.11 Non-text Contrast AA = 3:1 UI components
  - 1.4.13 Content on Hover or Focus (dismissable, hoverable, persistent)
  - 2.1.1 Keyboard (toda funcionalidade via teclado)
  - 2.4.7 Focus Visible (indicador visível)
  - 2.5.5 Target Size (Minimum) AAA = 44×44 CSS px
  - 2.5.8 Target Size (Enhanced) AAA = 24×24
- Next.js Image: "The `alt` property is used to describe the image for screen readers... if purely decorative, alt should be empty string (`alt=""`)"
