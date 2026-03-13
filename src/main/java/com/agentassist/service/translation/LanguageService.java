package com.agentassist.service.translation;

import com.agentassist.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LanguageService {

    private final AiProvider aiProvider;

    public String detectLanguage(String text) {
        return aiProvider.detectLanguage(text);
    }

    public String toEnglish(String text) {
        return aiProvider.translateToEnglish(text);
    }

    public String fromEnglish(String english, String targetLang) {
        if (targetLang == null || targetLang.equals("und")) {
            return english;
        }
        return aiProvider.translateFromEnglish(english, targetLang);
    }
}
