## Plan
- Audit BlazeEventHub codebase line by line.
- Identify bugs, potential issues, and areas for improvement.
- Follow the orchestrator-mode strictness (HEAVY tier: multiple criteria, verification gate).
- Generate a comprehensive to-do list (audit report) and save it in the project vault.

## Success criteria + QA scenarios
1. **Audit Completion:** Codebase is analyzed across all layers (Frontend, API, Auth, Business, DB/Persistence).
2. **Report Generation:** A markdown report is saved to `C:\Coisas\vault\Blaze Event Hub\orquestrador\auditoria-completa-2026-07-24.md`.
3. **Actionable TODOs:** Report contains clear, actionable items prioritized by severity.

## Now
- Running RED->GREEN->SURFACE verification (Maven compile+tests, NPM build) in parallel.
- Committing the changes if the build passes.

## Todo
- Resolve any test failures or push the commit.

## Findings
- Project builds and tests pass locally.
- Build system: Maven via custom wrapper invocation (due to MSYS2/wrapper issues). Java 21. Spring Boot 3.5.14.
- Checkstyle has 2405 violations (mostly Javadoc, LineLength, HiddenField, MagicNumber, FinalParameters). This indicates a lack of strict lint enforcement in the past or a recent introduction of checkstyle.

### Audit Wave 1 (Subagents 1-3)
**Auth/Security Layer:**
1. HIGH: Global Concurrency Bottleneck in Token Refresh (`PersistentOAuthCredentialService.currentValid` uses method-level `synchronized`).
2. MEDIUM: Race Condition in OAuth Credential UPSERT (`JdbcOAuthCredentialStore.save` lacks `@Transactional` on the fallback `INSERT`).
3. MEDIUM: CSRF False Positives Behind Reverse Proxy (`BrowserSecurityFilter.sameOrigin` fails if reverse proxy termination is used without ForwardedHeaderFilter).
4. LOW: Inefficient Key Decoding (`AesGcmCredentialCipher.requireKey()` decodes key on every call instead of constructor).
5. LOW: Missing `@Transactional` Boundary on Disconnect (`BlazeOAuthService.disconnect()`).

**Event/Business Logic Layer:**
1. HIGH: Extreme Lock Contention on Chat Capture (`EventParticipantCaptureService.capture` takes row-level lock `findByIdForUpdate` on the `events` table per message, bottlenecking all chats).
2. HIGH: N+1 Queries in Chat Capture (`calculateEntries` hits DB for rule/tiers per chat message).
3. HIGH: N+1 Queries in Event Listing (`EventService.listEvents` queries rules per event mapped).
4. HIGH: In-Memory Sorting Risk (`DrawService.executeDraw` sorts large lists in-memory causing GC spikes, instead of trusting DB sorting).
5. MEDIUM: Race condition in capture (`raw_action_count + 1` logic) if the row lock is ever removed to fix the contention issue.
6. MEDIUM: Missing `@Transactional` on OAuth Update (`MemberService.createOrUpdateFromOAuth`).

**WebSocket/Integration Layer:**
1. HIGH: Socket.IO Connection Leak (`BlazeSocketIOClient` map `sockets` retains disconnected entries if connection drops naturally due to missing cleanup in `EVENT_DISCONNECT`).
2. HIGH: Missing Re-Subscription on Reconnect (`BlazeSocketIOClient` fires `EVENT_CONNECT` repeatedly but never offloads old subscriptions, leading to duplicate states).
3. HIGH: Polling Global Rate Limit Missing (`BlazeChatPollingService.pollTargets` hits `BlazeApiClient` sequentially without yield, spamming API if targets > 10).
4. MEDIUM: No Error Handling for Socket Token Expiry (Blind reconnect loop with expired token).
5. MEDIUM: Unsafe REST Error Translation (`BlazeApiClient` does not catch `ResourceAccessException`, missing connection timeout handling).

## Learnings
- The Maven wrapper (`mvnw`) is broken in this specific MSYS2 environment (`mvnw: command not found`), and `mvnw.cmd` does not work directly. The workaround is invoking the `plexus-classworlds` launcher directly with the downloaded Maven binaries, which works perfectly for compilation, testing, and packaging.