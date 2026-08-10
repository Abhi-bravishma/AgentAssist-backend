package com.agentassist.ai.support;

import com.agentassist.configregistry.IntentRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Operation/intent classification, REGISTRY-DRIVEN since Part 2b: the intent
 * code list and per-intent rules come from aa_intent rows
 * ({@link IntentRegistryService#classifierVars()}), and the set of values the
 * model may return comes from the same rows plus GENERAL. Adding an intent in
 * the DB — code, description, project mapping — is all it takes for the
 * classifier to know it; no code or template edit. The assembled prompt is
 * byte-identical to the pre-2b hardcoded one (golden fixture pins it).
 */
@Slf4j
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;
    private final IntentRegistryService intentRegistry;

    public String detectOperationType(List<String> messages, String latestMessage) {
        log.info("[AI:{}] detectOperationType called, {} messages, latest: {}",
                providerName, messages.size(),
                latestMessage.length() > 50 ? latestMessage.substring(0, 50) + "..." : latestMessage);
        long startTime = System.currentTimeMillis();

        try {
            Map<String, String> vars = new HashMap<>(intentRegistry.classifierVars());
            vars.put("latest_message", latestMessage);
            String prompt = promptService.renderDefault(TemplateKeys.AI_DETECT_OPERATION, vars);

            String result = chat.call(prompt);
            if (result == null || result.isBlank()) {
                log.warn("[AI:{}] detectOperationType returned empty, defaulting to GENERAL", providerName);
                return "GENERAL";
            }

            // Clean and validate the response
            result = result.trim().toUpperCase().replaceAll("[^A-Z_]", "");

            // Validate against the registry's active intents (+ GENERAL)
            Set<String> validCodes = intentRegistry.validClassifierCodes();
            if (!validCodes.contains(result)) {
                log.warn("[AI:{}] detectOperationType returned invalid value '{}', defaulting to GENERAL", providerName, result);
                return "GENERAL";
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] detectOperationType completed in {}ms, result: {}", providerName, duration, result);
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] detectOperationType failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return "GENERAL";
        }
    }
}
