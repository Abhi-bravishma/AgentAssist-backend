package com.agentassist.service.translation;

import com.agentassist.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageService {

    private final AiProviderFactory aiProviderFactory;

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
