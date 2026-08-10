# Restructure — Master Plan & Live Status

> Single source of truth for the 5-part restructure. Update this file as parts
> complete. Full Part 1 spec + audit findings: [RESTRUCTURE_PART1_PLAN.md](RESTRUCTURE_PART1_PLAN.md).
> Branch: `restructure/part1-config-registry` (pushed to origin).

## The 5-part plan

| Part | Scope | Status |
|------|-------|--------|
| **1** | Prompt & config registry — ALL hardcoded prompts/checklists/intents/brand values into DB-backed versioned registry (`aa_*` tables), byte-identical behavior, golden-prompt tests as safety net | ✅ **DONE** |
| **2** | Split `BaseAiProvider` (1,454 lines) into focused components; classifier driven by `aa_intent` rows; remove `OperationType` enum → open string intent codes | ✅ **DONE** (2a `e05c9dd`, 2b `89269c3`, 2c `06d6b69`) |
| **3** | Pipeline/stage refactor of `ConversationProcessingService.processMessage()` + deferred language defects (§4.9–4.11 of Part 1 plan) | ✅ **DONE** (3a pipeline stages, 3b §4.9 sticky base language / §4.10 no silent und degrade / §4.11 aa_language display names, 3c TranslationService collapsed into LanguageService) |
| **4** | Internal RAG — agent-assist lane copied verbatim from bravishma-rag into this app (`com.agentassist.rag`, `RagGateway` internal/remote modes) | ✅ **DONE** (early, out of order) |
| **5** | Admin portal — separate React app (`portal/`, Vite+React18+TS, port 5174) | ✅ **DONE** — documents, prompts, settings + Registry tab (projects with whitelist matrix, intents with classifier-rule editor, brand attributes) via `/api/v1/admin/registry` |

## What "done" means (acceptance gates)

- **Byte-identity**: 40 golden fixtures in `src/test/resources/golden/` captured
  from pre-restructure code. Any prompt drift fails the build. 101/101 tests green.
- Registry resolution: highest PUBLISHED (key, project) → (key, NULL); 60s TTL
  caches; fail-loud `ConfigRegistryException` (never silent empty prompts).
- New intent = pure data: one `aa_intent` row flows through classifier prompt,
  detection, project gating, checklist lookup, filtered messages — zero Java.

## Remaining work (in order)

1. **Agent-turn-dropping in conversation history** — separate task
2. **Ollama hookup** — ON HOLD until server available. Config ready:
   `ai.active_provider=ollama` setting + collection `pdf_docs_new_mxbai-embed-large`
   + base-url; restore `minRelevanceScore` 0.55 for mxbai (0.40 is OpenAI-tuned)
3. **KB re-upload** (user's task) — docs into `agent_assist_docs_openai`
   (1536-dim, OpenAI text-embedding-3-small) on 74.225.250.214:6334; old mxbai
   collection is dimension-incompatible

## Standing constraints

- Never modify: `SecurityConfig`, `CorsConfig`, controller request/response
  contracts, DTOs. No security changes in any part.
- Never change prompt wording — byte-identical is the acceptance criterion;
  golden fixtures enforce it.
- One AI provider active at a time (`ai.active_provider` aa_setting,
  portal-switchable): OpenAI live, Ollama on hold.
- bravishma-rag repo is read-only reference (its agent-assist lane is frozen).
- Liquibase for all schema changes, every changeset with rollback; `aa_*`
  tables excluded from Hibernate ddl-auto via `AaSchemaFilterProvider`.
- Environment: backend :8085, portal :5174, MySQL local / Postgres in compose,
  Qdrant + everything else on the 74 server.
