package com.agentassist.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for follow-up requirement check.
 * Called at the end of an interaction to determine if follow-up is needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpCheckRequest {

    /**
     * The interaction ID to analyze.
     */
    @NotBlank(message = "Interaction ID is required")
    private String interactionId;

    /**
     * Optional: Full conversation transcript if not fetching from DB.
     * Format: List of messages like "Customer: message" or "Agent: message"
     */
    private List<String> transcript;

    /**
     * Optional: Customer's mobile number for context.
     */
    private String mobileNumber;

    /**
     * Optional: Customer name for personalized analysis.
     */
    private String customerName;
}
