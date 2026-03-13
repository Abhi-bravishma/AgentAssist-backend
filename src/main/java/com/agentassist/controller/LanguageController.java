package com.agentassist.controller;

import com.agentassist.dto.requestDTO.DetectLanguageRequest;
import com.agentassist.dto.responseDTO.DetectLanguageResponse;
import com.agentassist.service.translation.LanguageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/language")
@RequiredArgsConstructor
@Tag(name = "Language", description = "Language detection API")
public class LanguageController {

    private final LanguageService languageService;

    @PostMapping("/detect")
    public DetectLanguageResponse detect(@Valid @RequestBody DetectLanguageRequest req) {
        String lang = languageService.detectLanguage(req.getText());
        DetectLanguageResponse response = new DetectLanguageResponse();
        response.setLanguage(lang);
        return response;
    }
}
