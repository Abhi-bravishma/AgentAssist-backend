package com.agentassist.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DetectLanguageRequest {

    @NotBlank(message = "text is required")
    @Size(max = 10000, message = "text must not exceed 10000 characters")
    private String text;
}
