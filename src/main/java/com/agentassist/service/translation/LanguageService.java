package com.agentassist.service.translation;

import com.agentassist.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageService {

    private final AiProviderFactory aiProviderFactory;
    private final LocalLanguageDetector localDetector;

    /**
     * local  — in-process detector, model only when it is not confident (default)
     * llm    — the model for every message, exactly as before
     * shadow — the model decides; the local result is logged next to it so the
     *          agreement rate can be read off real traffic before switching
     */
    @Value("${ai.language.detection:local}")
    private String detectionMode;

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
        String mode = detectionMode == null ? "local" : detectionMode.trim().toLowerCase();
        if ("llm".equals(mode)) {
            return aiProviderFactory.active().detectLanguage(text);
        }

        LocalLanguageDetector.Detection local = localDetector.detect(text);

        if ("shadow".equals(mode)) {
            String llm = aiProviderFactory.active().detectLanguage(text);
            log.info("[Language] shadow: local={} ({}, margin {}) llm={} {}",
                    local.code(), pct(local.confidence()), pct(local.margin()), llm,
                    local.code().equals(llm) ? "agree" : "DISAGREE");
            return llm;
        }

        if (local.confident()) {
            log.info("[Language] local: {} ({}, margin {}) - no model call", local.code(),
                    pct(local.confidence()), pct(local.margin()));
            return local.code();
        }
        log.info("[Language] local not confident ({} at {}, margin {}) - asking the model",
                local.code(), pct(local.confidence()), pct(local.margin()));
        return aiProviderFactory.active().detectLanguage(text);
    }

    private static String pct(double v) {
        return String.format("%.0f%%", v * 100);
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
