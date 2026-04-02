package com.agentassist.dto.requestDTO;

import lombok.Data;

@Data
public class RegenerateRequest {
    private String interactionId;
    private String message;  // Current/previous suggestion to regenerate from

    // Optional context for checklist-based regeneration
    private String checklistContext;  // Customer data context (card numbers, amounts, eligibility)
    private String customerName;      // Customer name for personalization
}
