package com.agentassist.dto.responseDTO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for follow-up requirement analysis.
 * Simplified response with only essential fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FollowUpCheckResponse {

    /**
     * Whether follow-up is required for this interaction.
     */
    private boolean followUpRequired;

    /**
     * Reason for follow-up (only populated if followUpRequired is true).
     * Empty string if no follow-up needed.
     */
    private String followUp;

    /**
     * Summary of the conversation.
     */
    private String conversationSummary;
}
