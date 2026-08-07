# Part 1 — Prompt & Config Registry: Summary

Branch: `restructure/part1-config-registry` · Plan: [docs/RESTRUCTURE_PART1_PLAN.md](docs/RESTRUCTURE_PART1_PLAN.md)
Acceptance: byte-identical prompts, proven by 40 golden fixtures · `./mvnw clean verify`: **82 tests, 0 failures**

## What moved where

| Was (hardcoded in Java) | Now |
|---|---|
| 13 prompt text blocks in `BaseAiProvider` (1,454 → 852 lines) | `aa_prompt_template` rows, rendered via `PromptService` |
| 7 checklist blocks + `resolveBankName/Hotline/LoanEmail` in `ChecklistService` (1,113 → 427 lines) | `aa_prompt_template` + `aa_brand_attribute` |
| `isOperationValidForProject()` switch | `aa_project_intent` rows via `IntentRegistryService` |
| `getFilteredIntentMessage()` switch + `NO_KNOWLEDGE_REPLY` | `aa_intent.filtered_message_template` + `system.no_knowledge_reply` template |
| `ai.provider` property (restart to switch) | `aa_setting` row `ai.active_provider`, runtime-switchable via `AiProviderFactory` |
| `describeLanguage()` Chinese special-casing | `aa_language` rows (inert until phase 1c switches callers) |

New package: `com.agentassist.configregistry` — entities, repositories, `PromptService`,
`BrandService`, `IntentRegistryService`, `LanguageRegistryService`, `SettingsService`,
`RegistryValidator` (startup check, logs a key table, never crashes the app) and a
`configRegistry` actuator health indicator (DOWN when unseeded/unreachable).

Schema/seed: Liquibase `db/changelog/changes/part1-config-registry.yaml` (7 tables,
per-changeset rollbacks) + `part1-seed.yaml`. Prompt content lives in
`db/changelog/changes/prompts/*.txt` (EOL-protected via `.gitattributes` — **never**
let these convert to CRLF) loaded with `valueClobFile`.

## Template keys (21 PUBLISHED v1 rows)

`ai.analyze_text`, `ai.analyze_conversation`, `ai.analyze_conversation_with_context`,
`ai.analyze_conversation_with_checklist`, `ai.translate_to_english`,
`ai.translate_from_english`, `ai.detect_language`, `ai.detect_operation`,
`ai.overall_sentiment`, `ai.regenerate_suggestions`,
`ai.regenerate_suggestions_with_context`, `ai.follow_up_check`, `ai.compliance`,
`checklist.fee_waiver` (default **and** SCB override — same key, project row),
`checklist.home_loan_closure`, `checklist.billing`, `checklist.policy`,
`checklist.claims`, `checklist.telco`, `system.no_knowledge_reply`.

Placeholders are `{snake_case}`; extra variables are ignored, missing ones throw
listing every name. Resolution: highest PUBLISHED version for (key, project), else
(key, NULL). 60s TTL cache; `POST /api/v1/admin/config/cache/evict` for instant pickup.

## Runtime provider switch

One provider active at a time. `GET /api/v1/admin/config/ai-provider` shows
active + available; `PUT` with `{"provider":"ollama"}` switches (validated against
availability first, 400 otherwise). Both beans always register; a deployment that
sets `ai.active_provider` to an absent provider fails loudly rather than silently
serving the other. **Note:** deployments that used the `AI_PROVIDER` env var now need
the DB row instead (seed is `openai`) — one `UPDATE aa_setting ...` or one PUT.

## How to add things via SQL (until the Part 5 portal)

```sql
-- New project (no aa_project_intent rows = ALL intents allowed)
INSERT INTO aa_project (code, display_name, active) VALUES ('HOSPITALITY', 'Hospitality Demo', true);

-- Restrict it to specific intents (rows present = whitelist)
INSERT INTO aa_project_intent (project_id, intent_id, enabled)
SELECT p.id, i.id, true FROM aa_project p, aa_intent i
WHERE p.code = 'HOSPITALITY' AND i.code IN ('BILLING');

-- Brand values (omit project_id for the global default; bank_name for an
-- unknown project falls back to the project code itself — see plan §4.6)
INSERT INTO aa_brand_attribute (project_id, attr_key, attr_value)
SELECT id, 'bank_name', 'Grand Hotel' FROM aa_project WHERE code = 'HOSPITALITY';

-- New intent (filtered_message_template supports {project})
INSERT INTO aa_intent (code, display_name, active, filtered_message_template)
VALUES ('ROOM_BOOKING', 'Room Booking', true,
        'I''m sorry, but room booking is not available through {project}.');
-- NOTE: until Part 2, ai.detect_operation hardcodes the intent list — a new
-- intent also needs a new version of that template.

-- New prompt version (draft → publish; highest PUBLISHED wins)
INSERT INTO aa_prompt_template (template_key, project_id, version, content, status, created_by)
VALUES ('checklist.billing', NULL, 2, '<new text>', 'PUBLISHED', 'you');
-- Project override: same key with project_id = (SELECT id FROM aa_project WHERE code='SCB')

-- Switch provider
UPDATE aa_setting SET setting_value = 'ollama' WHERE setting_key = 'ai.active_provider';
```

Changes are live within 60s (cache TTL) or immediately after `POST /api/v1/admin/config/cache/evict`.

## The safety net (keep it green)

- `src/test/resources/golden/*.txt` — 40 fixtures captured from the pre-restructure
  code. `GoldenPromptRegressionTest` (provider → registry → prompt),
  `RegistryGoldenPromptTest` (template + vars → fixture),
  `ChecklistServiceGoldenTest`. **Never regenerate fixtures to make a test pass** —
  a diff means the prompt changed.
- `IntentRegistryServiceTest` pins the full old project×intent matrix;
  `BrandServiceTest` pins the §4.6 `bank_name` fallback asymmetry.
- Tests run on H2 through the REAL Liquibase changelog (`ConfigRegistryTestBase`).

## Gotchas discovered and fixed on the way

1. **`spring.liquibase.change-log` was never bound** — the old key sat at YAML root;
   Liquibase had never run a real migration before this branch.
2. **`%%` escapes** — seeds store the rendered `%` form; copying Java source verbatim
   would have produced different prompts.
3. **Hibernate `ddl-auto: update` fought Liquibase** — it tried to shrink the clob
   columns to `varchar(255)` (silently succeeding where data fit).
   `AaSchemaFilterProvider` now excludes `aa_*` from all hbm2ddl phases; Liquibase is
   the single owner. Don't remove that filter.
4. **CRLF checkouts** — seed prompt files and fixtures are `-text` in `.gitattributes`;
   without that, a Windows clone seeds `\r\n` into every prompt.

## Deferred (tracked in the plan doc)

Phase 1c behaviour fixes (dead `baseLanguage`, silent English fallback on `und`,
registry-driven language names) → Part 3. `OperationType` enum removal +
classifier-from-`aa_intent` → Part 2. Portal UI → Part 5. No security changes.
