package com.agentassist.ai;

import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.agentassist.dto.responseDTO.FollowUpCheckResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Base class with shared AI prompt logic.
 */
@Slf4j
public abstract class BaseAiProvider implements AiProvider {

    protected final ChatModel chatModel;
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final String providerName;
    protected final PromptService promptService;

    protected BaseAiProvider(ChatModel chatModel, String providerName, PromptService promptService) {
        this.chatModel = chatModel;
        this.providerName = providerName;
        this.promptService = promptService;
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
            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_TEXT,
                    Map.of("text", text));

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

            String prompt = promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION, Map.of(
                    "latest_message", latestUserMsg,
                    "message_count", String.valueOf(messages.size()),
                    "conversation", sb.toString()));

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversation returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
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

    @Override
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

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversationWithContext returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
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

    @Override
    public String translateToEnglish(String text) {
        log.info("[AI:{}] translateToEnglish called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            // This translation becomes the knowledge base search query, so a mangled
            // proper noun does not just read badly - it lowers the embedding score and
            // pulls the wrong document into the results.
            String prompt = promptService.renderDefault(TemplateKeys.AI_TRANSLATE_TO_ENGLISH,
                    Map.of("text", text));

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
            String prompt = promptService.renderDefault(TemplateKeys.AI_TRANSLATE_FROM_ENGLISH, Map.of(
                    "target_language", describeLanguage(targetLang),
                    "text", english));

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
            String prompt = promptService.renderDefault(TemplateKeys.AI_DETECT_LANGUAGE,
                    Map.of("text", text));

            String lang = call(prompt);
            if (lang == null) {
                log.warn("[AI:{}] detectLanguage returned null, defaulting to 'und'", providerName);
                return "und";
            }
            String result = normalizeLanguageTag(lang, text);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] detectLanguage completed in {}ms, raw: '{}', detected: {}",
                    providerName, duration, lang.trim(), result);
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] detectLanguage failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return "und";
        }
    }

    // Characters that exist in only one Chinese script. Used to decide Traditional vs
    // Simplified from the customer's own text, which is deterministic - unlike asking
    // the model, which answers "zh", "zh-TW" or "zh-Hant" for the same input.
    private static final String TRADITIONAL_ONLY =
            "繁體灣東車買觀們個來應這時說對開關費務點電話廣鐵頭問題實現當經濟權證單價營業機構樣兒學國讀寫語譯聽見產屬醫藥銀錢長門雞魚鳥馬龍鳳從將軍隊島龜"
            + "軒裝麼樓處號間過還發為與動樂兩邊廳選進預訂麗舊歡團導會"
            // Traditional-only, deliberately with no Simplified counterpart below:
            // 着 and 几 are both valid in Traditional writing, so counting them as
            // Simplified evidence would misread Hong Kong / Macau text.
            + "著幾";
    private static final String SIMPLIFIED_ONLY =
            "简体湾东车买观们个来应这时说对开关费务点电话广铁头问题实现当经济权证单价营业机构样儿学国读写语译听见产属医药银钱长门鸡鱼鸟马龙凤从将军队岛龟"
            + "轩装么楼处号间过还发为与动乐两边厅选进预订丽旧欢团导会";

    /**
     * Normalize whatever the model returns into a usable language tag.
     * <p>
     * Models answer "zh-TW", "zh-Hant" or "Chinese (Traditional)" for Traditional Chinese.
     * The previous {@code length() == 2} check turned every one of those into "und", and
     * LanguageService skips translation entirely for "und" - so the agent was handed raw
     * English where Chinese was expected. For Chinese the script is decided from the
     * customer's own characters; the model's region tag is only a fallback.
     */
    private String normalizeLanguageTag(String raw, String sourceText) {
        String tag = raw.trim().toLowerCase().replaceAll("[^a-z-]", "");
        String primary = tag.isEmpty() ? "" : tag.split("-")[0];
        boolean tagUnusable = primary.length() != 2;

        // Han characters alone do NOT mean Chinese - Japanese kanji live in the same
        // Unicode block, and an English message can quote a Chinese venue name. So the
        // text is only consulted when the model's own answer is unusable.
        boolean modelSaysChinese = tag.startsWith("zh") || tag.contains("chinese");
        if (modelSaysChinese || (tagUnusable && hasHanCharacters(sourceText))) {
            String scriptFromText = detectChineseScript(sourceText);
            if (scriptFromText != null) {
                return scriptFromText;
            }
            if (tag.contains("hant") || tag.contains("traditional")
                    || tag.contains("tw") || tag.contains("hk") || tag.contains("mo")) {
                return "zh-Hant";
            }
            if (tag.contains("hans") || tag.contains("simplified")
                    || tag.contains("cn") || tag.contains("sg")) {
                return "zh-Hans";
            }
            return "zh";
        }

        // Primary subtag only, e.g. "pt-br" -> "pt"
        return tagUnusable ? "und" : primary;
    }

    private static boolean hasHanCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.codePoints().anyMatch(cp -> (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF));
    }

    /**
     * Decide Traditional vs Simplified by counting script-exclusive characters.
     * Returns null when the text has no distinguishing characters.
     */
    private static String detectChineseScript(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int traditional = 0;
        int simplified = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (TRADITIONAL_ONLY.indexOf(c) >= 0) {
                traditional++;
            } else if (SIMPLIFIED_ONLY.indexOf(c) >= 0) {
                simplified++;
            }
        }
        if (traditional == 0 && simplified == 0) {
            return null;
        }
        return traditional >= simplified ? "zh-Hant" : "zh-Hans";
    }

    /**
     * Human-readable target for the translation prompt. A bare tag like "zh-Hant" is
     * ambiguous to the model; naming the script explicitly is not.
     */
    private String describeLanguage(String tag) {
        if (tag == null || tag.isBlank()) {
            return "English";
        }
        return switch (tag.toLowerCase()) {
            case "zh-hant" -> "Traditional Chinese (繁體中文), using Traditional characters only";
            case "zh-hans" -> "Simplified Chinese (简体中文), using Simplified characters only";
            case "zh" -> "Chinese";
            default -> tag;
        };
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

            String prompt = promptService.renderDefault(TemplateKeys.AI_OVERALL_SENTIMENT,
                    Map.of("conversation", sb.toString()));

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

            String prompt = promptService.renderDefault(TemplateKeys.AI_REGENERATE_SUGGESTIONS, Map.of(
                    "previous_suggestion", previousSuggestion != null ? previousSuggestion : "",
                    "conversation", sb.toString(),
                    "latest_message", latestUserMsg));

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] regenerateSuggestions returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
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

    @Override
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

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] regenerateSuggestionsWithContext returned empty, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
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

    @Override
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

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeFollowUpRequirement returned empty response, using fallback", providerName);
                return fallbackFollowUpResponse();
            }

            content = cleanJson(content);
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

    protected FollowUpCheckResponse fallbackFollowUpResponse() {
        return FollowUpCheckResponse.builder()
                .followUpRequired(false)
                .followUp("")
                .conversationSummary("")
                .build();
    }

    @Override
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

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeConversationWithChecklist returned empty response, using fallback", providerName);
                return fallbackBundle();
            }

            content = cleanJson(content);
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

    @Override
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

            String result = call(prompt);
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
     * Clean JSON response from markdown formatting and extract JSON object.
     * Handles cases where LLM adds extra text before/after JSON.
     * Also attempts to repair truncated JSON.
     */
    protected String cleanJson(String text) {
        if (text == null || text.isBlank()) return "";

        String original = text;
        text = text.trim();

        // Remove markdown code blocks
        text = text.replace("```json", "")
                .replace("```", "")
                .trim();

        // Try to extract JSON object from response
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            String extracted = text.substring(firstBrace, lastBrace + 1);
            log.debug("[AI] Extracted JSON from position {} to {}", firstBrace, lastBrace);
            return extracted;
        }

        // JSON might be truncated - try to repair it
        if (firstBrace != -1 && lastBrace == -1) {
            log.warn("[AI] JSON appears truncated, attempting repair. Raw response: {}",
                    original.length() > 500 ? original.substring(0, 500) + "..." : original);

            String partial = text.substring(firstBrace);

            // Count unclosed braces and brackets
            int openBraces = 0;
            int openBrackets = 0;
            boolean inString = false;
            char prevChar = 0;

            for (char c : partial.toCharArray()) {
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (c == '{') openBraces++;
                    else if (c == '}') openBraces--;
                    else if (c == '[') openBrackets++;
                    else if (c == ']') openBrackets--;
                }
                prevChar = c;
            }

            // Close any unclosed strings, arrays, and objects
            StringBuilder repaired = new StringBuilder(partial);
            if (inString) {
                repaired.append("\"");
            }
            for (int i = 0; i < openBrackets; i++) {
                repaired.append("]");
            }
            for (int i = 0; i < openBraces; i++) {
                repaired.append("}");
            }

            log.info("[AI] Repaired truncated JSON by adding {} closing braces, {} closing brackets",
                    openBraces, openBrackets);
            return repaired.toString();
        }

        // If no braces found at all, return cleaned text as-is
        log.warn("[AI] Could not find JSON braces in response: {}",
                original.length() > 200 ? original.substring(0, 200) + "..." : original);
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

    /**
     * Infer sentiment scores from label when Ollama returns null (becomes 0.0).
     * Only affects cases where score is 0.0 but label indicates otherwise.
     * OpenAI always returns proper scores, so this won't affect OpenAI flow.
     */
    protected void inferSentimentScoresFromLabel(AiAnalysisBundle bundle) {
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

    @Override
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

            String content = call(prompt);
            if (content == null || content.isBlank()) {
                log.warn("[AI:{}] analyzeCompliance returned empty response, using fallback", providerName);
                return fallback;
            }

            content = cleanJson(content);
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
