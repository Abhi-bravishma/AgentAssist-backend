package com.agentassist.ai.support;

import com.agentassist.configregistry.LanguageRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Translation and language detection. Method bodies moved VERBATIM from
 * BaseAiProvider in the Part 2 split; since Part 3 (§4.11) the translation
 * target's display name comes from the aa_language registry instead of the
 * old Chinese-only switch, so "id" reads "Indonesian" in the prompt.
 */
@Slf4j
@RequiredArgsConstructor
public class TranslationEngine {

    private final ChatCaller chat;
    private final String providerName;
    private final PromptService promptService;
    private final LanguageRegistryService languageRegistry;

    public String translateToEnglish(String text) {
        log.info("[AI:{}] translateToEnglish called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            // This translation becomes the knowledge base search query, so a mangled
            // proper noun does not just read badly - it lowers the embedding score and
            // pulls the wrong document into the results.
            String prompt = promptService.renderDefault(TemplateKeys.AI_TRANSLATE_TO_ENGLISH,
                    Map.of("text", text));

            String out = chat.call(prompt);
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

    public String translateFromEnglish(String english, String targetLang) {
        log.info("[AI:{}] translateFromEnglish called, target: {}, text length: {}", providerName, targetLang, english.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = promptService.renderDefault(TemplateKeys.AI_TRANSLATE_FROM_ENGLISH, Map.of(
                    "target_language", languageRegistry.describe(targetLang),
                    "text", english));

            String out = chat.call(prompt);
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

    public String detectLanguage(String text) {
        log.info("[AI:{}] detectLanguage called, text length: {}", providerName, text.length());
        long startTime = System.currentTimeMillis();

        try {
            String prompt = promptService.renderDefault(TemplateKeys.AI_DETECT_LANGUAGE,
                    Map.of("text", text));

            String lang = chat.call(prompt);
            if (lang == null) {
                log.warn("[AI:{}] detectLanguage returned null, defaulting to 'und'", providerName);
                return "und";
            }
            String result = LanguageSupport.normalizeLanguageTag(lang, text);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[AI:{}] detectLanguage completed in {}ms, raw: '{}', detected: {}",
                    providerName, duration, lang.trim(), result);
            return result;

        } catch (Exception e) {
            log.error("[AI:{}] detectLanguage failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            return "und";
        }
    }
}
