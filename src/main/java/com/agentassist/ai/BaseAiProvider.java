package com.agentassist.ai;

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
                  "suggestions": ["1 short empathetic reply"]
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
                Analyze this customer service conversation. Return ONLY valid JSON.

                ============================================================
                LATEST MESSAGE (RESPOND TO THIS IN SUGGESTION):
                ============================================================
                "%s"

                ============================================================
                CONVERSATION HISTORY (%d messages):
                ============================================================
                %s

                ============================================================
                OUTPUT FORMAT (Return ONLY this JSON):
                ============================================================
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<end-to-end conversation summary>",
                  "suggestions": ["<reply that DIRECTLY responds to LATEST message>"]
                }

                ============================================================
                SUMMARY RULES:
                ============================================================
                - Summarize the ENTIRE conversation from FIRST message to LATEST
                - Write 4-5 sentences covering the full interaction chronologically
                - Include: what customer asked, what agent replied, information provided, current status
                - Mention specific details (names, IDs, amounts, dates if discussed)
                - NEVER include sentiment/emotion words (tone, neutral, feeling, etc.)

                GOOD SUMMARY EXAMPLE:
                "Customer initiated conversation inquiring about insurance policies. Agent retrieved data showing 3 active policies: Car Insurance (POL-0014), Travel Insurance (POL-0013), and Home Insurance (POL-0012). Customer then asked about claims status. Agent provided breakdown of 7 claims with 1 approved, 4 pending, and 2 rejected. Customer is currently asking about the claim filing process."

                ============================================================
                 SUGGESTION RULES (MUST RESPOND TO LATEST MESSAGE):
                ============================================================
                Your suggestion MUST respond to the LATEST MESSAGE above, NOT to earlier messages.

                IF LATEST MESSAGE IS:
                - "thank you" / "thanks" → Reply: "You're welcome! Is there anything else I can help you with?"
                - "bye" / "goodbye" → Reply: "Goodbye! Have a great day. Feel free to reach out if you need help."
                - "ok" / "okay" / "alright" → Reply: "Is there anything else I can assist you with?"
                - "yes" / "no" → Respond appropriately to what they're confirming/denying
                - A question → Answer that specific question
                - A complaint → Address that complaint with empathy

                - Generate EXACTLY 1 suggestion as direct reply to LATEST message
                - Start with "Hi [Customer Name]," if name is known, else "Hi there,"

                FORBIDDEN PHRASES:
                 "As a helpful assistant..."
                 "I would suggest..."
                 "It may be beneficial..."

                ============================================================
                SENTIMENT SCORING RULES:
                ============================================================
                - Score range: -1 to +1
                - Score > 0.3 = positive, Score < -0.3 = negative, between = neutral
                - SINGLE MESSAGE: overall_sentiment_score MUST EQUAL current_sentiment_score (identical)
                - MULTIPLE MESSAGES:
                  * current_sentiment_score = 70%% latest message sentiment + 30%% trend
                  * overall_sentiment_score = average of ALL message sentiments
                """.formatted(latestUserMsg, messages.size(), sb.toString());

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

            String prompt = """
                Analyze this customer service conversation. Return ONLY valid JSON.

                ============================================================
                LATEST MESSAGE (RESPOND TO THIS IN SUGGESTION):
                ============================================================
                "%s"

                %s
                ============================================================
                CONVERSATION HISTORY (%d messages):
                ============================================================
                %s

                ============================================================
                OUTPUT FORMAT (Return ONLY this JSON):
                ============================================================
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<end-to-end conversation summary>",
                  "suggestions": ["<reply that DIRECTLY responds to LATEST message>"]
                }

                ============================================================
                SUMMARY RULES:
                ============================================================
                - Summarize ENTIRE conversation from FIRST to LATEST message
                - Write 4-5 sentences covering full interaction chronologically
                - Include: what customer asked, what agent replied, data found, current status
                - Include specific details (names, IDs, amounts, dates)
                - DO NOT start with "Hi" - this is for agent reference
                - NEVER include sentiment/emotion words

                GOOD SUMMARY EXAMPLE:
                "Customer initiated conversation inquiring about insurance policies. Agent retrieved data showing 3 active policies: Car, Travel, and Home Insurance. Customer asked about claims status. Agent provided breakdown showing 7 claims total. Customer is currently asking about claim filing process."

                ============================================================
                SUGGESTION RULES (MUST RESPOND TO LATEST MESSAGE):
                ============================================================
                Your suggestion MUST respond to the LATEST MESSAGE above, NOT to earlier messages.

                IF LATEST MESSAGE IS:
                - "thank you" / "thanks" → Reply: "You're welcome! Is there anything else I can help you with?"
                - "bye" / "goodbye" → Reply: "Goodbye! Have a great day. Feel free to reach out if you need help."
                - "ok" / "okay" / "alright" → Reply: "Is there anything else I can assist you with?"
                - "yes" / "no" → Respond appropriately to what they're confirming/denying
                - A question → Answer using customer data if available
                - A complaint → Address with empathy

                - Generate EXACTLY 1 suggestion as direct reply to LATEST message
                - Start with "Hi [Customer Name]," if name is in context, else "Hi there,"

                FORBIDDEN PHRASES:
                 "As a helpful assistant..."
                 "I would suggest..."
                 "Based on the information..."

                ============================================================
                SENTIMENT SCORING RULES:
                ============================================================
                - Score range: -1 to +1
                - Score > 0.3 = positive, Score < -0.3 = negative, between = neutral
                - SINGLE MESSAGE: overall_sentiment_score MUST EQUAL current_sentiment_score (identical)
                - MULTIPLE MESSAGES:
                  * current_sentiment_score = 70%% latest message sentiment + 30%% trend
                  * overall_sentiment_score = average of ALL message sentiments
                """.formatted(latestUserMsg, contextSection, messages.size(), sb.toString());

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
            String prompt = """
                Translate to English.
                Output ONLY the translated text.

                RULES:
                - Proper nouns must survive translation: restaurant, hotel, venue, brand,
                  product, card and programme names.
                - If the proper noun has a well-known official English name, use that name.
                - If you are not certain of the official English name, keep the original
                  characters unchanged. NEVER translate a name character by character and
                  never invent an English-sounding name for it.
                - Keep numbers, times, dates, currency amounts and phone numbers unchanged.

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

                RULES:
                - Proper nouns must survive translation: restaurant, hotel, venue, brand,
                  product, card and programme names.
                - If the proper noun has a well-known official name in the target language,
                  use that name. Otherwise leave it exactly as written in the source.
                  NEVER translate a name word by word and never invent a local name for it.
                - Keep numbers, times, dates, currency amounts and phone numbers unchanged.
                - Translate everything else naturally.

                Text: %s
                """.formatted(describeLanguage(targetLang), english);

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

                PREVIOUS SUGGESTION (reword this - same info, different style):
                "%s"

                Required structure:
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<4-5 sentence end-to-end conversation summary>",
                  "suggestions": ["<reworded reply with same data>"]
                }

                ============================================================
                REGENERATE RULES - SAME INFO, DIFFERENT WORDING:
                ============================================================
                - Keep the SAME customer data (names, numbers, amounts, eligibility)
                - Change the WORDING and TONE (formal ↔ friendly ↔ empathetic)
                - MUST include all specific details from previous suggestion
                - DO NOT give generic advice - use ACTUAL data

                FORBIDDEN PHRASES - NEVER USE:
                "As a helpful assistant..."
                "I would suggest..."
                "It may be beneficial..."
                 "I recommend that..."
                 "Based on the information..."

                CORRECT FORMAT:
                 Start with "Hi [Customer Name]," or similar greeting
                 Include SAME specific data (card numbers, amounts, status)
                 Just change the wording/tone

                EXAMPLE:
                Previous: "Hi <Customer Name>, your Rewards card (1116) qualifies for fee waiver. Call (02) 88-700-700."
                Regenerated: "Good news, <Customer Name>! Your Rewards card ending in 1116 is eligible for the annual fee waiver. Simply contact us at (02) 88-700-700 to process your request."

                ============================================================
                SUMMARY RULES:
                ============================================================
                - Summarize ENTIRE conversation from start to current (4-5 sentences)
                - Include: what customer asked, what was discussed, data found, current status
                - Include specific details (card numbers, amounts, eligibility)
                - NO sentiment/emotion words

                ============================================================
                SENTIMENT SCORING RULES:
                ============================================================
                - Score range: -1 to +1
                - Score > 0.3 = positive, Score < -0.3 = negative, between = neutral
                - SINGLE MESSAGE: overall_sentiment_score MUST EQUAL current_sentiment_score
                - MULTIPLE MESSAGES:
                  * current_sentiment_score = 70%% latest message sentiment + 30%% trend
                  * overall_sentiment_score = average of ALL message sentiments

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

            String prompt = """
                You are a customer service AI assistant. You MUST use the REAL CUSTOMER DATA provided below.
                Return ONLY valid JSON.

                ============ CUSTOMER DATA CONTEXT ============
                %s
                ===============================================

                PREVIOUS SUGGESTION TO REWORD:
                "%s"

                Required JSON:
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<4-5 sentence end-to-end conversation summary>",
                  "suggestions": ["<reworded reply with SAME customer data>"]
                }

                ============================================================
                REGENERATE RULES - SAME DATA, DIFFERENT WORDING:
                ============================================================
                - Keep ALL customer-specific data (card numbers, amounts, eligibility status, thresholds)
                - Change ONLY the wording and tone (formal ↔ friendly ↔ concise ↔ detailed)
                - MUST include: card numbers, spend amounts, eligibility status, contact numbers
                - DO NOT give generic advice - use ACTUAL customer data

                FORBIDDEN PHRASES - NEVER USE:
                 "As a helpful assistant..."
                 "I would suggest..."
                 "It may be beneficial..."
                 "I recommend that..."
                 "Based on the information..."

                MANDATORY FORMAT:
                Start with "Hi %s," or similar greeting
                Include SAME specific data from previous suggestion
                Include card numbers, amounts, eligibility
                Provide contact info for next steps

                EXAMPLE TRANSFORMATION:
                Previous: "Hi <Customer Name>, your Rewards card (1116) qualifies for fee waiver with ₱30,000 spend. Call (02) 88-700-700."
                Reworded: "Good news, <Customer Name>! Your Rewards card ending in 1116 meets the spend requirement of ₱30,000 and is eligible for the annual fee waiver. Please contact (02) 88-700-700 to proceed."

                ============================================================
                SUMMARY RULES:
                ============================================================
                - Summarize ENTIRE conversation (4-5 sentences)
                - Include: what customer asked, data found, eligibility, current status
                - Include specific details (card numbers, amounts)
                - DO NOT start with "Hi"
                - NO sentiment/emotion words

                ============================================================
                SENTIMENT SCORING RULES:
                ============================================================
                - Score range: -1 to +1
                - Score > 0.3 = positive, Score < -0.3 = negative, between = neutral
                - SINGLE MESSAGE: overall_sentiment_score MUST EQUAL current_sentiment_score
                - MULTIPLE MESSAGES:
                  * current_sentiment_score = 70%% latest message sentiment + 30%% trend
                  * overall_sentiment_score = average of ALL message sentiments

                Conversation:
                %s

                Latest message: "%s"
                """.formatted(
                    checklistContext,
                    previousSuggestion != null ? previousSuggestion : "",
                    customerName != null ? customerName : "there",
                    sb.toString(),
                    latestUserMsg
                );

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

            String prompt = """
                You are analyzing a completed customer service conversation to determine if follow-up is required.
                Return ONLY valid JSON.

                CONVERSATION TRANSCRIPT:
                %s

                Customer Name: %s

                Required JSON structure:
                {
                  "follow_up_required": true/false,
                  "follow_up": "<reason for follow-up OR empty string if not required>",
                  "conversation_summary": "<4-5 sentence comprehensive summary of the conversation>"
                }

                RULES:
                - If follow_up_required is TRUE: "follow_up" must contain the reason (e.g., "Customer issue unresolved - claim pending approval")
                - If follow_up_required is FALSE: "follow_up" must be empty string ""

                FOLLOW-UP IS REQUIRED IF:
                - Customer issue was NOT fully resolved
                - Agent promised to call back or follow up
                - Customer expressed frustration or dissatisfaction
                - Pending actions mentioned (e.g., "we will process your request")
                - Complaint was filed or escalation needed
                - Customer asked to be contacted later

                FOLLOW-UP IS NOT REQUIRED IF:
                - Issue was completely resolved
                - Customer expressed satisfaction/thanks
                - Simple inquiry answered fully
                """.formatted(sb.toString(), customerName != null ? customerName : "Unknown");

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

            String prompt = """
                You are a customer service AI assistant. You MUST use the REAL CUSTOMER DATA provided below.
                Return ONLY valid JSON.

                ============================================================
                 LATEST MESSAGE (RESPOND TO THIS IN SUGGESTION):
                ============================================================
                "%s"

                ============ CHECKLIST GUIDE + CUSTOMER DATA ============
                %s
                =========================================================

                ============================================================
                CONVERSATION HISTORY (%d messages):
                ============================================================
                %s

                ============================================================
                OUTPUT FORMAT (Return ONLY this JSON):
                ============================================================
                {
                  "overall_sentiment_score": <-1 to 1>,
                  "current_sentiment_score": <-1 to 1>,
                  "current_sentiment_label": "positive" | "negative" | "neutral",
                  "summary": "<end-to-end conversation summary>",
                  "suggestions": ["<reply that DIRECTLY responds to LATEST message>"]
                }

                ============================================================
                SUMMARY RULES (for agent's internal reference):
                ============================================================
                - Summarize the ENTIRE conversation from START to END
                - Include: what customer asked, what agent replied, what data was found, decisions made, current status
                - Write 4-5 sentences covering the FULL interaction chronologically
                - Include specific details: card numbers, amounts, eligibility status, dates
                - DO NOT start with "Hi" - this is for agent reference only
                - NEVER include sentiment/emotion words

                GOOD SUMMARY EXAMPLE:
                "Customer initiated conversation requesting credit card annual fee waiver. System retrieved customer data showing {credit cards count} on file. Cashback card (ending 1117) has ₱50,000 annual spend which does not meet the ₱180,000 requirement for fee waiver. Rewards card (ending 1116) has ₱30,000 spend in 90 days which qualifies for NAFFL (No Annual Fee For Life). Customer was informed about eligibility and provided with next steps to call (02) 88-700-700."

                ============================================================
                SUGGESTION RULES (MUST RESPOND TO LATEST MESSAGE):
                ============================================================
                Your suggestion MUST respond to the LATEST MESSAGE above, NOT to earlier messages.

                IF LATEST MESSAGE IS:
                - "thank you" / "thanks" → Reply: "You're welcome! Is there anything else I can help you with?"
                - "bye" / "goodbye" → Reply: "Goodbye! Have a great day. Feel free to reach out if you need help."
                - "ok" / "okay" / "alright" → Reply: "Is there anything else I can assist you with?"
                - "yes" / "no" → Respond appropriately to what they're confirming/denying
                - A question about their data → Answer using the CUSTOMER DATA above
                - A complaint → Address with empathy

                TONE RULES (talking TO customer, not ABOUT them):
                "Hi [First Name], you have {credit cards count} cards..."
                "Your Rewards card qualifies..."
                "Mr. [Name] has..." (WRONG)
                "He has already met..." (WRONG)

                FORBIDDEN PHRASES:
                 "Based on the data..." / "According to records..."
                 "Mr./Mrs. [Name]" (use first name only)
                 Responding to old topics when customer said "thank you"

                ============================================================
                SENTIMENT SCORING RULES:
                ============================================================
                - Score range: -1 to +1
                - Score > 0.3 = positive, Score < -0.3 = negative, between = neutral
                - SINGLE MESSAGE: overall_sentiment_score MUST EQUAL current_sentiment_score (identical)
                - MULTIPLE MESSAGES:
                  * current_sentiment_score = 70%% latest message sentiment + 30%% trend
                  * overall_sentiment_score = average of ALL message sentiments
                """.formatted(latestUserMsg, checklistContext, messages.size(), sb.toString());

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

            String prompt = """
                You are a STRICT customer service intent classifier.
                Return ONLY one of these exact values (no quotes, no explanation):
                FEE_WAIVER
                HOME_LOAN_CLOSURE
                POLICY
                CLAIMS
                TELCO
                BILLING
                GENERAL

                STRICT CLASSIFICATION RULES:

                Return "FEE_WAIVER" if customer mentions ANY of these:
                - Credit card fee waiver / annual fee waiver
                - NAFFL (No Annual Fee For Life)
                - Waiving/removing card fees
                - "Am I eligible for fee waiver?" (checking eligibility)
                - "Do I qualify for fee waiver?" (checking eligibility)
                - "Which rewards do I have on my card?" (card benefits/rewards)
                - "What benefits do I have on my credit card?" (card benefits)
                - Asking about THEIR credit card data/eligibility/rewards

                Return "HOME_LOAN_CLOSURE" if customer mentions ANY of these:
                - CLOSING or SETTLING a home loan
                - Loan FORECLOSURE or PRE-CLOSURE
                - Paying OFF home loan EARLY
                - Getting NOC after loan closure
                - "How many home loans do I have?" (their loan data)
                - "What is my home loan balance?" (their loan data)
                - "What is my outstanding loan amount?" (their loan data)
                - "What is my prepayment penalty?" (their loan data)
                - "Show my home loan details" (their loan data)
                - Asking about THEIR home loan data/balance/status

                Return "POLICY" if customer wants to SEE/LIST/COUNT their personal policy DATA:
                - "How many policies do I have?" (counting their policies)
                - "What are my policies?" (listing their policies)
                - "Show my policy details" (viewing their data)
                - "List my policies" (listing their data)
                - "What is my policy number?" (their specific data)
                - KEY: Asking for their PERSONAL POLICY DATA

                Return "CLAIMS" if customer wants to SEE/CHECK their personal claim DATA:
                - "How many claims do I have?" (counting their claims)
                - "What is my claim status?" (checking their status)
                - "Show my claims" (listing their claims)
                - "Is my claim approved?" (checking their status)
                - KEY: Asking for their PERSONAL CLAIM DATA

                Return "TELCO" if customer mentions ANY of these (mobile/telecom related):
                - Mobile plan / data plan / internet package
                - "What is my current plan?" / "Show my plan"
                - "How much data do I have?" / "Check my quota" / "Remaining data"
                - "I need more data" / "Upgrade my plan" / "Better plan"
                - "Recommend a plan" / "Suggest a plan" / "Which plan"
                - "Roaming" / "Travel to Singapore/Malaysia/Thailand"
                - "Add-on" / "Extra data" / "Top up" / "Pulsa"
                - "Data usage" / "Habis kuota" / "Kuota malam"
                - "TikTok plan" / "YouTube plan" / "WhatsApp unlimited"
                - "Prepaid" / "Postpaid" / "Recharge"
                - "Weekly plan" / "Monthly plan" / "7 days" / "30 days"
                - "Promo" / "Lebaran offer" / "Weekend special"
                - "Cheap plan" / "Budget plan" / "Murah"
                - "Student plan" / "Mahasiswa"
                - KEY: Anything related to mobile/telecom plans, data, or services

                Return "BILLING" when the customer asks about their PAYMENT POSITION or a
                CARD RESTRICTION - in any wording, in any language. Judge by what the
                customer needs to know, not by matching these words:
                - What they owe: "how much do I owe", "outstanding amount", "my balance",
                  "what's my bill", "minimum due", "statement balance"
                - When to pay: "when is my payment due", "due date", "am I late",
                  "am I overdue", "did my payment go through"
                - A billing summary: "summarise my billing", "billing for my cards",
                  "give me a summary of my account"
                - A restricted card: "why is my card blocked", "card declined",
                  "my card isn't working", "why can't I use my card"
                - Restoring a card: "can my card be unblocked", "how do I unblock it",
                  "what do I need to do to use my card again"
                - Same meanings in other languages, e.g. "我還欠多少錢", "什麼時候到期",
                  "我的卡為什麼被封鎖", "berapa tagihan saya"
                - KEY: the answer would come from their STATEMENT or CARD STATUS

                BILLING BOUNDARIES - these are NOT billing:
                - Annual fee, late fee, waiving a fee, NAFFL, card rewards or benefits,
                  fee eligibility → FEE_WAIVER
                - Home loan balance, loan payoff, foreclosure, prepayment penalty
                  → HOME_LOAN_CLOSURE
                - Insurance policies or claims → POLICY or CLAIMS
                - Mobile data, quota, recharge, mobile plans → TELCO

                Return "GENERAL" for process/how-to/FAQ questions:
                - "How do I renew my policy?" → GENERAL (process question)
                - "How do I file a claim?" → GENERAL (process question)
                - "What is a premium?" → GENERAL (definition)
                - "What if I miss a payment?" → GENERAL (FAQ)
                - "What documents do I need?" → GENERAL (FAQ)
                - Greetings (hi, hello)

                CRITICAL - MY/YOUR DATA vs GENERAL PROCESS:
                | Question | Intent |
                | "I want to waive my annual fee" | FEE_WAIVER |
                | "Am I eligible for fee waiver?" | FEE_WAIVER |
                | "Which rewards do I have?" | FEE_WAIVER |
                | "I want to close my home loan" | HOME_LOAN_CLOSURE |
                | "How many home loans do I have?" | HOME_LOAN_CLOSURE |
                | "What is my home loan balance?" | HOME_LOAN_CLOSURE |
                | "What is my outstanding loan amount?" | HOME_LOAN_CLOSURE |
                | "What is my prepayment penalty?" | HOME_LOAN_CLOSURE |
                | "How much do I owe on my card?" | BILLING |
                | "What is my outstanding amount and due date?" | BILLING |
                | "Summarise the billing for my cards" | BILLING |
                | "Why is my card blocked?" | BILLING |
                | "Can my card be unblocked?" | BILLING |
                | "When is my payment due?" | BILLING |
                | "What do I need to do to use my card again?" | BILLING |
                | "How do I get my card working again?" | BILLING |
                | "My card is not working, why?" | BILLING |
                | "How many policies do I have?" | POLICY |
                | "What is my claim status?" | CLAIMS |
                | "I need more data" | TELCO |
                | "What is my current plan?" | TELCO |
                | "Recommend a plan for me" | TELCO |
                | "Roaming for Singapore" | TELCO |
                | "How do I file a claim?" | GENERAL |
                | "What is a premium?" | GENERAL |

                RULE: If question asks about MY/YOUR personal data (cards, loans, policies, claims, mobile plans) → use appropriate category
                RULE: If question asks HOW TO DO something or WHAT IS something → GENERAL
                RULE (OVERRIDES THE ABOVE): a "how do I" or "what do I need to do" question about
                THEIR OWN card, payment or account is NOT general. "What do I need to do to use my
                card again" is about their blocked card → BILLING. Only route to GENERAL when the
                question is about a process in the abstract, with no reference to their own account.

                LATEST MESSAGE: "%s"

                Classification:
                """.formatted(latestMessage);

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

            String prompt = """
                You are a compliance analyst for customer service conversations.
                Analyze the following AGENT messages and determine if each compliance metric is met.
                Return ONLY valid JSON with true/false for each metric.

                ============================================================
                AGENT MESSAGES TO ANALYZE:
                ============================================================
                %s

                ============================================================
                OUTPUT FORMAT (Return ONLY this JSON):
                ============================================================
                {
                  "greeting": true/false,
                  "empathy": true/false,
                  "clarity": true/false,
                  "productTnC": true/false,
                  "valediction": true/false
                }

                ============================================================
                COMPLIANCE METRICS DEFINITIONS:
                ============================================================

                GREETING (true if agent properly greeted the customer):
                - Said "Hello", "Hi", "Good morning/afternoon/evening"
                - Welcomed the customer
                - Introduced themselves or the company
                - Examples: "Hello, how can I help you?", "Hi there, thank you for contacting us"

                EMPATHY (true if agent showed empathy or understanding):
                - Acknowledged customer's feelings or situation
                - Used phrases like "I understand", "I'm sorry to hear", "I appreciate your patience"
                - Showed concern for customer's issue
                - Validated customer's frustration or concern
                - Examples: "I understand how frustrating this must be", "I'm sorry for the inconvenience"

                CLARITY (true if agent communicated clearly):
                - Provided clear and understandable explanations
                - Avoided jargon or explained technical terms
                - Gave step-by-step instructions when needed
                - Confirmed understanding with customer
                - Messages are well-structured and easy to follow

                PRODUCT T&C (true if agent mentioned terms and conditions):
                - Referenced terms and conditions
                - Mentioned policies, rules, or guidelines
                - Explained product limitations or requirements
                - Discussed eligibility criteria
                - Mentioned fees, charges, or penalties
                - Examples: "According to our policy", "The terms state that", "Please note the conditions"

                VALEDICTION (true if agent properly closed the conversation):
                - Said goodbye or closing statement
                - Thanked the customer
                - Offered further assistance
                - Used closing phrases like "Have a great day", "Thank you for contacting us"
                - Asked if there's anything else they can help with
                - Examples: "Is there anything else I can help you with?", "Thank you, have a great day!"

                ============================================================
                IMPORTANT RULES:
                ============================================================
                - Only analyze AGENT messages, not customer messages
                - Return true ONLY if the metric is clearly met
                - Return false if uncertain or metric is not present
                - Be strict in evaluation - compliance must be clearly demonstrated
                """.formatted(sb.toString());

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
