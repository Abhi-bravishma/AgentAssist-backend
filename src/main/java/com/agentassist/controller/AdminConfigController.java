package com.agentassist.controller;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.configregistry.BrandService;
import com.agentassist.configregistry.ConfigRegistryException;
import com.agentassist.configregistry.LanguageRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.SettingsService;
import com.agentassist.configregistry.entity.AaSetting;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Runtime config endpoints for the admin portal (Part 5 builds the UI; these
 * are the switches it flips). New endpoints only — no existing contract is
 * touched.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/config")
@RequiredArgsConstructor
@Tag(name = "Admin Config", description = "Runtime configuration: active AI provider, registry cache")
public class AdminConfigController {

    private final AiProviderFactory aiProviderFactory;
    private final SettingsService settingsService;
    private final PromptService promptService;
    private final BrandService brandService;
    private final LanguageRegistryService languageRegistryService;

    @Operation(summary = "Current AI provider",
            description = "The active provider (openai | ollama) and which providers this deployment has")
    @GetMapping("/ai-provider")
    public ResponseEntity<?> getAiProvider() {
        return ResponseEntity.ok(Map.of(
                "active", aiProviderFactory.activeName(),
                "available", aiProviderFactory.availableProviders()));
    }

    @Operation(summary = "Switch AI provider",
            description = "Sets ai.active_provider in the registry. One provider is active at a "
                    + "time; the switch takes effect immediately, no restart. Fails with 400 if "
                    + "the requested provider is not available in this deployment.")
    @PutMapping("/ai-provider")
    public ResponseEntity<?> setAiProvider(@RequestBody Map<String, String> body) {
        String requested = body != null ? body.get("provider") : null;
        if (requested == null || requested.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "`provider` (openai | ollama) is required"));
        }
        try {
            // Validate availability BEFORE persisting — never store a provider
            // this deployment cannot serve.
            aiProviderFactory.forName(requested);
        } catch (ConfigRegistryException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        String normalized = requested.trim().toLowerCase();
        settingsService.set(AaSetting.AI_ACTIVE_PROVIDER, normalized, "admin-portal");
        log.info("[AdminConfig] Active AI provider switched to '{}'", normalized);
        return ResponseEntity.ok(Map.of(
                "active", aiProviderFactory.activeName(),
                "available", aiProviderFactory.availableProviders()));
    }

    @Operation(summary = "Evict registry caches",
            description = "Drops all cached templates, brand attributes, settings and languages "
                    + "so registry edits are picked up immediately instead of within the 60s TTL.")
    @PostMapping("/cache/evict")
    public ResponseEntity<?> evictCaches() {
        promptService.evictAll();
        brandService.evictAll();
        settingsService.evictAll();
        languageRegistryService.evictAll();
        log.info("[AdminConfig] Registry caches evicted");
        return ResponseEntity.ok(Map.of("message", "Registry caches evicted"));
    }
}
