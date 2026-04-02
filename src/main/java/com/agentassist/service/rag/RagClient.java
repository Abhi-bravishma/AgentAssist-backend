package com.agentassist.service.rag;

import com.agentassist.config.RagClientConfig;
import com.agentassist.dto.rag.RagDocumentListResponse;
import com.agentassist.dto.rag.RagDocumentUploadResponse;
import com.agentassist.dto.rag.RagSuggestionRequest;
import com.agentassist.dto.rag.RagSuggestionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client service for communicating with the RAG (Retrieval Augmented Generation) application.
 * Handles API calls to fetch AI-powered suggestions based on conversation context and documents.
 */
@Slf4j
@Service
public class RagClient {

    private static final String SUGGESTIONS_ENDPOINT = "/api/v1/agent-assist/suggestions";
    private static final String DOCUMENTS_ENDPOINT = "/api/v1/agent-assist/documents";

    private final WebClient ragWebClient;
    private final RagClientConfig ragClientConfig;

    public RagClient(@Qualifier("ragWebClient") WebClient ragWebClient, RagClientConfig ragClientConfig) {
        this.ragWebClient = ragWebClient;
        this.ragClientConfig = ragClientConfig;
    }

    /**
     * Check if RAG integration is enabled.
     *
     * @return true if RAG is enabled
     */
    public boolean isEnabled() {
        return ragClientConfig.isEnabled();
    }

    /**
     * Fetch suggestions from the RAG application.
     *
     * @param conversationHistory List of conversation messages
     * @param latestMessage       The latest customer message
     * @param suggestionCount     Number of suggestions to generate
     * @return RAG suggestion response with suggestions and source documents
     */
    public RagSuggestionResponse getSuggestions(List<String> conversationHistory,
                                                 String latestMessage,
                                                 int suggestionCount) {
        return getSuggestions(conversationHistory, latestMessage, suggestionCount, null);
    }

    /**
     * Fetch suggestions from the RAG application with additional context.
     *
     * @param conversationHistory List of conversation messages
     * @param latestMessage       The latest customer message
     * @param suggestionCount     Number of suggestions to generate
     * @param additionalContext   Additional context (e.g., policy data from Salesforce)
     * @return RAG suggestion response with suggestions and source documents
     */
    public RagSuggestionResponse getSuggestions(List<String> conversationHistory,
                                                 String latestMessage,
                                                 int suggestionCount,
                                                 String additionalContext) {
        return getSuggestions(conversationHistory, latestMessage, suggestionCount, additionalContext, null);
    }

