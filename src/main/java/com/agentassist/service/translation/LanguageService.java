package com.agentassist.service.translation;

import com.agentassist.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageService {

    private final AiProviderFactory aiProviderFactory;

    /**
     * The language replies should be written in (plan §4.9).
     * <p>
     * The conversation's base language wins when known: a customer who types one
     * English message mid-conversation still gets the reply in their language.
     * Falls back to the current message's detected language when no base
     * language has been recorded yet.
     */
    public static String replyLanguage(String baseLanguage, String currentLanguage) {
        if (baseLanguage != null && !baseLanguage.isBlank()) {
            return baseLanguage;
        }
        return currentLanguage;
    }

    public String detectLanguage(String text) {
        return aiProviderFactory.active().detectLanguage(text);
    }

    public String toEnglish(String text) {
        return aiProviderFactory.active().translateToEnglish(text);
    }

    public String fromEnglish(String english, String targetLang) {
        if (targetLang == null || targetLang.equals("und")) {
            return english;
        }
        return aiProviderFactory.active().translateFromEnglish(english, targetLang);
    }
}
