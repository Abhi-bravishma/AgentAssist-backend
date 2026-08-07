package com.agentassist.rag;

import com.agentassist.ai.AiProviderFactory;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.rag.RagSourceDocument;
import com.agentassist.dto.rag.RagSuggestionRequest;
import com.agentassist.dto.rag.RagSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * COPY of bravishma-rag's {@code AgentAssistServiceImpl} (suggestion
 * generation), brought in-app: same search-query building, same
 * {@code minRelevanceScore} post-filter, same context assembly, same
 * suggestion parsing, and the SAME prompt — now served from the registry as
 * {@code rag.suggestions}, seeded verbatim from upstream's
 * SUGGESTION_PROMPT_TEMPLATE.
 *
 * <p>Deltas from upstream: the LLM call goes through this app's
 * {@link AiProviderFactory} (the portal's openai/ollama switch governs
 * suggestions too, instead of upstream's ChatClientFactory); the per-agent
 * prompt lookup and guardrail check are dropped because this app's calls
 * always arrived with {@code agentId=null} and no user context — upstream
 * resolved those paths to "default prompt, guardrails skipped" on every
 * request we ever made.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAssistSuggestionService {

    private static final int DEFAULT_SUGGESTION_COUNT = 1;
    private static final int MAX_CONTENT_PREVIEW_LENGTH = 200;

    private final AgentAssistVectorSearchService vectorSearchService;
    private final PromptService promptService;
    private final AiProviderFactory aiProviderFactory;
    private final InternalRagProperties properties;

    public RagSuggestionResponse generateSuggestions(RagSuggestionRequest request) {
        Long companyId = request.getCompanyId();
        log.info("Generating agent assist suggestions (internal) for companyId: {}", companyId);

        // Build search query from conversation context
        String searchQuery = buildSearchQuery(request);

        // Always filter by useCase=agent_assist to only search Agent Assist documents
        AgentAssistSearchFilter searchFilter = AgentAssistSearchFilter.builder()
                .useCase("agent_assist")
                .build();

        // Set project name filter if provided (e.g., "ALLIANZ", "METRO")
        if (request.getProjectName() != null && !request.getProjectName().isEmpty()) {
            searchFilter.setProjectName(request.getProjectName());
            log.info("Filtering by projectName: {}", request.getProjectName());
        }

        // Search for relevant documents
        List<Document> allDocs = vectorSearchService.search(
                searchQuery,
                companyId,
                searchFilter,
                properties.getTopK(),
                properties.getVectorScore());

        // Filter documents by minimum relevance score for agent assist
        double minRelevanceScore = properties.getMinRelevanceScore();
        List<Document> docs = allDocs.stream()
                .filter(doc -> {
                    Double score = getDoubleMetadata(doc.getMetadata(), "score");
                    boolean isRelevant = score != null && score >= minRelevanceScore;
                    if (!isRelevant && score != null) {
                        log.debug("Filtering out document with low relevance score: {} (threshold: {})",
                                score, minRelevanceScore);
                    }
                    return isRelevant;
                })
                .toList();

        log.info("Found {} documents, {} passed relevance threshold (>= {})",
                allDocs.size(), docs.size(), minRelevanceScore);

        boolean usedContext = !docs.isEmpty();

        // Extract source documents with metadata
        List<RagSourceDocument> sourceDocuments = extractSourceDocuments(docs);

        // Build context from documents
        String context = docs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));

        // Append additional context (e.g., Salesforce policy data) if provided
        String additionalContext = request.getAdditionalContext();
        if (additionalContext != null && !additionalContext.isBlank()) {
            log.info("Including additional context (e.g., policy data) in prompt");
            context = (usedContext ? context + "\n\n" : "") + additionalContext;
            usedContext = true;
        }

        // Build conversation history string
        String conversationHistory = String.join("\n", request.getConversationHistory());

        int suggestionCount = request.getSuggestionCount() != null
                ? request.getSuggestionCount()
                : DEFAULT_SUGGESTION_COUNT;

        // The exact upstream prompt, served from the registry
        String prompt = promptService.renderDefault(TemplateKeys.RAG_SUGGESTIONS, Map.of(
                "conversation_history", conversationHistory,
                "latest_message", request.getLatestMessage(),
                "context", usedContext ? context : "No relevant documents found.",
                "suggestion_count", String.valueOf(suggestionCount)));

        String llmResponse = aiProviderFactory.active().complete(prompt);
        if (llmResponse == null || llmResponse.isBlank()) {
            log.error("Failed to generate suggestions: LLM returned no content");
            return RagSuggestionResponse.builder()
                    .suggestions(Collections.emptyList())
                    .sourceDocuments(sourceDocuments)
                    .documentsFound(docs.size())
                    .usedContext(usedContext)
                    .blocked(false)
                    .build();
        }

        // Parse suggestions from LLM response
        List<String> suggestions = parseSuggestions(llmResponse, suggestionCount);

        return RagSuggestionResponse.builder()
                .suggestions(suggestions)
                .sourceDocuments(sourceDocuments)
                .documentsFound(docs.size())
                .usedContext(usedContext)
                .agentPrompt("Default")
                .conversationSummary(buildConversationSummary(request))
                .blocked(false)
                .build();
    }

    /**
     * Build a search query from the conversation context.
     * Combines latest message with recent conversation for better semantic search.
     */
    private String buildSearchQuery(RagSuggestionRequest request) {
        String latest = request.getLatestMessage();
        StringBuilder queryBuilder = new StringBuilder(latest);

        // Add last 2 messages from history for context, skipping any that
        // duplicate the latest message (history usually ends with it) —
        // embedding the question twice skews the vector and lowers scores
        List<String> history = request.getConversationHistory();
        int historySize = history.size();
        int startIndex = Math.max(0, historySize - 2);

        for (int i = startIndex; i < historySize; i++) {
            String msg = history.get(i);
            String content = msg.replaceFirst("^(Customer|Agent):\\s*", "");
            if (content.equalsIgnoreCase(latest)) {
                continue;
            }
            queryBuilder.append(" ").append(msg);
        }

        return queryBuilder.toString();
    }

    /**
     * Extract source document metadata from retrieved documents.
     */
    private List<RagSourceDocument> extractSourceDocuments(List<Document> docs) {
        List<RagSourceDocument> sourceDocuments = new ArrayList<>();

        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();

            String fileName = getStringMetadata(metadata, "filename");
            Double score = getDoubleMetadata(metadata, "score");
            String sourceType = getStringMetadata(metadata, "source");
            String category = getStringMetadata(metadata, "category");
            Integer chunkIndex = getIntegerMetadata(metadata, "chunkIndex");

            // Get content preview
            String content = doc.getContent();
            String contentPreview = content.length() > MAX_CONTENT_PREVIEW_LENGTH
                    ? content.substring(0, MAX_CONTENT_PREVIEW_LENGTH) + "..."
                    : content;

            sourceDocuments.add(RagSourceDocument.builder()
                    .fileName(fileName)
                    .score(score)
                    .sourceType(sourceType)
                    .category(category)
                    .chunkIndex(chunkIndex)
                    .contentPreview(contentPreview)
                    .build());
        }

        return sourceDocuments;
    }

    /**
     * Parse numbered suggestions from LLM response.
     */
    List<String> parseSuggestions(String response, int expectedCount) {
        List<String> suggestions = new ArrayList<>();

        if (response == null || response.isBlank()) {
            return suggestions;
        }

        // Split by numbered patterns (1., 2., 3., etc.)
        String[] lines = response.split("\\n");
        StringBuilder currentSuggestion = new StringBuilder();
        boolean inSuggestion = false;

        for (String line : lines) {
            line = line.trim();

            // Check if line starts with a number followed by . or )
            if (line.matches("^\\d+[.)].*")) {
                // Save previous suggestion if exists
                if (inSuggestion && currentSuggestion.length() > 0) {
                    suggestions.add(currentSuggestion.toString().trim());
                    currentSuggestion = new StringBuilder();
                }

                // Extract suggestion text (remove the number prefix)
                String suggestionText = line.replaceFirst("^\\d+[.)]\\s*", "");
                currentSuggestion.append(suggestionText);
                inSuggestion = true;
            } else if (inSuggestion && !line.isEmpty()) {
                // Continue previous suggestion
                currentSuggestion.append(" ").append(line);
            }
        }

        // Add last suggestion
        if (currentSuggestion.length() > 0) {
            suggestions.add(currentSuggestion.toString().trim());
        }

        // If parsing failed, return the whole response as a single suggestion
        if (suggestions.isEmpty() && !response.isBlank()) {
            suggestions.add(response.trim());
        }

        return suggestions;
    }

    /**
     * Build a brief summary of the conversation.
     */
    private String buildConversationSummary(RagSuggestionRequest request) {
        List<String> history = request.getConversationHistory();
        int messageCount = history.size();

        if (messageCount == 0) {
            return "New conversation";
        }

        return String.format("Conversation with %d messages. Latest: %s",
                messageCount,
                truncate(request.getLatestMessage(), 100));
    }

    private String getStringMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDoubleMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Integer getIntegerMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
