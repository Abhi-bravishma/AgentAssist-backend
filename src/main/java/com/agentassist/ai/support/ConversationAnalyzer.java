package com.agentassist.ai.support;

import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Conversation analysis, sentiment and suggestion (re)generation. Method
 * bodies moved VERBATIM from BaseAiProvider in the Part 2 split — identical
 * prompts (registry-served), identical fallbacks, identical log lines.
 */
@Slf4j
@RequiredArgsConstructor
public class ConversationAnalyzer {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;
    private final ObjectMapper mapper;

    public AiAnalysisResult analyzeText(String text) {
        log.info("[AI:{}] analyzeText called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_TEXT,
                    Map.of("text", text));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeText returned empty response, using fallback", providerName);
                return fallbackResult();
            }

            content = AiJson.cleanJson(content);
            AiAnalysisResult result = mapper.readValue(content, AiAnalysisResult.class);
            enforceDefaults(result);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeText completed in {}ms, sentiment: {}, score: {}",
                    providerName, duration, result.getSentiment(), result.getSentimentScore());
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeText failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackResult();
        }
    }

    public AiAnalysisBundle analyzeConversation(List<String> messages, String latestUserMsg) {
        log.info("[AI:{}] analyzeConversation called, {} messages, latest msg length: {}",
                providerName, messages.size(), latestUserMsg.length());
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION, Map.of(
                    "latest_message", latestUserMsg,
                    "message_count", String.valueOf(messages.size()),
                    "conversation", sb.toString()));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversation returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = AiJson.cleanJson(content);
            log.debug("[AI:{}] Raw JSON after cleaning: {}", providerName, content);

            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);
            inferSentimentScoresFromLabel(bundle);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeConversation completed in {}ms, overall: {}, current: {} ({})",
                    providerName, duration,
                    bundle.getOverall_sentiment_score(),
                    bundle.getCurrent_sentiment_score(),
                    bundle.getCurrent_sentiment_label());
            log.debug("[AI:{}] Summary: {}", providerName, bundle.getSummary());
            log.debug("[AI:{}] Suggestions count: {}", providerName, bundle.getSuggestions() != null ? bundle.getSuggestions().size() : 0);

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeConversation failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    public AiAnalysisBundle analyzeConversationWithContext(List<String> messages, String latestUserMsg, String policyContext) {
        log.info("[AI:{}] analyzeConversationWithContext called, {} messages, policy context: {}",
                providerName, messages.size(), policyContext != null ? "yes" : "no");
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String contextSection = (policyContext != null && !policyContext.isBlank())
                ? "CUSTOMER DATA:\n" + policyContext + "\n"
                : "";

            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION_WITH_CONTEXT, Map.of(
                    "latest_message", latestUserMsg,
                    "context_section", contextSection,
                    "message_count", String.valueOf(messages.size()),
                    "conversation", sb.toString()));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversationWithContext returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = AiJson.cleanJson(content);
            log.debug("[AI:{}] Raw JSON after cleaning: {}", providerName, content);

            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);
            inferSentimentScoresFromLabel(bundle);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeConversationWithContext completed in {}ms, overall: {}, current: {} ({})",
                    providerName, duration,
                    bundle.getOverall_sentiment_score(),
                    bundle.getCurrent_sentiment_score(),
                    bundle.getCurrent_sentiment_label());

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeConversationWithContext failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    public AiAnalysisBundle analyzeConversationWithChecklist(List<String> messages, String latestUserMsg,
                                                             String checklistContext, String operationType) {
        log.info("[AI:{}] analyzeConversationWithChecklist called, {} messages, operation: {}",
                providerName, messages.size(), operationType);
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION_WITH_CHECKLIST, Map.of(
                    "latest_message", latestUserMsg,
                    "checklist_context", checklistContext,
                    "message_count", String.valueOf(messages.size()),
                    "conversation", sb.toString()));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversationWithChecklist returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = AiJson.cleanJson(content);
            log.debug("[AI:{}] Raw JSON after cleaning: {}", providerName, content);

            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);
            inferSentimentScoresFromLabel(bundle);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] analyzeConversationWithChecklist completed in {}ms, overall: {}, current: {} ({})",
                    providerName, duration,
                    bundle.getOverall_sentiment_score(),
                    bundle.getCurrent_sentiment_score(),
                    bundle.getCurrent_sentiment_label());
            log.debug("[AI:{}] Parsed bundle - summary length: {}, suggestions: {}",
                    providerName,
                    bundle.getSummary() != null ? bundle.getSummary().length() : 0,
                    bundle.getSuggestions());

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] analyzeConversationWithChecklist failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    public double computeOverallSentiment(List<String> messages) {
        log.info("[AI:{}] computeOverallSentiment called, {} messages", providerName, messages.size());
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_OVERALL_SENTIMENT,
                    Map.of("conversation", sb.toString()));

            String content = AiJson.cleanJson(chat.call(prompt));
            JsonNode json = mapper.readTree(content);
            double score = json.get("overall_sentiment_score").asDouble();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] computeOverallSentiment completed in {}ms, score: {}", providerName, duration, score);
            return score;

        } catch (Exception e) {
            log.error("[AI:{}] computeOverallSentiment failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return 0.0;
        }
    }

    public AiAnalysisBundle regenerateSuggestions(List<String> messages, String latestUserMsg, String previousSuggestion) {
        log.info("[AI:{}] regenerateSuggestions called, {} messages, previous suggestion length: {}",
                providerName, messages.size(), previousSuggestion != null ? previousSuggestion.length() : 0);
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_REGENERATE_SUGGESTIONS, Map.of(
                    "previous_suggestion", previousSuggestion != null ? previousSuggestion : "",
                    "conversation", sb.toString(),
                    "latest_message", latestUserMsg));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] regenerateSuggestions returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = AiJson.cleanJson(content);
            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);
            inferSentimentScoresFromLabel(bundle);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] regenerateSuggestions completed in {}ms, suggestions count: {}",
                    providerName, duration, bundle.getSuggestions() != null ? bundle.getSuggestions().size() : 0);

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] regenerateSuggestions failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    public AiAnalysisBundle regenerateSuggestionsWithContext(List<String> messages, String latestUserMsg,
                                                             String previousSuggestion, String checklistContext,
                                                             String customerName) {
        // If no checklist context, fall back to regular regenerate
        if (checklistContext == null || checklistContext.isBlank()) {
            return regenerateSuggestions(messages, latestUserMsg, previousSuggestion);
        }

        log.info("[AI:{}] regenerateSuggestionsWithContext called, {} messages, hasContext: true, customer: {}",
                providerName, messages.size(), customerName != null ? customerName : "unknown");
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = promptService.renderDefault(TemplateKeys.AI_REGENERATE_SUGGESTIONS_WITH_CONTEXT, Map.of(
                    "checklist_context", checklistContext,
                    "previous_suggestion", previousSuggestion != null ? previousSuggestion : "",
                    "customer_name", customerName != null ? customerName : "there",
                    "conversation", sb.toString(),
                    "latest_message", latestUserMsg));

            String content = chat.call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] regenerateSuggestionsWithContext returned empty, using fallback", providerName);
                return fallbackBundle();
            }

            content = AiJson.cleanJson(content);
            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);
            inferSentimentScoresFromLabel(bundle);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] regenerateSuggestionsWithContext completed in {}ms, suggestions: {}",
                    providerName, duration, bundle.getSuggestions() != null ? bundle.getSuggestions().size() : 0);

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] regenerateSuggestionsWithContext failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    private void enforceDefaults(AiAnalysisResult r) {
        if (r.getSentiment() == null) r.setSentiment("neutral");
        if (r.getSummary() == null) r.setSummary("");
        if (r.getSuggestions() == null) r.setSuggestions(List.of());
    }

    private AiAnalysisResult fallbackResult() {
        AiAnalysisResult f = new AiAnalysisResult();
        f.setSentiment("neutral");
        f.setSentimentScore(0.0);
        f.setSummary("");
        f.setSuggestions(List.of());
        return f;
    }

    private AiAnalysisBundle fallbackBundle() {
        AiAnalysisBundle b = new AiAnalysisBundle();
        b.setCurrent_sentiment_score(0);
        b.setOverall_sentiment_score(0);
        b.setCurrent_sentiment_label("neutral");
        b.setSummary("");
        b.setSuggestions(List.of());
        return b;
    }

    /**
     * Infer sentiment scores from label when Ollama returns null (becomes 0.0).
     * Only affects cases where score is 0.0 but label indicates otherwise.
     * OpenAI always returns proper scores, so this won't affect OpenAI flow.
     */
    private void inferSentimentScoresFromLabel(AiAnalysisBundle bundle) {
        if (bundle == null) return;

        String label = bundle.getCurrent_sentiment_label();
        if (label == null) {
            label = "neutral";
            bundle.setCurrent_sentiment_label(label);
        }

        // Only infer if score is 0.0 (which happens when Ollama returns null)
        // and the label suggests a different sentiment
        if (bundle.getCurrent_sentiment_score() == 0.0) {
            double inferredScore = switch (label.toLowerCase()) {
                case "positive" -> 0.5;
                case "negative" -> -0.5;
                default -> 0.0;
            };
            if (inferredScore != 0.0) {
                log.info("[AI:{}] Inferred current_sentiment_score {} from label '{}'",
                        providerName, inferredScore, label);
                bundle.setCurrent_sentiment_score(inferredScore);
            }
        }

        // Also infer overall if it's 0.0 and current is not
        if (bundle.getOverall_sentiment_score() == 0.0 && bundle.getCurrent_sentiment_score() != 0.0) {
            bundle.setOverall_sentiment_score(bundle.getCurrent_sentiment_score());
            log.info("[AI:{}] Inferred overall_sentiment_score {} from current score",
                    providerName, bundle.getOverall_sentiment_score());
        }
    }
}
