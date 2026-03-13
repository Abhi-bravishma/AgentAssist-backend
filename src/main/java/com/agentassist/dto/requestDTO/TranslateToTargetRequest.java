package com.agentassist.dto.requestDTO;



import lombok.Data;

@Data
public class TranslateToTargetRequest {
    private String text;
    private String targetLanguage;
}
