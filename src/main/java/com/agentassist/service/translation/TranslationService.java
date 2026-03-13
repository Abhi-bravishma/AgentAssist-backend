package com.agentassist.service.translation;



import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranslationService {

    private final LanguageService languageService; // from your project

    public String detect(String text) {
        return languageService.detectLanguage(text);
    }

    public String toEnglish(String text) {
        return languageService.toEnglish(text);
    }

    public String fromEnglish(String english, String target) {
        return languageService.fromEnglish(english, target);
    }
}

