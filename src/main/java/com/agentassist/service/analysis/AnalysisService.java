package com.agentassist.service.analysis;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.dto.rag.RagSourceDocument;
import com.agentassist.dto.rag.RagSuggestionRequest;
import com.agentassist.dto.rag.RagSuggestionResponse;
import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.agentassist.dto.responseDTO.FollowUpCheckResponse;
import com.agentassist.dto.responseDTO.KnowledgeSource;
import com.agentassist.dto.responseDTO.SuggestedResponse;
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

    private final AiProviderFactory aiProviderFactory;
    private final MessageService messageService;
    private final TranslationService translationService;
    private final RagClient ragClient;

    // -------------------------------------------------------------------------------------
    // CORE AI METHODS
    // -------------------------------------------------------------------------------------
    public AiAnalysisBundle analyzeConversation(List<String> englishConversation, String latestMessage) {
        return aiProviderFactory.active().analyzeConversation(englishConversation, latestMessage);
    }

    /**
     * Analyze conversation with additional policy context from Salesforce.
     */
    public AiAnalysisBundle analyzeConversationWithContext(List<String> englishConversation,
                                                            String latestMessage,
                                                            String policyContext) {
        if (policyContext == null || policyContext.isBlank()) {
            return analyzeConversation(englishConversation, latestMessage);
        }
        return aiProviderFactory.active().analyzeConversationWithContext(englishConversation, latestMessage, policyContext);
    }

    /**
     * Analyze conversation with checklist context (fee waiver, home loan closure).
     * Uses the checklist guide + customer-specific data to generate targeted suggestions.
     *
     * @param englishConversation List of conversation messages in English
     * @param latestMessage       The latest user message
     * @param checklistContext    Combined checklist guide + customer data context
     * @param operationType       Type of operation (FEE_WAIVER, HOME_LOAN_CLOSURE)
     * @return Analysis bundle with sentiment scores, summary, and checklist-aware suggestions
     */
    public AiAnalysisBundle analyzeConversationWithChecklist(List<String> englishConversation,
                                                              String latestMessage,
                                                              String checklistContext,
                                                              String operationType) {
        if (checklistContext == null || checklistContext.isBlank()) {
            return analyzeConversation(englishConversation, latestMessage);
        }
        log.info("Analyzing conversation with checklist context for operation: {}", operationType);
        return aiProviderFactory.active().analyzeConversationWithChecklist(englishConversation, latestMessage, checklistContext, operationType);
    }

    public AiAnalysisResult analyzeText(String englishText) {
        return aiProviderFactory.active().analyzeText(englishText);
    }

    public double computeOverallSentimentScore(List<String> englishUserMessages) {
        if (englishUserMessages == null || englishUserMessages.isEmpty()) return 0.0;
        return aiProviderFactory.active().computeOverallSentiment(englishUserMessages);
    }

    public List<String> generateSuggestionsEnglish(String englishContext) {
        return analyzeText(englishContext).getSuggestions();
    }

    public String summarizeEnglish(String englishContext) {
        return analyzeText(englishContext).getSummary();
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
        return buildReplySuggestionsWithRag(messages, null);
    }

    /**
     * Build reply suggestions using RAG with pre-fetched messages and policy context.
     *
     * @param messages      The list of messages (already fetched within same transaction)
     * @param policyContext Customer policy data context from Salesforce (optional)
     * @return Result containing suggestions and knowledge sources used
     */
    public RagSuggestionsResult buildReplySuggestionsWithRag(List<MessageEntity> messages, String policyContext) {
        return buildReplySuggestionsWithRag(messages, policyContext, null);
    }

    /**
     * Build reply suggestions using RAG with pre-fetched messages, policy context, and project filtering.
     *
     * @param messages      The list of messages (already fetched within same transaction)
     * @param policyContext Customer policy data context from Salesforce (optional)
     * @param projectName   Project/Bank name for filtering documents (optional, null = search all)
     * @return Result containing suggestions and knowledge sources used
     */
    public RagSuggestionsResult buildReplySuggestionsWithRag(List<MessageEntity> messages, String policyContext, String projectName) {
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
        String originalMessage = latest.getOriginalText();

        // RAG builds its search query from latestMessage plus this list. For a non-English
        // customer we add their ORIGINAL wording, so retrieval no longer depends solely on
        // an LLM translation that varies between runs - the knowledge base documents are
        // bilingual, so the original text matches them directly.
        // When the original and the English are the same (English customers) we send exactly
        // what was sent before, so English retrieval is unchanged.
        boolean bilingualQuery = originalMessage != null
                && !originalMessage.isBlank()
                && !originalMessage.equalsIgnoreCase(latestMessage);
        List<String> searchContext = bilingualQuery
                ? List.of(originalMessage)
                : List.of(latestMessage);

        // Still no policy context - that would confuse RAG when answering FAQ questions
        log.info("Fetching suggestions from RAG for latest message: {}, bilingualQuery: {}, projectName: {}",
                latestMessage.length() > 50 ? latestMessage.substring(0, 50) + "..." : latestMessage,
                bilingualQuery, projectName != null ? projectName : "ALL");
        RagSuggestionResponse ragResponse = ragClient.getSuggestions(searchContext, latestMessage, 1, null, projectName);

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
        return regenerateSuggestions(interactionId, previousSuggestion, null, null);
    }

    /**
     * Regenerate suggestions with optional checklist context.
     * When checklist context is provided, the regeneration will use actual customer data.
     *
     * @param interactionId    The conversation interaction ID
     * @param previousSuggestion The previous suggestion to reword
     * @param checklistContext Customer data context (card numbers, amounts, eligibility)
     * @param customerName     Customer name for personalization
     * @return List of suggested responses with same data, different wording
     */
    public List<SuggestedResponse> regenerateSuggestions(String interactionId, String previousSuggestion,
                                                          String checklistContext, String customerName) {

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

        // Call AI to regenerate - use context-aware method if checklist provided
        AiAnalysisBundle bundle;
        if (checklistContext != null && !checklistContext.isBlank()) {
            log.info("Regenerating suggestions with checklist context for customer: {}", customerName);
            bundle = aiProviderFactory.active().regenerateSuggestionsWithContext(
                    englishList, latest.getEnglishText(), previousSuggestion, checklistContext, customerName);
        } else {
            bundle = aiProviderFactory.active().regenerateSuggestions(englishList, latest.getEnglishText(), previousSuggestion);
        }

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
    // FOLLOW-UP REQUIREMENT CHECK
    // -------------------------------------------------------------------------------------

    /**
     * Analyze if follow-up is required for a conversation.
     * Called at the end of an interaction.
     *
     * @param interactionId The interaction ID to analyze
     * @return Follow-up analysis result
     */
    public FollowUpCheckResponse analyzeFollowUpRequirement(String interactionId) {
        return analyzeFollowUpRequirement(interactionId, null);
    }

    /**
     * Analyze if follow-up is required for a conversation with customer name.
     *
     * @param interactionId The interaction ID to analyze
     * @param customerName  Optional customer name for personalized analysis
     * @return Follow-up analysis result
     */
    public FollowUpCheckResponse analyzeFollowUpRequirement(String interactionId, String customerName) {
        log.info("Analyzing follow-up requirement for interaction: {}", interactionId);

        var messages = messageService.fetchByInteraction(interactionId);
        if (messages.isEmpty()) {
            log.warn("No messages found for interaction: {}", interactionId);
            return FollowUpCheckResponse.builder()
                    .followUpRequired(false)
                    .followUp("")
                    .conversationSummary("")
                    .build();
        }

        // Build transcript in "Role: message" format
        List<String> transcript = messages.stream()
                .map(msg -> {
                    String role = msg.getSender() == SenderType.customer ? "Customer" : "Agent";
                    String text = msg.getEnglishText() != null ? msg.getEnglishText() : msg.getOriginalText();
                    return role + ": " + text;
                })
                .filter(text -> text != null && !text.isBlank())
                .toList();

        return aiProviderFactory.active().analyzeFollowUpRequirement(transcript, customerName);
    }

    /**
     * Analyze if follow-up is required using a provided transcript.
     * Use this when transcript is provided directly instead of fetching from DB.
     *
     * @param transcript   List of messages in "Role: message" format
     * @param customerName Optional customer name
     * @return Follow-up analysis result
     */
    public FollowUpCheckResponse analyzeFollowUpRequirementFromTranscript(List<String> transcript, String customerName) {
        if (transcript == null || transcript.isEmpty()) {
            return FollowUpCheckResponse.builder()
                    .followUpRequired(false)
                    .followUp("")
                    .conversationSummary("")
                    .build();
        }

        return aiProviderFactory.active().analyzeFollowUpRequirement(transcript, customerName);
    }
}
