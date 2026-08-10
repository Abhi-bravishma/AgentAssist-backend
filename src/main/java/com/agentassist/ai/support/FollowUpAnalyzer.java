package com.agentassist.ai.support;

import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.responseDTO.FollowUpCheckResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * End-of-interaction follow-up analysis. Method body moved VERBATIM from
 * BaseAiProvider in the Part 2 split.
 */
@Slf4j
@RequiredArgsConstructor
public class FollowUpAnalyzer {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;
    private final ObjectMapper mapper;

    public FollowUpCheckResponse analyzeFollowUpRequirement(List<String> transcript, String customerName) {
        log.info("[AI:{}] analyzeFollowUpRequirement called, {} messages, customer: {}",
                providerName, transcript.size(), customerName != null ? customerName : "unknown");
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < transcript.size(); i++) {
                sb.append(transcript.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_FOLLOW_UP_CHECK, Map.of(
                    "transcript", sb.toString(),
                    "customer_name", customerName != null ? customerName : "Unknown"));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeFollowUpRequirement returned empty response, using fallback", providerName);
                return fallbackFollowUpResponse();
            }

            content = AiJson.cleanJson(content);
            JsonNode json = mapper.readTree(content);

            boolean followUpRequired = json.path("follow_up_required").asBoolean(false);
            String followUp = json.path("follow_up").asText("");

            // Ensure followUp is empty if not required
            if (!followUpRequired) {
                followUp = "";
            }

            FollowUpCheckResponse response = FollowUpCheckResponse.builder()
                    .followUpRequired(followUpRequired)
                    .followUp(followUp)
                    .conversationSummary(json.path("conversation_summary").asText(""))
                    .build();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeFollowUpRequirement completed in {}ms, followUp: {}",
                    providerName, duration, response.isFollowUpRequired());

            return response;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeFollowUpRequirement failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackFollowUpResponse();
        }
    }

    private FollowUpCheckResponse fallbackFollowUpResponse() {
        return FollowUpCheckResponse.builder()
                .followUpRequired(false)
                .followUp("")
                .conversationSummary("")
                .build();
    }
}
