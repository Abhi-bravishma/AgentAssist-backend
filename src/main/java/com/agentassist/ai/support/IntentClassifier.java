package com.agentassist.ai.support;

import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Operation/intent classification. Method body moved VERBATIM from
 * BaseAiProvider in the Part 2 split. Phase 2b replaces the hardcoded intent
 * list here (and inside the ai.detect_operation template) with rules generated
 * from aa_intent rows.
 */
@Slf4j
@RequiredArgsConstructor
public class IntentClassifier {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;

    public String detectOperationType(List<String> messages, String latestMessage) {
        log.info("[AI:{}] detectOperationType called, {} messages, latest: {}",
                providerName, messages.size(),
                latestMessage.length() > 50 ? latestMessage.substring(0, 50) + "..." : latestMessage);
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder conversationContext = new StringBuilder();
            int startIdx = Math.max(0, messages.size() - 5); // Last 5 messages for context
            for (int i = startIdx; i < messages.size(); i++) {
                conversationContext.append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_DETECT_OPERATION,
                    Map.of("latest_message", latestMessage));

            String result = chat.call(prompt);
            if (result == null || result.isBlank()) {
                log.warn("[AI:{}] detectOperationType returned empty, defaulting to GENERAL", providerName);
                return "GENERAL";
            }

            // Clean and validate the response
            result = result.trim().toUpperCase().replaceAll("[^A-Z_]", "");

            // Validate it's one of the expected values
            if (!result.equals("FEE_WAIVER") && !result.equals("HOME_LOAN_CLOSURE")
                    && !result.equals("POLICY") && !result.equals("CLAIMS")
                    && !result.equals("TELCO") && !result.equals("BILLING")
                    && !result.equals("GENERAL")) {
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
