package com.agentassist.dto.responseDTO;

import lombok.Data;

@Data
public class TranslationResponse {
    private String translatedText;
    private String targetLanguage;
}
