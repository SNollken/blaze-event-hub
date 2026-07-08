# AGENTS

- Produto: Blaze Event Hub.
- Package Java principal: `com.blaze.eventhub`.
- Nome historico `NollenBlaze` / `BlazeBot` so pode aparecer em contexto historico ou de migracao; nao usar como nome novo de aplicacao, package, classe, endpoint ou branding.
- Vault oficial nesta maquina: detectar entre `C:\Users\sofia\OneDrive - SENAC DF\Drive\vault\Blaze Event Hub` e `C:\Users\sofia57152576\OneDrive - SENAC DF\Drive\vault\Blaze Event Hub`; neste host, usar `C:\Users\sofia57152576\OneDrive - SENAC DF\Drive\vault\Blaze Event Hub`.
- Nao commitar vault, prints, logs, `target`, `.env`, cookies, storage state, `.hermes`, temporarios ou credenciais.
- Nao commitar `clientSecret`, `accessToken`, `refreshToken`, cookies ou qualquer segredo real.
- Usar Java 21 sem alterar PATH global. Preferir `JAVA_HOME` apenas por processo/sessao.
- Usar Maven Wrapper. Nao instalar Maven global para este projeto.
- Microfeatures futuras devem ir para branch longa de feature e so integrar blocos completos e validados.
- Manter historico Git limpo. Evitar merge commit desnecessario.
- Frontend React + Vite em `frontend/`, servido pelo Spring Boot via `SpaController`.
- Porta padrao: 9090.
- API key header: `X-Nollen-Api-Key` (mantido por compatibilidade).
- Design final do dashboard continua reservado para OpenDesign/opencode.
- Nao abrir PR automatico. Commits e pushes seguem a branch combinada.
- Features devem usar `feature/*` e integrar direto em `dev` quando validadas.
