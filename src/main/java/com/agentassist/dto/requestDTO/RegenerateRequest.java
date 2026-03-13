package com.agentassist.dto.requestDTO;

import lombok.Data;

@Data
public class RegenerateRequest {
    private String interactionId;
    private String message;  // Current/previous suggestion to regenerate from
}
