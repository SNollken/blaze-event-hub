# AGENTS

- Produto: NollenBlaze.
- Package Java principal: `com.nollen.blaze`.
- Nome historico `BlazeBot` so pode aparecer em contexto historico ou de migracao; nao usar como nome novo de aplicacao, package, classe, endpoint ou branding.
- Vault oficial nesta maquina: `C:\Users\sofia\OneDrive - SENAC DF\Drive\vault\NollenBlaze`.
- Nao commitar vault, prints, logs, `target`, `.env`, cookies, storage state, `.hermes`, temporarios ou credenciais.
- Nao commitar `clientSecret`, `accessToken`, `refreshToken`, cookies ou qualquer segredo real.
- Usar Java 21 sem alterar PATH global. Preferir `JAVA_HOME` apenas por processo/sessao.
- Usar Maven Wrapper. Nao instalar Maven global para este projeto.
- Microfeatures futuras devem ir para branch longa de feature e so integrar blocos completos e validados.
- Manter historico Git limpo. Evitar merge commit desnecessario.
- Tela MVP 1 de dashboard e provisoria e funcional. Nao gastar tempo refinando UI sem pedido explicito.
- Design final do dashboard vira depois no OpenDesign/opencode.
- Nao abrir PR automatico. Commits e pushes seguem a branch combinada.
- Features devem usar `feature/*` e integrar direto em `dev` quando validadas.
