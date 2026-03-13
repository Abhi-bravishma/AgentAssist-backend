package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the one-shot message processing endpoint.
 * Contains sentiment analysis, summary, suggestions, and knowledge sources.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneShotResponse {

    /**
     * Overall sentiment score for the entire conversation (-1 to 1).
     */
    private double overallSentiment;

    /**
     * Current sentiment score for the latest message (-1 to 1).
     */
    private double currentSentiment;

    /**
     * AI-generated summary of the conversation.
     */
    private String summary;

    /**
     * List of AI-generated reply suggestions.
     */
    private List<SuggestedResponse> suggestedResponses;

    /**
     * List of knowledge base documents used to generate suggestions.
     * Shows which documents from the RAG system were used as context.
     */
    private List<KnowledgeSource> knowledgeSources;

    /**
     * Number of relevant documents found in the knowledge base.
     */
    private int documentsFound;

    /**
     * Whether knowledge base context was used for generating suggestions.
     */
    private boolean usedKnowledgeBase;
}