package com.agentassist.controller;

import com.agentassist.dto.requestDTO.TranslateToTargetRequest;
import com.agentassist.dto.responseDTO.TranslationResponse;
import com.agentassist.service.translation.LanguageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final LanguageService languageService;

    @PostMapping("")
    public ResponseEntity<TranslationResponse> toTarget(@Valid @RequestBody TranslateToTargetRequest req) {

        if (req.getText() == null || req.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // detect language
        String detected = languageService.detect(req.getText());

        // normalize into English
        String english = detected.equalsIgnoreCase("en")
                ? req.getText()
                : languageService.toEnglish(req.getText());

        // translate from English to target
        String translated = languageService.fromEnglish(english, req.getTargetLanguage());

        // construct response
        TranslationResponse r = new TranslationResponse();
        r.setTranslatedText(translated);
        r.setTargetLanguage(req.getTargetLanguage());

        return ResponseEntity.ok(r);
    }
}
