package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO from RAG suggestion API.
 * Mirrors the AgentAssistResponse in the RAG application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSuggestionResponse {

    /**
     * List of AI-generated reply suggestions.
     */
    private List<String> suggestions;

    /**
     * List of source documents that were used to generate suggestions.
     */
    private List<RagSourceDocument> sourceDocuments;

    /**
     * Total number of relevant documents found.
     */
    private int documentsFound;

    /**
     * Whether context from documents was used.
     */
    private boolean usedContext;

    /**
     * Name of the agent prompt configuration used.
     */
    private String agentPrompt;

    /**
     * Summary of the conversation context.
     */
    private String conversationSummary;

    /**
     * Indicates if the request was blocked by guardrails.
     */
    private boolean blocked;

    /**
     * Reason for blocking (if blocked).
     */
    private String blockedReason;
}
