package com.agentassist.ai;

import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

/**
 * Base class with shared AI prompt logic.
 */
@Slf4j
public abstract class BaseAiProvider implements AiProvider {

    protected final ChatModel chatModel;
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final String providerName;

    protected BaseAiProvider(ChatModel chatModel, String providerName) {
        this.chatModel = chatModel;
        this.providerName = providerName;
        log.info("[AI] Provider initialized: {}", providerName);
    }

    /**
     * Get the provider name for logging.
     */
    public String getProviderName() {
        return providerName;
    }

    @Override
    public AiAnalysisResult analyzeText(String text) {
        log.info("[AI:{}] analyzeText called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = """
                You MUST return pure JSON only. No markdown. No commentary.

                Output format:
                {
                  "sentiment": "positive | negative | neutral",
                  "sentiment_score": number,
                  "summary": "short summary",
                  "suggestions": ["3 short empathetic replies"]
                }

                Analyze this text:
                """ + text;

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeText returned empty response, using fallback", providerName);
                return fallbackResult();
            }

            content = cleanJson(content);
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

    @Override
    public AiAnalysisBundle analyzeConversation(List<String> messages, String latestUserMsg) {
        log.info("[AI:{}] analyzeConversation called, {} messages, latest msg length: {}",
                providerName, messages.size(), latestUserMsg.length());
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = """
                You are analyzing a customer service conversation. Return ONLY valid JSON.

                Required structure:
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<2-3 sentences>",
                  "suggestions": ["<reply 1>", "<reply 2>", "<reply 3>"]
                }

                Guidelines:
                - Score > 0.3 = positive
                - Score < -0.3 = negative
                - Suggestions should be empathetic and context-aware
                - IMPORTANT: If there is only ONE message, overall_sentiment_score and current_sentiment_score MUST be EXACTLY THE SAME
                - For multiple messages: current_sentiment_score = 70%% latest message + 30%% trend, overall_sentiment_score = average of all messages

                Conversation (%d messages):
                %s

                Latest user message:
                "%s"
                """.formatted(messages.size(), sb.toString(), latestUserMsg);

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversation returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);

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

    @Override
    public String translateToEnglish(String text) {
        log.info("[AI:{}] translateToEnglish called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = """
                Translate to English.
                Output ONLY the translated text.
                Text: %s
                """.formatted(text);

            String out = call(prompt);
            String result = (out == null || out.isBlank()) ? text : out.trim();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] translateToEnglish completed in {}ms, result length: {}", providerName, duration, result.length());
            log.debug("[AI:{}] Translation: '{}' -> '{}'", providerName,
                    text.substring(0, Math.min(50, text.length())),
                    result.substring(0, Math.min(50, result.length())));
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] translateToEnglish failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return text;
        }
    }

    @Override
    public String translateFromEnglish(String english, String targetLang) {
        log.info("[AI:{}] translateFromEnglish called, target: {}, text length: {}", providerName, targetLang, english.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = """
                Translate strictly to %s.
                Output ONLY the translated text.
                Text: %s
                """.formatted(targetLang, english);

            String out = call(prompt);
            String result = (out == null || out.isBlank()) ? english : out.trim();

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] translateFromEnglish completed in {}ms, target: {}", providerName, duration, targetLang);
            log.debug("[AI:{}] Translation to {}: '{}' -> '{}'", providerName, targetLang,
                    english.substring(0, Math.min(50, english.length())),
                    result.substring(0, Math.min(50, result.length())));
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] translateFromEnglish failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return english;
        }
    }

    @Override
    public String detectLanguage(String text) {
        log.info("[AI:{}] detectLanguage called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = """
                Detect language. Return ONLY ISO 639-1 code.
                Text: %s
                """.formatted(text);

            String lang = call(prompt);
            if (lang == null) {
                log.warn("[AI:{}] detectLanguage returned null, defaulting to 'und'", providerName);
                return "und";
            }
            lang = lang.trim().toLowerCase();
            String result = lang.length() == 2 ? lang : "und";

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] detectLanguage completed in {}ms, detected: {}", providerName, duration, result);
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] detectLanguage failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return "und";
        }
    }

    @Override
    public double computeOverallSentiment(List<String> messages) {
        log.info("[AI:{}] computeOverallSentiment called, {} messages", providerName, messages.size());
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = """
                Return ONLY pure JSON:
                {"overall_sentiment_score": number}

                Compute overall sentiment score (-1 to +1) for these messages:
                %s
                """.formatted(sb.toString());

            String content = cleanJson(call(prompt));
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

    @Override
    public AiAnalysisBundle regenerateSuggestions(List<String> messages, String latestUserMsg, String previousSuggestion) {
        log.info("[AI:{}] regenerateSuggestions called, {} messages, previous suggestion length: {}",
                providerName, messages.size(), previousSuggestion != null ? previousSuggestion.length() : 0);
        long startTime = System.currentTimeMillis();

        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
            }

            String prompt = """
                You are analyzing a customer service conversation. Return ONLY valid JSON.

                IMPORTANT: Generate NEW and DIFFERENT suggestions. Do NOT repeat or paraphrase this previous suggestion:
                "%s"

                Required structure:
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<2-3 sentences>",
                  "suggestions": ["<new reply 1>", "<new reply 2>", "<new reply 3>"]
                }

