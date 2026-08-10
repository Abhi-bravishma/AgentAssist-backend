package com.agentassist.service.translation;

import com.agentassist.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final AiProviderFactory aiProviderFactory;

    /**
     * Whether a language tag is one the pipeline can act on (plan §4.10):
     * non-blank and not the "und" (undetermined) sentinel that
     * {@code detectLanguage} returns when detection fails.
     */
    public static boolean isUsable(String languageTag) {
        return languageTag != null && !languageTag.isBlank() && !"und".equalsIgnoreCase(languageTag);
    }

    /**
     * The language replies should be written in (plan §4.9).
     * <p>
     * The conversation's base language wins when known: a customer who types one
     * English message mid-conversation still gets the reply in their language.
     * Falls back to the current message's detected language when no base
     * language has been recorded, or when the recorded one is unusable (§4.10).
     */
    public static String replyLanguage(String baseLanguage, String currentLanguage) {
        if (isUsable(baseLanguage)) {
            return baseLanguage;
        }
        return currentLanguage;
    }

    public String detectLanguage(String text) {
        return aiProviderFactory.active().detectLanguage(text);
    }

    /** Alias kept from the collapsed TranslationService (Part 3c). */
    public String detect(String text) {
        return detectLanguage(text);
    }

    public String toEnglish(String text) {
        return aiProviderFactory.active().translateToEnglish(text);
    }

    public String fromEnglish(String english, String targetLang) {
        if (!isUsable(targetLang)) {
            // §4.10: this is a failure being papered over, not a normal path -
            // say so instead of silently handing the agent English.
            log.warn("[Language] Translation target is '{}' (language detection failed upstream and no usable base language) - returning ENGLISH text unchanged", targetLang);
            return english;
        }
        return aiProviderFactory.active().translateFromEnglish(english, targetLang);
    }
}
