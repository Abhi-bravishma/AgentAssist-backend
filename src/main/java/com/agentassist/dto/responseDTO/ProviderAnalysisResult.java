package com.agentassist.dto.responseDTO;

import com.agentassist.ai.ProviderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO holding analysis results from a single AI provider.
 * Includes timing information for performance comparison.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderAnalysisResult {

    /**
     * The provider that generated this result
     */
    private ProviderType provider;

    /**
     * Provider name with model info (e.g., "OpenAI:gpt-4o-mini")
     */
    private String providerName;

    /**
     * Overall sentiment score for the entire conversation (-1 to 1)
     */
    private double overallSentiment;

    /**
     * Current sentiment score for the latest message (-1 to 1)
     */
    private double currentSentiment;

    /**
     * Sentiment label (positive, negative, neutral)
     */
    private String sentimentLabel;

    /**
     * AI-generated summary of the conversation
     */
    private String summary;

    /**
     * List of AI-generated reply suggestions (English)
     */
    private List<String> suggestions;

    /**
     * Detected language code (ISO 639-1)
     */
    private String detectedLanguage;

    /**
     * English translation of the message
     */
    private String englishTranslation;

    /**
     * Time taken for analysis in milliseconds
     */
    private long latencyMs;

    /**
     * Whether this provider call was successful
     */
    private boolean success;

    /**
     * Error message if the call failed
     */
    private String errorMessage;
}
