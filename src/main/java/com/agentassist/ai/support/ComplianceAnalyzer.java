package com.agentassist.ai.support;

import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Agent-message compliance scoring. Method body moved VERBATIM from
 * BaseAiProvider in the Part 2 split.
 */
@Slf4j
@RequiredArgsConstructor
public class ComplianceAnalyzer {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;
    private final ObjectMapper mapper;

    public ComplianceResponse analyzeCompliance(List<String> agentMessages, String interactionId) {
        log.info("[AI:{}] analyzeCompliance called, {} agent messages, interaction: {}",
                providerName, agentMessages.size(), interactionId);
        long startTime = System.currentTimeMillis();

        // Default response with all false
        ComplianceResponse fallback = ComplianceResponse.builder()
                .interactionId(interactionId)
                .greeting(false)
                .empathy(false)
                .clarity(false)
                .productTnC(false)
                .valediction(false)
                .checkedAt(Instant.now())
                .build();

        if (agentMessages == null || agentMessages.isEmpty()) {
            log.warn("[AI:{}] No agent messages to analyze for compliance", providerName);
            return fallback;
        }

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < agentMessages.size(); i++) {
                sb.append(i + 1).append(") ").append(agentMessages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_COMPLIANCE,
                    Map.of("agent_messages", sb.toString()));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeCompliance returned empty response, using fallback", providerName);
                return fallback;
            }

            content = AiJson.cleanJson(content);
            log.debug("[AI:{}] Compliance check JSON: {}", providerName, content);

            JsonNode json = mapper.readTree(content);

            ComplianceResponse response = ComplianceResponse.builder()
                    .interactionId(interactionId)
                    .greeting(json.path("greeting").asBoolean(false))
                    .empathy(json.path("empathy").asBoolean(false))
                    .clarity(json.path("clarity").asBoolean(false))
                    .productTnC(json.path("productTnC").asBoolean(false))
                    .valediction(json.path("valediction").asBoolean(false))
                    .checkedAt(Instant.now())
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeCompliance completed in {}ms - greeting:{}, empathy:{}, clarity:{}, productTnC:{}, valediction:{}",
                    providerName, duration,
                    response.getGreeting(), response.getEmpathy(), response.getClarity(),
                    response.getProductTnC(), response.getValediction());

            return response;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeCompliance failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallback;
        }
    }
}