    /**
     * Fetch suggestions from the RAG application with additional context and project filtering.
     *
     * @param conversationHistory List of conversation messages
     * @param latestMessage       The latest customer message
     * @param suggestionCount     Number of suggestions to generate
     * @param additionalContext   Additional context (e.g., policy data from Salesforce)
     * @param projectName         Project/Bank name for filtering (e.g., "ALLIANZ", "METRO"). Null = search all.
     * @return RAG suggestion response with suggestions and source documents
     */
    public RagSuggestionResponse getSuggestions(List<String> conversationHistory,
                                                 String latestMessage,
                                                 int suggestionCount,
                                                 String additionalContext,
                                                 String projectName) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return createEmptyResponse();
        }

        RagSuggestionRequest request = RagSuggestionRequest.builder()
                .conversationHistory(conversationHistory)
                .latestMessage(latestMessage)
                .suggestionCount(suggestionCount)
                .companyId(ragClientConfig.getCompanyId())
                .additionalContext(additionalContext)
                .projectName(projectName)
                .build();

        log.info("RAG request - projectName: {}", projectName != null ? projectName : "ALL");

        return callRagApi(request);
    }

    /**
     * Fetch suggestions with custom filters.
     *
     * @param request The full RAG suggestion request
     * @return RAG suggestion response
     */
    public RagSuggestionResponse getSuggestions(RagSuggestionRequest request) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return createEmptyResponse();
        }

        return callRagApi(request);
    }

    /**
     * Make the actual API call to RAG application.
     */
    private RagSuggestionResponse callRagApi(RagSuggestionRequest request) {
        log.info("Calling RAG API at {} with {} conversation messages",
                ragClientConfig.getBaseUrl() + SUGGESTIONS_ENDPOINT,
                request.getConversationHistory() != null ? request.getConversationHistory().size() : 0);

        try {
            RagSuggestionResponse response = ragWebClient.post()
                    .uri(SUGGESTIONS_ENDPOINT)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RagSuggestionResponse.class)
                    .block();

            if (response != null) {
                log.info("RAG API returned {} suggestions, {} documents used",
                        response.getSuggestions() != null ? response.getSuggestions().size() : 0,
                        response.getDocumentsFound());
                return response;
            }

            log.warn("RAG API returned null response");
            return createEmptyResponse();

        } catch (WebClientResponseException e) {
            log.error("RAG API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return createEmptyResponse();
        } catch (Exception e) {
            log.error("Failed to call RAG API: {}", e.getMessage(), e);
            return createEmptyResponse();
        }
    }

    /**
     * Create an empty response for fallback scenarios.
     */
    private RagSuggestionResponse createEmptyResponse() {
        return RagSuggestionResponse.builder()
                .suggestions(Collections.emptyList())
                .sourceDocuments(Collections.emptyList())
                .documentsFound(0)
                .usedContext(false)
                .blocked(false)
                .build();
    }

    // ==================== Document Operations ====================

    /**
     * Upload documents to the RAG knowledge base for Agent Assist.
     *
     * @param files    List of files to upload
     * @param category Optional category for document classification
     * @return Upload response with status
     */
    public RagDocumentUploadResponse uploadDocuments(List<MultipartFile> files, String category) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return RagDocumentUploadResponse.builder()
                    .message("RAG integration is disabled")
                    .totalFiles(0)
                    .processedFiles(0)
                    .failedFiles(0)
                    .build();
        }

        log.info("Uploading {} documents to RAG for companyId: {}", files.size(), ragClientConfig.getCompanyId());

        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();

            // Add files
            for (MultipartFile file : files) {
                builder.part("files", new ByteArrayResource(file.getBytes()) {
                    @Override
                    public String getFilename() {
                        return file.getOriginalFilename();
                    }
                }).contentType(MediaType.APPLICATION_OCTET_STREAM);
            }

            String uri = DOCUMENTS_ENDPOINT + "/upload?companyId=" + ragClientConfig.getCompanyId();
            if (category != null && !category.isEmpty()) {
                uri += "&category=" + category;
            }

            RagDocumentUploadResponse response = ragWebClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(RagDocumentUploadResponse.class)
                    .block();

            if (response != null) {
                log.info("RAG upload completed: {} processed, {} failed",
                        response.getProcessedFiles(), response.getFailedFiles());
                return response;
            }

            return RagDocumentUploadResponse.builder()
                    .message("No response from RAG API")
                    .totalFiles(files.size())
                    .processedFiles(0)
                    .failedFiles(files.size())
                    .build();

        } catch (WebClientResponseException e) {
            log.error("RAG upload error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return RagDocumentUploadResponse.builder()
                    .message("Upload failed: " + e.getMessage())
                    .totalFiles(files.size())
                    .processedFiles(0)
                    .failedFiles(files.size())
                    .build();
        } catch (Exception e) {
            log.error("Failed to upload documents to RAG: {}", e.getMessage(), e);
            return RagDocumentUploadResponse.builder()
                    .message("Upload failed: " + e.getMessage())
                    .totalFiles(files.size())
                    .processedFiles(0)
                    .failedFiles(files.size())
                    .build();
        }
    }

    /**
     * List all Agent Assist documents from RAG.
     *
     * @param page Page number (0-based)
     * @param size Page size
     * @return List of documents
     */
    public RagDocumentListResponse listDocuments(int page, int size) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return RagDocumentListResponse.builder()
                    .documents(Collections.emptyList())
                    .totalDocuments(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .build();
        }

        log.info("Listing RAG documents for companyId: {}, page: {}, size: {}",
                ragClientConfig.getCompanyId(), page, size);

        try {
            String uri = DOCUMENTS_ENDPOINT + "?companyId=" + ragClientConfig.getCompanyId()
                    + "&page=" + page + "&size=" + size;

            RagDocumentListResponse response = ragWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(RagDocumentListResponse.class)
                    .block();

            if (response != null) {
                log.info("RAG returned {} documents", response.getTotalDocuments());
                return response;
            }

            return RagDocumentListResponse.builder()
                    .documents(Collections.emptyList())
                    .totalDocuments(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .build();

        } catch (WebClientResponseException e) {
            log.error("RAG list error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return RagDocumentListResponse.builder()
                    .documents(Collections.emptyList())
                    .totalDocuments(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .build();
        } catch (Exception e) {
            log.error("Failed to list RAG documents: {}", e.getMessage(), e);
            return RagDocumentListResponse.builder()
                    .documents(Collections.emptyList())
                    .totalDocuments(0)
                    .page(page)
                    .size(size)
                    .totalPages(0)
                    .build();
        }
    }

    /**
     * Delete a document from the RAG knowledge base.
     *
     * @param fileName File name to delete
     * @return Delete response
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteDocument(String fileName) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return Map.of("error", "RAG integration is disabled");
        }

        log.info("Deleting RAG document: {} for companyId: {}", fileName, ragClientConfig.getCompanyId());

        try {
            String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            String uri = DOCUMENTS_ENDPOINT + "/" + encodedFileName + "?companyId=" + ragClientConfig.getCompanyId();

            Map<String, Object> response = ragWebClient.delete()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null) {
                log.info("RAG delete response: {}", response);
                return response;
            }

            return Map.of("error", "No response from RAG API");

        } catch (WebClientResponseException e) {
            log.error("RAG delete error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return Map.of("error", e.getMessage(), "status", e.getStatusCode().value());
        } catch (Exception e) {
            log.error("Failed to delete RAG document: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Download a document from the RAG knowledge base.
     *
     * @param fileName File name to download
     * @param companyId Optional company ID (uses default if null)
     * @return Resource for the file, or null if not found
     */
    public Resource downloadDocument(String fileName, Long companyId) {
        if (!ragClientConfig.isEnabled()) {
            log.debug("RAG integration is disabled");
            return null;
        }

        Long effectiveCompanyId = companyId != null ? companyId : ragClientConfig.getCompanyId();
        log.info("Downloading RAG document: {} for companyId: {}", fileName, effectiveCompanyId);

        try {
            String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            String uri = DOCUMENTS_ENDPOINT + "/download/" + encodedFileName + "?companyId=" + effectiveCompanyId;

            return ragWebClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .bodyToMono(Resource.class)
                    .block();

        } catch (WebClientResponseException e) {
            log.error("RAG download error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Failed to download RAG document: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Download a document using default company ID.
     */
    public Resource downloadDocument(String fileName) {
        return downloadDocument(fileName, null);
    }

    /**
     * Get the download URL for a document (direct RAG URL).
     * Returns the full RAG URL which is publicly accessible for downloads.
     *
     * @param fileName File name
     * @return Full URL to download the document directly from RAG
     */
    public String getDocumentDownloadUrl(String fileName) {
        if (!ragClientConfig.isEnabled()) {
            return null;
        }
        try {
            String encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            // Return direct RAG URL (download endpoint is public)
            return ragClientConfig.getBaseUrl() + DOCUMENTS_ENDPOINT + "/download/" + encodedFileName
                    + "?companyId=" + ragClientConfig.getCompanyId();
        } catch (Exception e) {
            log.error("Failed to build download URL: {}", e.getMessage());
            return null;
        }
    }
}
