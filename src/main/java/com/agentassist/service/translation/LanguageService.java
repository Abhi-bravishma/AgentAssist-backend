package com.agentassist.service.translation;

import com.agentassist.ai.DualAiProviderService;
import com.agentassist.ai.ProviderType;
import com.agentassist.dto.responseDTO.ComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageService {

    private final DualAiProviderService dualAiProviderService;

    /**
     * Detect language using default provider (OpenAI)
     */
    public String detectLanguage(String text) {
        return detectLanguage(text, ProviderType.OPENAI);
    }

    /**
     * Detect language using specified provider
     */
    public String detectLanguage(String text, ProviderType provider) {
        return dualAiProviderService.detectLanguage(provider, text);
    }

    /**
     * Detect language with both providers for comparison
     */
    public ComparisonResponse detectLanguageComparison(String text) {
        return dualAiProviderService.detectLanguageComparison(text);
    }

    /**
     * Translate to English using default provider (OpenAI)
     */
    public String toEnglish(String text) {
        return toEnglish(text, ProviderType.OPENAI);
    }

    /**
     * Translate to English using specified provider
     */
    public String toEnglish(String text, ProviderType provider) {
        return dualAiProviderService.translateToEnglish(provider, text);
    }

    /**
     * Translate to English with both providers for comparison
     */
    public ComparisonResponse toEnglishComparison(String text) {
        return dualAiProviderService.translateToEnglishComparison(text);
    }

    /**
     * Translate from English using default provider (OpenAI)
     */
    public String fromEnglish(String english, String targetLang) {
        return fromEnglish(english, targetLang, ProviderType.OPENAI);
    }

    /**
     * Translate from English using specified provider
     */
    public String fromEnglish(String english, String targetLang, ProviderType provider) {
        if (targetLang == null || targetLang.equals("und")) {
            return english;
        }
        return dualAiProviderService.translateFromEnglish(provider, english, targetLang);
    }
}
