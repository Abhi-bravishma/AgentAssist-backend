package com.agentassist.ai;

import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.agentassist.dto.responseDTO.FollowUpCheckResponse;

import java.util.List;

/**
 * Interface for AI providers (OpenAI, Ollama).
 * Implementations handle conversation analysis, translation, and language detection.
 */
public interface AiProvider {

    /**
     * Analyze a single text and return sentiment, summary, and suggestions.
     */
    AiAnalysisResult analyzeText(String text);

    /**
     * Analyze a full conversation and return comprehensive analysis.
     *
     * @param messages      List of conversation messages in English
     * @param latestUserMsg The latest user message
     * @return Analysis bundle with sentiment scores, summary, and suggestions
     */
    AiAnalysisBundle analyzeConversation(List<String> messages, String latestUserMsg);

    /**
     * Analyze a full conversation with additional context (e.g., customer policy data).
     *
     * @param messages      List of conversation messages in English
     * @param latestUserMsg The latest user message
     * @param policyContext Additional context about customer's policies/claims from Salesforce
     * @return Analysis bundle with sentiment scores, summary, and suggestions
     */
    AiAnalysisBundle analyzeConversationWithContext(List<String> messages, String latestUserMsg, String policyContext);

    /**
     * Translate text to English.
     */
    String translateToEnglish(String text);

    /**
     * Translate English text to target language.
     *
     * @param english    Text in English
     * @param targetLang Target language ISO 639-1 code
     */
    String translateFromEnglish(String english, String targetLang);

    /**
     * Detect the language of the given text.
     *
     * @return ISO 639-1 language code, or "und" if undetermined
     */
    String detectLanguage(String text);

    /**
     * Compute overall sentiment score from a list of messages.
     *
     * @return Score from -1 (negative) to +1 (positive)
     */
    double computeOverallSentiment(List<String> messages);

    /**
     * Regenerate suggestions with different responses.
     *
     * @param messages           List of conversation messages in English
     * @param latestUserMsg      The latest user message
     * @param previousSuggestion Previous suggestion to avoid repeating
     * @return Analysis bundle with new suggestions
     */
    AiAnalysisBundle regenerateSuggestions(List<String> messages, String latestUserMsg, String previousSuggestion);

    /**
     * Regenerate suggestions with checklist context (for customer-specific data).
     *
     * @param messages           List of conversation messages in English
     * @param latestUserMsg      The latest user message
     * @param previousSuggestion Previous suggestion to reword
     * @param checklistContext   Customer data context (card numbers, amounts, eligibility)
     * @param customerName       Customer name for personalization
     * @return Analysis bundle with new suggestions containing same data, different wording
     */
    AiAnalysisBundle regenerateSuggestionsWithContext(List<String> messages, String latestUserMsg,
                                                       String previousSuggestion, String checklistContext,
                                                       String customerName);

    /**
     * Analyze conversation transcript to determine if follow-up is required.
     * Called at the end of an interaction.
     *
     * @param transcript    Full conversation transcript (list of "Role: message" strings)
     * @param customerName  Optional customer name for context
     * @return Follow-up analysis result with requirement, urgency, and actions
     */
    FollowUpCheckResponse analyzeFollowUpRequirement(List<String> transcript, String customerName);

    /**
     * Analyze conversation with checklist context (fee waiver, home loan closure).
     * Uses the checklist guide + customer-specific data to generate targeted suggestions.
     *
     * @param messages         List of conversation messages in English
     * @param latestUserMsg    The latest user message
     * @param checklistContext Combined checklist guide + customer data context
     * @param operationType    Type of operation (FEE_WAIVER, HOME_LOAN_CLOSURE)
     * @return Analysis bundle with sentiment scores, summary, and checklist-aware suggestions
     */
    AiAnalysisBundle analyzeConversationWithChecklist(List<String> messages, String latestUserMsg,
                                                       String checklistContext, String operationType);

    /**
     * Detect the type of operation the customer is asking about.
     * Uses AI to understand intent from conversation context.
     *
     * @param messages      List of conversation messages (can be in any language)
     * @param latestMessage The latest customer message
     * @return Operation type: "FEE_WAIVER", "HOME_LOAN_CLOSURE", or "GENERAL"
     */
    String detectOperationType(List<String> messages, String latestMessage);

    /**
     * Analyze agent messages for compliance.
     * Checks for: Greeting, Empathy, Clarity, Product T&C, Valediction.
     *
     * @param agentMessages List of agent message texts to analyze
     * @param interactionId Interaction ID for context
     * @return Compliance check results with true/false for each metric
     */
    ComplianceResponse analyzeCompliance(List<String> agentMessages, String interactionId);
}
