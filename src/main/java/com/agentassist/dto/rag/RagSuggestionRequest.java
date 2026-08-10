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
     * Number of suggestions to generate (default: 3).
     */
    private Integer suggestionCount;

    /**
     * Company ID for multi-tenant document filtering.
     */
    private Long companyId;

    /**
     * Additional context to include in prompt (e.g., customer policy data from Salesforce).
     * This will be appended to the knowledge base context when generating suggestions.
     */
    private String additionalContext;

    /**
     * Project/Bank name for filtering documents.
     * Examples: "ALLIANZ", "METRO", "HSBC"
     * Optional - if null/empty, searches all projects.
     */
    private String projectName;
}
