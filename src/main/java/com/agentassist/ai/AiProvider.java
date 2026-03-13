package com.agentassist.ai;

import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;

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
}
