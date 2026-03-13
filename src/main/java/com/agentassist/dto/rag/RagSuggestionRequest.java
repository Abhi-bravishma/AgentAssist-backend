package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for RAG suggestion API.
 * Mirrors the AgentAssistRequest in the RAG application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSuggestionRequest {

    /**
     * Full conversation history as a list of messages.
     * Format: "Role: message" (e.g., "Customer: I need help", "Agent: How can I assist?")
     */
    private List<String> conversationHistory;

    /**
     * The latest message from the customer that needs a response.
     */
    private String latestMessage;

    /**
     * Optional agent prompt configuration ID.
     */
    private Long agentId;

    /**
     * Optional search filters.
     */
    private RagSearchFilter filters;

    /**
     * Number of suggestions to generate (default: 3).
     */
    private Integer suggestionCount;

    /**
     * Company ID for multi-tenant document filtering.
     */
    private Long companyId;
}
