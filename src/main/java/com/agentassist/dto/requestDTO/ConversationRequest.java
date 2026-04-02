package com.agentassist.dto.requestDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for processing conversation messages.
 * Used by the /api/v1/agent-assistant/process endpoint.
 */
@Data
public class ConversationRequest {

    @NotBlank(message = "interactionId is required")
    private String interactionId;

    @NotBlank(message = "from is required")
    @Pattern(regexp = "customer|user", message = "from must be 'customer' or 'user'")
    private String from;

    @NotBlank(message = "message is required")
    @Size(max = 10000, message = "message must not exceed 10000 characters")
    private String message;

    /**
     * Customer's mobile number for Salesforce policy lookup.
     * Optional - if provided, policy/claims data will be fetched on first message.
     */
    private String mobileNumber;

    /**
     * Project/Bank name for filtering knowledge base documents.
     * Examples: "ALLIANZ", "METRO", "HSBC"
     * Optional - if empty/null, searches all projects.
     */
    private String projectName;
}
