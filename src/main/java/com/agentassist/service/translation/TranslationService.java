package com.agentassist.service.translation;

import com.agentassist.ai.ProviderType;
import com.agentassist.dto.responseDTO.ComparisonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final LanguageService languageService;

    // Default methods (use OpenAI)
    public String detect(String text) {
        return languageService.detectLanguage(text);
    }

    public String toEnglish(String text) {
        return languageService.toEnglish(text);
    }

    public String fromEnglish(String english, String target) {
        return languageService.fromEnglish(english, target);
    }

    // Provider-specific methods
    public String detect(String text, ProviderType provider) {
        return languageService.detectLanguage(text, provider);
    }

    public String toEnglish(String text, ProviderType provider) {
        return languageService.toEnglish(text, provider);
    }

    public String fromEnglish(String english, String target, ProviderType provider) {
        return languageService.fromEnglish(english, target, provider);
    }

    // Comparison methods
    public ComparisonResponse detectComparison(String text) {
        return languageService.detectLanguageComparison(text);
    }

    public ComparisonResponse toEnglishComparison(String text) {
        return languageService.toEnglishComparison(text);
    }
}

