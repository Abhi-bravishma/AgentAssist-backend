# Full Codebase Review — 2026-08-10

Four parallel audits over every package: controllers/DTOs/models, services/security,
AI/RAG/config-registry, portal/tests/build. Findings verified by grepping for real
callers — "dead" means zero callers in main AND test code.

## TIER 1 — Real bugs (fix these first)

| # | Finding | Where | Impact |
|---|---------|-------|--------|
| 1 | **Every agent message is dropped from conversation history** — the map ternary emits the literal string `"agent"` with no text | `ConversationProcessingService` history builder | The AI never sees anything the agent said; poisons analysis, intent detection, suggestions. THE known agent-turn bug — highest value fix in the codebase |
| 2 | **The React portal never ships** — no build step packages `portal/dist` into the WAR; only legacy `admin.html` is served in production | `pom.xml` (no frontend plugin) | All portal work is dev-server-only until packaged |
| 3 | **Security is decorative** — `permitAll` on everything, JWT filter injected but never wired into the chain, CORS `*` with credentials, `CORS_ALLOWED_ORIGINS` key read by nothing | `SecurityConfig`, `CorsConfig`, `JwtAuthFilter` | Docs claim JWT-protected endpoints; nothing is protected. Auth stack (controller, entities, filter) mints tokens nobody validates |
| 4 | **Salesforce TLS verification disabled by default** and no yaml key to enable it | `SalesforceConfig.skipSslVerification = true` | Customer PII fetched over MITM-able connections in any environment |
| 5 | **Secrets committed**: Avaya client secret as yaml default, DB root/root hardcoded (no env placeholder on url/user/pass), JWT default secret, RAG API key + Postgres password in docker-compose | `application.yaml`, `docker-compose.yml` | In git history; deploys that forget env vars silently run with them |
| 6 | **Qdrant outage masquerades as "no documents found"** — search returns empty on timeout/error, suggestion service then asks the LLM to answer WITHOUT grounding | `AgentAssistVectorSearchService` → `RagGateway` → suggestion prompt | During a KB outage agents get confident invented answers, not an error |
| 7 | **Classifier sanitizer strips digits** — `replaceAll("[^A-Z_]", "")`, but the portal allows codes like `TIER2_SUPPORT` | `IntentClassifier` vs `RegistryAdminService.CODE_PATTERN` | Digit-bearing registry intents can never be detected; silent GENERAL |
| 8 | **Checklist cache keyed by interactionId only** — same interaction with a different projectName serves the other project's cached Salesforce data + prompt | `ChecklistCacheService` | Cross-project data leak path |
| 9 | **Login failures return HTTP 500** (bare RuntimeException, no @ControllerAdvice anywhere); duplicate registration → 500 | `AuthController`/`AuthService` | Broken auth contract |
| 10 | **`@Transactional` holds a DB connection across 20–60s of LLM/RAG/Salesforce calls** | `processMessage` | Pool exhaustion under modest concurrency |
| 11 | **Language-name answers become `und`** for everything except Chinese ("Indonesian" → und → translation silently skipped) | `LanguageSupport.normalizeLanguageTag` | The exact bug class fixed for Chinese, still open for all other languages (bites hardest with Ollama) |
| 12 | **"Current sentiment" endpoint often scores the agent's own reply** (last message of ANY sender); the two sentiment endpoints are copy-paste twins each burning a full analysis LLM call for one number, with different labeling rules | `AnalysisController` | Wrong numbers + wasted tokens |
| 13 | **Docker build broken** — Dockerfile copies `agentassist.war`, Maven produces `target/agentassist-0.0.1-SNAPSHOT.war`; compose also has typo'd `SALESFORCE_exiBASE_URL` and sets only legacy-RAG env vars while internal-RAG vars are absent | `Dockerfile`, `docker-compose.yml` | `docker-compose build` cannot succeed |
| 14 | **One golden test self-heals** — missing fixture is silently rewritten and passes (the other two golden classes correctly fail) | `GoldenPromptRegressionTest.compareOrCapture` | The byte-identity gate has a hole |
| 15 | **ComplianceAnalyzer NPEs before its own null guard** (logs `agentMessages.size()` above the check); same log-before-try NPE pattern in every ai/support analyzer | `ai/support/*` | Null input → 500 instead of documented fallback |

## TIER 2 — Dead weight (safe to delete, verified zero callers)

- **Entire KnowledgeArticle system**: 8 endpoints + 7 classes + `knowledge_article`
  table. Called by nothing (portal uses `KnowledgeBaseController`); articles never
  reach RAG. Largest single deletable surface.
- **`PolicyCacheService`** — whole class, zero calls; plus unused injected
  `salesforceClient`/`policyCacheService` in `ConversationProcessingService`, the
  always-null `policyData` field and its unreachable customer-name branch.
- **Dead DTO triangle**: `ConversationDto` + `MessageDto` + `MessageMapper` (orphans
  of a documented-but-nonexistent endpoint). Plus `ConversationSummaryResponse` +
  `MessageService.getDBSummary` + 4 repository methods behind it.
- **Dead overload chains**: 2× `processMessage`, 3× `buildReplySuggestionsWithRag`,
  2× `buildChecklistContext`, regenerate/followup 1-arg variants, 2 "backward
  compatible" `ChecklistContext` constructors.
- **~15 dead public methods** across MessageService, AnalysisService,
  SalesforceClient, BaseAiProvider (`getProviderName`), FileStorageService,
  SettingsService (`require` test-only), IntentRegistryService (`allowedIntents`
  test-only), repositories.
