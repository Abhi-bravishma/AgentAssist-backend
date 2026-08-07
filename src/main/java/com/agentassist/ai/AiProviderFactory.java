package com.agentassist.ai;

import com.agentassist.ai.config.AiConfig;
import com.agentassist.configregistry.ConfigRegistryException;
import com.agentassist.configregistry.SettingsService;
import com.agentassist.configregistry.entity.AaSetting;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the ACTIVE AiProvider at call time from the registry setting
 * {@code ai.active_provider} — one provider is active at a time, switchable
 * from the admin portal without a restart (the SettingsService cache means a
 * change is live within 60 seconds, immediately after an evict).
 *
 * <p>Both provider beans exist since Part 1 removed their mutually exclusive
 * {@code @ConditionalOnProperty}; either may be absent in a deployment
 * (nullable injection), and selecting an absent provider fails LOUDLY rather
 * than silently serving the other one.</p>
 *
 * <p>Bootstrap: when the setting row does not exist (registry seeded later),
 * the old {@code ai.provider} property decides — the same value the seed
 * copies. After seeding, the DATABASE is authoritative.</p>
 */
@Slf4j
@Component
public class AiProviderFactory {

    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_OLLAMA = "ollama";

    private final AiProvider openAiProvider;
    private final AiProvider ollamaProvider;
    private final SettingsService settingsService;
    private final AiConfig aiConfig;

    public AiProviderFactory(@Nullable @Qualifier("openAiProvider") AiProvider openAiProvider,
                             @Nullable @Qualifier("ollamaProvider") AiProvider ollamaProvider,
                             SettingsService settingsService,
                             AiConfig aiConfig) {
        this.openAiProvider = openAiProvider;
        this.ollamaProvider = ollamaProvider;
        this.settingsService = settingsService;
        this.aiConfig = aiConfig;
        log.info("[AI] Provider factory initialized - openai: {}, ollama: {}",
                openAiProvider != null ? "available" : "absent",
                ollamaProvider != null ? "available" : "absent");
    }

    /** The provider currently selected by the registry (or the bootstrap property). */
    public AiProvider active() {
        return forName(activeName());
    }

    /** Normalized name of the active provider. */
    public String activeName() {
        String configured = settingsService.find(AaSetting.AI_ACTIVE_PROVIDER)
                .orElseGet(() -> {
                    log.warn("[AI] No {} row in the registry - falling back to the "
                            + "ai.provider property ({})", AaSetting.AI_ACTIVE_PROVIDER,
                            aiConfig.getProvider());
                    return aiConfig.getProvider();
                });
        return normalize(configured);
    }

    /** Providers that actually have a bean in this deployment. */
    public List<String> availableProviders() {
        List<String> available = new ArrayList<>();
        if (openAiProvider != null) {
            available.add(PROVIDER_OPENAI);
        }
        if (ollamaProvider != null) {
            available.add(PROVIDER_OLLAMA);
        }
        return available;
    }

    /**
     * Resolve a provider by name, failing loudly when it is unknown or not
     * available in this deployment. Also used by the portal to validate a
     * switch BEFORE persisting it.
     */
    public AiProvider forName(String name) {
        String normalized = normalize(name);
        AiProvider chosen = switch (normalized) {
            case PROVIDER_OPENAI -> openAiProvider;
            case PROVIDER_OLLAMA -> ollamaProvider;
            default -> throw new ConfigRegistryException(
                    "Unknown AI provider '" + name + "' - expected openai or ollama");
        };
        if (chosen == null) {
            throw new ConfigRegistryException(
                    "AI provider '" + normalized + "' is selected but not available in this deployment"
                    + " (available: " + availableProviders() + ")");
        }
        return chosen;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
