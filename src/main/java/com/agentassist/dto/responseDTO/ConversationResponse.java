package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for the conversation message processing endpoint.
 * Contains sentiment analysis, summary, suggestions, and knowledge sources.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

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

    /**
     * Whether customer policy data from Salesforce was used.
     */
    private boolean usedPolicyData;

    /**
     * Customer name from Salesforce (if policy data was fetched).
     */
    private String customerName;

    /**
     * Number of policies found for the customer.
     */
    private int policiesFound;

    /**
     * Number of claims found for the customer.
     */
    private int claimsFound;

    /**
     * Whether checklist context was used (fee waiver, home loan closure).
     */
    private boolean usedChecklist;

    /**
     * Type of checklist operation detected (FEE_WAIVER, HOME_LOAN_CLOSURE, or null).
     */
    private String checklistOperation;

    /**
     * Number of credit cards found for the customer (for fee waiver).
     */
    private int creditCardsFound;

    /**
     * Number of home loans found for the customer (for loan closure).
     */
    private int homeLoansFound;

    /**
     * Project/Bank name used for filtering (echoed back from request).
     */
    private String projectName;
}
