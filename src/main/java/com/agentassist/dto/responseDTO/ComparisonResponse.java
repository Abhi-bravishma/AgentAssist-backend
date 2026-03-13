package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for comparison mode where both OpenAI and Ollama results are returned.
 * Used when provider=both in the request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResponse {

    /**
     * Results from OpenAI provider
     */
    private ProviderAnalysisResult openai;

    /**
     * Results from Ollama provider
     */
    private ProviderAnalysisResult ollama;

    /**
     * Knowledge sources from RAG (shared between providers)
     */
    private List<KnowledgeSource> knowledgeSources;

    /**
     * Number of relevant documents found in the knowledge base
     */
    private int documentsFound;

    /**
     * Whether knowledge base context was used for generating suggestions
     */
    private boolean usedKnowledgeBase;

    /**
     * Summary of differences between providers
     */
    private ComparisonSummary comparison;

    /**
     * Summary comparing the two provider results
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComparisonSummary {
        /**
         * Difference in overall sentiment scores (openai - ollama)
         */
        private double sentimentDifference;

        /**
         * Which provider was faster
         */
        private String fasterProvider;

        /**
         * Latency difference in milliseconds
         */
        private long latencyDifferenceMs;

        /**
         * Whether both providers agree on sentiment label
         */
        private boolean sentimentLabelMatch;
    }
}
