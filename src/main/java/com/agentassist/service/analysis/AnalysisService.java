package com.agentassist.service.analysis;

import com.agentassist.ai.DualAiProviderService;
import com.agentassist.ai.ProviderType;
import com.agentassist.dto.rag.RagSourceDocument;
import com.agentassist.dto.rag.RagSuggestionResponse;
import com.agentassist.dto.responseDTO.*;
import com.agentassist.model.MessageEntity;
import com.agentassist.model.SenderType;
import com.agentassist.service.conversation.MessageService;
import com.agentassist.service.rag.RagClient;
import com.agentassist.service.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final DualAiProviderService dualAiProviderService;
    private final MessageService messageService;
    private final TranslationService translationService;
    private final RagClient ragClient;

    // -------------------------------------------------------------------------------------
    // CORE AI METHODS
    // -------------------------------------------------------------------------------------

    /**
     * Analyze conversation using default provider (OpenAI)
     */
    public AiAnalysisBundle analyzeConversation(List<String> englishConversation, String latestMessage) {
        return analyzeConversation(englishConversation, latestMessage, ProviderType.OPENAI);
    }

    /**
     * Analyze conversation using specified provider
     */
    public AiAnalysisBundle analyzeConversation(List<String> englishConversation, String latestMessage, ProviderType provider) {
        return dualAiProviderService.analyzeConversation(provider, englishConversation, latestMessage);
    }

    /**
     * Analyze conversation with both providers for comparison
     */
    public ComparisonResponse analyzeConversationComparison(List<String> englishConversation, String latestMessage) {
        return dualAiProviderService.analyzeConversationComparison(englishConversation, latestMessage);
    }

    /**
     * Analyze text using default provider
     */
    public AiAnalysisResult analyzeText(String englishText) {
        return analyzeText(englishText, ProviderType.OPENAI);
    }

    /**
     * Analyze text using specified provider
     */
    public AiAnalysisResult analyzeText(String englishText, ProviderType provider) {
        var aiProvider = dualAiProviderService.getProvider(provider);
        if (aiProvider == null) {
            log.warn("Provider {} not available for text analysis", provider);
            return new AiAnalysisResult();
        }
        return aiProvider.analyzeText(englishText);
    }

    /**
     * Compute overall sentiment using default provider
     */
    public double computeOverallSentimentScore(List<String> englishUserMessages) {
        return computeOverallSentimentScore(englishUserMessages, ProviderType.OPENAI);
    }

    /**
     * Compute overall sentiment using specified provider
     */
    public double computeOverallSentimentScore(List<String> englishUserMessages, ProviderType provider) {
        if (englishUserMessages == null || englishUserMessages.isEmpty()) return 0.0;
        var aiProvider = dualAiProviderService.getProvider(provider);
        if (aiProvider == null) {
            log.warn("Provider {} not available for sentiment computation", provider);
            return 0.0;
        }
        return aiProvider.computeOverallSentiment(englishUserMessages);
    }

    public List<String> generateSuggestionsEnglish(String englishContext) {
        return analyzeText(englishContext).getSuggestions();
    }

    public String summarizeEnglish(String englishContext) {
        return analyzeText(englishContext).getSummary();
    }

    /**
     * Check if a provider is available
     */
    public boolean isProviderAvailable(ProviderType provider) {
        return dualAiProviderService.isProviderAvailable(provider);
    }


    // -------------------------------------------------------------------------------------
    // REPLY-STYLE SUGGESTIONS (English + User Lang)
    // -------------------------------------------------------------------------------------
    public List<SuggestedResponse> buildReplySuggestions(String interactionId) {

        var all = messageService.fetchByInteraction(interactionId);
        if (all.isEmpty()) return List.of();

        var latest = all.get(all.size() - 1);
        String lang = latest.getOriginalLanguage() != null ? latest.getOriginalLanguage() : "en";
        boolean isEnglish = "en".equalsIgnoreCase(lang);

        // English list (filter out nulls)
        var englishList = all.stream()
                .map(MessageEntity::getEnglishText)
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (englishList.isEmpty()) return List.of();

        // AI bundle
        var bundle = analyzeConversation(englishList, latest.getEnglishText());

        // Build suggestions
        return bundle.getSuggestions().stream()
                .map(s -> {
                    SuggestedResponse sr = new SuggestedResponse();
                    sr.setEnglishReply(s);

                    if (!isEnglish) {
                        sr.setUserLanguageReply(
                                translationService.fromEnglish(s, lang)
                        );
                    }

                    return sr;
                })
                .toList();
    }


    // -------------------------------------------------------------------------------------
    // RAG-BASED SUGGESTIONS (Uses external RAG application)
    // -------------------------------------------------------------------------------------

    /**
     * Result holder for RAG-based suggestions including knowledge sources.
     */
    public record RagSuggestionsResult(
            List<SuggestedResponse> suggestions,
            List<KnowledgeSource> knowledgeSources,
            int documentsFound,
            boolean usedKnowledgeBase
    ) {}

    /**
     * Build reply suggestions using RAG (Retrieval Augmented Generation).
     * Fetches suggestions from the external RAG application which uses
     * uploaded documents as context.
     *
     * @param interactionId The conversation interaction ID
     * @return Result containing suggestions and knowledge sources used
     */
    public RagSuggestionsResult buildReplySuggestionsWithRag(String interactionId) {
        var all = messageService.fetchByInteraction(interactionId);
        return buildReplySuggestionsWithRag(all);
    }

    /**
     * Build reply suggestions using RAG with pre-fetched messages.
     * This avoids re-fetching messages which can cause transaction isolation issues.
     *
     * @param messages The list of messages (already fetched within same transaction)
     * @return Result containing suggestions and knowledge sources used
     */
    public RagSuggestionsResult buildReplySuggestionsWithRag(List<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return new RagSuggestionsResult(List.of(), List.of(), 0, false);
        }

        var latest = messages.get(messages.size() - 1);
        String lang = latest.getOriginalLanguage() != null ? latest.getOriginalLanguage() : "en";
        boolean isEnglish = "en".equalsIgnoreCase(lang);

        // Build conversation history in format "Role: message"
        List<String> conversationHistory = messages.stream()
                .map(msg -> {
                    String role = msg.getSender() == SenderType.customer ? "Customer" : "Agent";
                    String text = msg.getEnglishText() != null ? msg.getEnglishText() : msg.getOriginalText();
                    return role + ": " + text;
                })
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (conversationHistory.isEmpty()) {
            return new RagSuggestionsResult(List.of(), List.of(), 0, false);
        }

        // Get latest message text
        String latestMessage = latest.getEnglishText() != null
                ? latest.getEnglishText()
                : latest.getOriginalText();

        // Call RAG API
        log.info("Fetching suggestions from RAG for latest message: {}",
                latestMessage.length() > 50 ? latestMessage.substring(0, 50) + "..." : latestMessage);
        RagSuggestionResponse ragResponse = ragClient.getSuggestions(conversationHistory, latestMessage, 3);

        // Handle blocked response
        if (ragResponse.isBlocked()) {
            log.warn("RAG request blocked: {}", ragResponse.getBlockedReason());
            return new RagSuggestionsResult(List.of(), List.of(), 0, false);
        }

        // Convert suggestions to SuggestedResponse with translation
        List<SuggestedResponse> suggestions = new ArrayList<>();
        if (ragResponse.getSuggestions() != null) {
            for (String suggestion : ragResponse.getSuggestions()) {
                SuggestedResponse sr = new SuggestedResponse();
                sr.setEnglishReply(suggestion);

                // Only set userLanguageReply if not English (avoid duplication)
                if (!isEnglish) {
                    sr.setUserLanguageReply(translationService.fromEnglish(suggestion, lang));
                }
                // When English, userLanguageReply stays null
                suggestions.add(sr);
            }
        }

        // Convert source documents to KnowledgeSource
        List<KnowledgeSource> knowledgeSources = convertToKnowledgeSources(ragResponse.getSourceDocuments());

        log.info("RAG returned {} suggestions, {} knowledge sources",
                suggestions.size(), knowledgeSources.size());

        return new RagSuggestionsResult(
                suggestions,
                knowledgeSources,
                ragResponse.getDocumentsFound(),
                ragResponse.isUsedContext()
        );
    }

    /**
     * Check if RAG integration is enabled.
     */
    public boolean isRagEnabled() {
        return ragClient.isEnabled();
    }

    /**
     * Convert RAG source documents to KnowledgeSource DTOs.
     */
    private List<KnowledgeSource> convertToKnowledgeSources(List<RagSourceDocument> sourceDocuments) {
        if (sourceDocuments == null || sourceDocuments.isEmpty()) {
            return Collections.emptyList();
        }

        return sourceDocuments.stream()
                .map(doc -> KnowledgeSource.builder()
                        .fileName(doc.getFileName())
                        .relevanceScore(doc.getScore())
                        .documentType(doc.getSourceType())
                        .category(doc.getCategory())
                        .contentPreview(doc.getContentPreview())
                        .downloadUrl(ragClient.getDocumentDownloadUrl(doc.getFileName()))
                        .build())
                .toList();
    }

    // -------------------------------------------------------------------------------------
    // FULL CONVERSATION SUMMARY
    // -------------------------------------------------------------------------------------
    public String buildConversationSummary(String interactionId) {

        var all = messageService.fetchByInteraction(interactionId);
        if (all.isEmpty()) {
            return "No conversation available to summarize.";
        }

        var englishList = all.stream()
                .map(MessageEntity::getEnglishText)
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (englishList.isEmpty()) {
            return "No conversation content available to summarize.";
        }

        var latest = englishList.get(englishList.size() - 1);

        var bundle = analyzeConversation(englishList, latest);

        return bundle.getSummary() != null ? bundle.getSummary() : "";
    }

    // -------------------------------------------------------------------------------------
    // REGENERATE SUGGESTIONS
    // -------------------------------------------------------------------------------------
    public List<SuggestedResponse> regenerateSuggestions(String interactionId, String previousSuggestion) {
        return regenerateSuggestions(interactionId, previousSuggestion, ProviderType.OPENAI);
    }

    public List<SuggestedResponse> regenerateSuggestions(String interactionId, String previousSuggestion, ProviderType provider) {
        var all = messageService.fetchByInteraction(interactionId);
        if (all.isEmpty()) return List.of();

        var latest = all.get(all.size() - 1);
        String lang = latest.getOriginalLanguage() != null ? latest.getOriginalLanguage() : "en";
        boolean isEnglish = "en".equalsIgnoreCase(lang);

        // English list (filter out nulls)
        var englishList = all.stream()
                .map(MessageEntity::getEnglishText)
                .filter(text -> text != null && !text.isBlank())
                .toList();

        if (englishList.isEmpty()) return List.of();

        // Get the provider
        var aiProvider = dualAiProviderService.getProvider(provider);
        if (aiProvider == null) {
            log.warn("Provider {} not available for suggestion regeneration", provider);
            return List.of();
        }

        // Call AI to regenerate with context about previous suggestion
        var bundle = aiProvider.regenerateSuggestions(englishList, latest.getEnglishText(), previousSuggestion);

        // Build suggestions
        return bundle.getSuggestions().stream()
                .map(s -> {
                    SuggestedResponse sr = new SuggestedResponse();
                    sr.setEnglishReply(s);

                    if (!isEnglish) {
                        sr.setUserLanguageReply(
                                translationService.fromEnglish(s, lang)
                        );
                    }

                    return sr;
                })
                .toList();
    }
}
