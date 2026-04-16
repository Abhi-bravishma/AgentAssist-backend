package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for compliance check results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceResponse {

    private String interactionId;

    /**
     * Did the agent greet the customer properly?
     */
    private Boolean greeting;

    /**
     * Did the agent show empathy in their responses?
     */
    private Boolean empathy;

    /**
     * Was the agent clear in their communication?
     */
    private Boolean clarity;

    /**
     * Did the agent explain product terms and conditions?
     */
    private Boolean productTnC;

    /**
     * Did the agent properly close/end the conversation?
     */
    private Boolean valediction;

    /**
     * Timestamp when the check was performed.
     */
    private Instant checkedAt;
}
