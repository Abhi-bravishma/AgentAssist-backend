package com.agentassist.controller;

import com.agentassist.configregistry.ConfigRegistryException;
import com.agentassist.configregistry.RegistryAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Portal CRUD over the config registry (Part 5): projects, intents, the
 * project→intent whitelist and brand attributes. Prompt templates already have
 * their endpoints in {@link AdminConfigController}. New endpoints only — no
 * existing contract is touched.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/registry")
@RequiredArgsConstructor
@Tag(name = "Admin Registry", description = "CRUD: projects, intents, project-intent whitelist, brand attributes")
public class AdminRegistryController {

    private final RegistryAdminService registryAdminService;

    // ==================== projects ====================

    @Operation(summary = "List projects",
            description = "Every project with its whitelist state: restricted=false means ALL intents are allowed.")
    @GetMapping("/projects")
    public ResponseEntity<?> listProjects() {
        return ResponseEntity.ok(registryAdminService.listProjects());
    }

    @Operation(summary = "Create project",
            description = "New projects start unrestricted (all intents allowed) with no brand overrides - "
                    + "brand values fall back to the global defaults until set.")
    @PostMapping("/projects")
    public ResponseEntity<?> createProject(@RequestBody Map<String, String> body) {
        return handle(() -> registryAdminService.createProject(
                body != null ? body.get("code") : null,
                body != null ? body.get("displayName") : null));
    }

    @Operation(summary = "Update project", description = "Rename or toggle active.")
    @PutMapping("/projects/{code}")
    public ResponseEntity<?> updateProject(@PathVariable String code,
                                           @RequestBody Map<String, Object> body) {
        return handle(() -> registryAdminService.updateProject(
                code,
                body != null ? (String) body.get("displayName") : null,
                body != null ? (Boolean) body.get("active") : null));
    }

    @Operation(summary = "Replace project intent whitelist",
            description = "restricted=false deletes all rows (ALL intents allowed). restricted=true stores "
                    + "one row per active intent, enabled only for `enabled` codes - intents added later "
                    + "are NOT allowed for restricted projects until enabled here.")
    @PutMapping("/projects/{code}/intents")
    public ResponseEntity<?> setProjectIntents(@PathVariable String code,
                                               @RequestBody Map<String, Object> body) {
        boolean restricted = body != null && Boolean.TRUE.equals(body.get("restricted"));
        @SuppressWarnings("unchecked")
        List<String> enabled = body != null ? (List<String>) body.get("enabled") : null;
        return handle(() -> registryAdminService.setProjectIntents(code, restricted, enabled));
    }

    // ==================== intents ====================

    @Operation(summary = "List intents",
            description = "All intents incl. inactive; description is the classifier rule, "
                    + "filteredMessageTemplate the project-gating reply ({project} placeholder).")
    @GetMapping("/intents")
    public ResponseEntity<?> listIntents() {
        return ResponseEntity.ok(registryAdminService.listIntents());
    }

    @Operation(summary = "Create intent",
            description = "Pure data - the classifier includes it on its next call, project gating and "
                    + "filtered messages just work. Optional checklist: publish a template under key "
                    + "checklist.<code lowercase>. Salesforce-backed customer data would need Java (rare).")
    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, String> body) {
        return handle(() -> registryAdminService.createIntent(
                body != null ? body.get("code") : null,
                body != null ? body.get("displayName") : null,
                body != null ? body.get("description") : null,
                body != null ? body.get("filteredMessageTemplate") : null));
    }

    @Operation(summary = "Update intent",
            description = "Edit rule/message/name or toggle active. Deactivating removes it from the "
                    + "classifier immediately; existing conversations simply stop detecting it.")
    @PutMapping("/intents/{code}")
    public ResponseEntity<?> updateIntent(@PathVariable String code,
                                          @RequestBody Map<String, Object> body) {
        return handle(() -> registryAdminService.updateIntent(
                code,
                body != null ? (String) body.get("displayName") : null,
                body != null ? (String) body.get("description") : null,
                body != null ? (String) body.get("filteredMessageTemplate") : null,
                body != null ? (Boolean) body.get("active") : null));
    }

    // ==================== brand attributes ====================

    @Operation(summary = "List brand attributes",
            description = "Global defaults (projectCode null) first, then per-project overrides.")
    @GetMapping("/brand-attributes")
    public ResponseEntity<?> listBrandAttributes() {
        return ResponseEntity.ok(registryAdminService.listBrandAttributes());
    }

    @Operation(summary = "Create or update a brand attribute",
            description = "Upsert by (projectCode, attrKey); empty projectCode = global default. "
                    + "Cache is evicted - the value is in live prompts immediately.")
    @PostMapping("/brand-attributes")
    public ResponseEntity<?> upsertBrandAttribute(@RequestBody Map<String, String> body) {
        return handle(() -> registryAdminService.upsertBrandAttribute(
                body != null ? body.get("projectCode") : null,
                body != null ? body.get("attrKey") : null,
                body != null ? body.get("attrValue") : null));
    }

    @Operation(summary = "Delete a brand attribute",
            description = "Lookups fall back to the global default; deleting a global default a "
                    + "checklist template still references makes rendering FAIL LOUDLY by design.")
    @DeleteMapping("/brand-attributes/{id}")
    public ResponseEntity<?> deleteBrandAttribute(@PathVariable Long id) {
        return handle(() -> {
            registryAdminService.deleteBrandAttribute(id);
            return Map.of("message", "deleted");
        });
    }

    // ==================== plumbing ====================

    private ResponseEntity<?> handle(java.util.function.Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (ConfigRegistryException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
