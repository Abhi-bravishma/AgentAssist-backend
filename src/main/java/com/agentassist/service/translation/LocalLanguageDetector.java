package com.agentassist.service.translation;

import com.agentassist.ai.support.LanguageSupport;
import com.optimaize.langdetect.DetectedLanguage;
import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * In-process language detection, so a message does not spend a full OpenAI
 * round-trip (~1.1s measured) learning that it is in English.
 *
 * <p>Uses the Optimaize n-gram detector: a few megabytes of language profiles,
 * loaded once at startup, restricted to the languages the registry supports
 * plus English and Chinese. Deliberately not Lingua, whose models would have
 * cost hundreds of megabytes on the shared VM this runs on.
 *
 * <p>It never guesses. {@link #detect} says whether it is confident; when it is
 * not — text too short, two candidates too close — the caller falls back to the
 * model, which is exactly what ran for every message before this existed.
 * Chinese goes through the same script resolution as the model path, so
 * {@code zh-Hans} / {@code zh-Hant} come out identically.</p>
 */
@Slf4j
@Component
public class LocalLanguageDetector {

    /** Registry languages (aa_language) plus the two the app always handles. */
    private static final List<String> LOCALES = List.of(
            "en", "zh-CN", "zh-TW",
            "id", "tl", "ms", "th", "vi", "hi", "ta", "ja", "ko",
            "fr", "es", "pt", "de", "it", "nl", "ar", "ru", "tr");

    public record Detection(String code, double confidence, double margin, boolean confident) {
        static Detection unknown() {
            return new Detection("und", 0, 0, false);
        }
    }

    private final LanguageDetector detector;
    private final TextObjectFactory textFactory;
    private final double minConfidence;
    private final double minMargin;
    private final int minLength;

    public LocalLanguageDetector(
            @Value("${ai.language.local.min-confidence:0.85}") double minConfidence,
            @Value("${ai.language.local.min-margin:0.30}") double minMargin,
            @Value("${ai.language.local.min-length:6}") int minLength) throws IOException {
        this.minConfidence = minConfidence;
        this.minMargin = minMargin;
        this.minLength = minLength;

        long start = System.currentTimeMillis();
        List<LdLocale> locales = LOCALES.stream().map(LdLocale::fromString).toList();
        List<LanguageProfile> profiles = new LanguageProfileReader().readBuiltIn(locales);
        this.detector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                .withProfiles(profiles)
                .build();
        // Chat lines are short; this factory skips the URL/number stripping meant
        // for documents and keeps everything the classifier can use.
        this.textFactory = CommonTextObjectFactories.forDetectingShortCleanText();
        log.info("[Language] Local detector ready: {} profiles in {}ms", profiles.size(), System.currentTimeMillis() - start);
    }

    /**
     * Detects the language of {@code text}. {@code confident} is false when the
     * text is too short to judge or the top two candidates are too close;
     * callers should then defer to the model.
     */
    public Detection detect(String text) {
        if (text == null) {
            return Detection.unknown();
        }
        String trimmed = text.strip();
        if (trimmed.length() < minLength) {
            return Detection.unknown();
        }

        TextObject textObject = textFactory.forText(trimmed);
        List<DetectedLanguage> ranked = detector.getProbabilities(textObject);
        if (ranked.isEmpty()) {
            return Detection.unknown();
        }
        DetectedLanguage top = ranked.get(0);
        double second = ranked.size() > 1 ? ranked.get(1).getProbability() : 0.0;
        double confidence = top.getProbability();
        double margin = confidence - second;
        boolean confident = confidence >= minConfidence && margin >= minMargin;
        return new Detection(iso(top.getLocale(), trimmed), confidence, margin, confident);
    }

    /** Same output contract as the model path: ISO 639-1, Chinese with its script. */
    private static String iso(LdLocale locale, String text) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        if ("zh".equals(language)) {
            return LanguageSupport.normalizeLanguageTag("zh", text);
        }
        return language;
    }
}