- **Legacy remote RAG lane** (`RagClient`, `ragWebClient` against a stale IP) fully
  constructed every boot but unreachable with `rag.mode=internal` — gate on
  `@ConditionalOnProperty` or delete.
- **pom.xml**: `spring-retry` (zero usages), explicit `jackson-databind`
  (BOM-managed), provided `jakarta.servlet-api`, commented tomcat block; `webflux`
  only for WebClient (RestClient would drop Netty + the servlet-mode workaround).
- **`admin.html` + `AdminPortalController`** — duplicate of DocumentsPage, BUT it is
  currently the only UI that ships (Tier 1 #2). Delete only after portal packaging.
- **Misc**: `RagSearchFilter`, unread Avaya DTO fields, `ArticleType` enum,
  zero-byte `checklist.none.txt` fixture, tracked `tsconfig.tsbuildinfo`, stray
  `nul` file, dead `AiConfig.temperature` keys (real one lives in `spring.ai.*`),
  dead `InternalRagProperties.FileStorage` nested class, dead
  `cors.allowed-origins` yaml key.

## TIER 3 — Duplication / better approach

- **Suggestion-translation loop ×5** (pipeline + AnalysisService×3 + filtered-intent)
  — one helper collapses them; §4.9 had to be fixed in 5 places because of this.
- **try/catch+cleanJson+fallback ×8** in `ai/support` — one `callAndParse` helper
  would also fix the NPE-before-try and null-content gaps once instead of 8 times.
- **fetch/toast/setBusy ×15** in portal — one `useAsyncAction` hook; would also add
  the missing timeout/abort (today a hung backend pins buttons in "Saving…" forever).
- **Mobile masking ×6** (helper exists in SalesforceClient, inline copies drifted).
- **Transcript builder ×2** in AnalysisService with a no-op null filter (emits
  literal "Customer: null" when both texts are null).
- **Reply-language resolution ×2** (pipeline computes it, AnalysisService recomputes
  with an extra SELECT per call).
- **WebClient config ×2** (Rag + Salesforce) — shared factory = one place to fix TLS.
- **Cache inconsistency**: TTL constant copy-pasted ×4; `IntentRegistryService`
  uncached (~5 DB queries per message); `TtlCache` never evicts expired entries and
  has no single-flight.
- **`isGreetingOrSimpleMessage`**: 83 lines of hardcoded English keyword tables in
  the pipeline — belongs in the registry; skips the KB for any 2-word message.
- **No `@ControllerAdvice`**: 11 hand-rolled error maps in some controllers, raw
  500s in the rest; portal's `api.ts` papers over both shapes.
- **`/api/v1/{interactionId}/...` catch-all** absorbs typo'd URLs at the API root
  (e.g. `GET /api/v1/knowledge-base/summary` → 200 with a fake summary).
- **Compliance rows grow forever** (every check INSERTs; nothing updates/deletes).

## TIER 4 — Wasted work per request ("loops")

- Full pipeline (intent LLM + up to 4 Salesforce calls + analysis LLM) runs for
  AGENT messages, then the result is discarded (agents get no suggestions).
- Checklist cache prevents nothing — detection + Salesforce fetch run on every
  message anyway; the cache only carries context into later GENERAL turns.
- Extra Qdrant `getCollectionInfo` round-trip before every search (info-log only,
  up to +30s tail latency).
- Full telco plan catalogue (49 plans) fetched on every telco message.
- `getTelcoPlanById` fires the same HTTP GET twice on the fallback path.
- Sentiment endpoints run full analysis (sentiment+summary+suggestions) and keep
  one number.
- JWT parsed + signature-verified twice per request.
- `listDocuments` scrolls the entire Qdrant collection per page request.

## TIER 5 — Test gaps

- **Zero tests on the runtime path**: ConversationProcessingService,
  AnalysisService, all 12 controllers (no @WebMvcTest), SalesforceClient,
  ComplianceService, auth. Everything tested is registry/prompt/DTO.
- Midnight flake: `CustomerTelcoDataValidityTest` captures `LocalDate.now()` at
  class init while production recomputes it (needs injected Clock).
- Brittle seed-count assertions (`assertEquals(6, ...)`, `assertEquals(4, ...)`,
  `endsWith("ROOM_BOOKING")`) break on ANY seed addition.
- `RegistryGoldenPromptTest` re-implements provider formatting in test code for the
  `ai.*` fixtures GoldenPromptRegressionTest already covers end-to-end.
- Two tests assert less than their names claim (`missingRowFallsBackToTheBootstrap
  Property` tests the opposite; `...RequireThrows` never asserts the throw).
- Golden fixture paths are CWD-relative (silently regenerate under a different
  runner, combined with the self-heal hole).

## Suggested order of attack

1. **Agent-message drop** (Tier 1 #1) — one line, biggest quality win; behavior
   change is the point.
2. **Portal packaging** (frontend-maven-plugin → WAR) — makes all Part 5 work real;
   then delete admin.html.
3. **Security decision** — either enforce JWT (wire the filter, fix 401s, scope
   CORS) or delete the decorative auth stack and document the app as
   perimeter-secured. Present state is the worst of both.
4. **Grounding honesty** — surface Qdrant failures instead of "no documents";
   fix classifier digit-stripping; checklist cache key.
5. **Dead-code purge** (Tier 2) — mechanical, zero behavior change, shrinks the
   codebase substantially.
6. **Consolidation helpers** (Tier 3) + **efficiency** (Tier 4) as opportunistic
   follow-ups.
