**Goal:** Perform a comprehensive line-by-line code audit of Blaze Event Hub, identify bugs/improvements, and generate an actionable to-do list.

**Tier:** HEAVY (Requires deep analysis across multiple layers, parallel subagents, and a final synthesized report).

**Justification:** User explicitly requested a "linha por linha" complete audit, mentioning orchestrator mode.

**Success Criteria:**
1. Codebase is analyzed across Frontend, API, Auth, Business, and Persistence layers.
   - *Pass/Fail:* Subagents complete their runs and return structured findings.
2. A comprehensive markdown report is saved to the vault.
   - *Pass/Fail:* `ls "C:\Coisas\vault\Blaze Event Hub\orquestrador\auditoria-completa-2026-07-24.md"` returns the file.
3. The report contains an actionable, prioritized To-Do list.
   - *Pass/Fail:* Visual inspection of the report confirms structured TODOs.

**When to stop:** When the audit report is fully generated and saved to the vault, and no outstanding critical bugs require immediate hotfixing before reporting.