                Guidelines:
                - Provide 3 COMPLETELY DIFFERENT suggestions from the previous one
                - Be creative and vary the tone (formal, friendly, empathetic)
                - Suggestions should be context-aware and helpful

                Conversation:
                %s

                Latest user message:
                "%s"
                """.formatted(
                    previousSuggestion != null ? previousSuggestion : "",
                    sb.toString(),
                    latestUserMsg
                );

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] regenerateSuggestions returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
            AiAnalysisBundle bundle = mapper.readValue(content, AiAnalysisBundle.class);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] regenerateSuggestions completed in {}ms, suggestions count: {}",
                    providerName, duration, bundle.getSuggestions() != null ? bundle.getSuggestions().size() : 0);

            return bundle;

        } catch (Exception e) {
            log.error("[AI:{}] regenerateSuggestions failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return fallbackBundle();
        }
    }

    /**
     * Call the underlying chat model.
     */
    protected String call(String promptText) {
        try {
            log.info("[AI:{}] Calling model, prompt length: {}", providerName, promptText.length());
            long startTime = System.currentTimeMillis();

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);

            if (response == null || response.getResult() == null) {
                log.warn("[AI:{}] Model returned null response", providerName);
                return null;
            }

            String content = response.getResult().getOutput().getContent();
            long duration = System.currentTimeMillis() - startTime;

            log.info("[AI:{}] Response received in {}ms, length: {}", providerName, duration, content != null ? content.length() : 0);
            return content;

        } catch (Exception e) {
            log.error("[AI:{}] Model call failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("[AI:{}] Caused by: {}", providerName, e.getCause().getMessage());
            }
            return null;
        }
    }

    /**
     * Clean JSON response from markdown formatting.
     */
    protected String cleanJson(String text) {
        if (text == null) return "";
        text = text.trim();
        text = text.replace("```json", "")
                .replace("```", "")
                .replace("Here is the JSON you requested:", "")
                .trim();
        return text;
    }

    protected void enforceDefaults(AiAnalysisResult r) {
        if (r.getSentiment() == null) r.setSentiment("neutral");
        if (r.getSummary() == null) r.setSummary("");
        if (r.getSuggestions() == null) r.setSuggestions(List.of());
    }

    protected AiAnalysisResult fallbackResult() {
        AiAnalysisResult f = new AiAnalysisResult();
        f.setSentiment("neutral");
        f.setSentimentScore(0.0);
        f.setSummary("");
        f.setSuggestions(List.of());
        return f;
    }

    protected AiAnalysisBundle fallbackBundle() {
        AiAnalysisBundle b = new AiAnalysisBundle();
        b.setCurrent_sentiment_score(0);
        b.setOverall_sentiment_score(0);
        b.setCurrent_sentiment_label("neutral");
        b.setSummary("");
        b.setSuggestions(List.of());
        return b;
    }
}
