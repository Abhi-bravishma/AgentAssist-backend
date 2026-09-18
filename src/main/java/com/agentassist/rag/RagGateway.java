package com.agentassist.rag;

import com.agentassist.config.RagClientConfig;
import com.agentassist.dto.rag.RagDocumentListResponse;
import com.agentassist.dto.rag.RagDocumentUploadResponse;
import com.agentassist.dto.rag.RagSuggestionRequest;
import com.agentassist.dto.rag.RagSuggestionResponse;
import com.agentassist.service.rag.RagClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Single entry point for knowledge-base operations. {@code rag.mode} decides:
 *
 * <ul>
 *   <li><b>internal</b> (default) — the in-app lane copied from bravishma-rag:
 *       Qdrant directly, suggestions through this app's own provider. No
 *       external RAG service involved.</li>
 *   <li><b>remote</b> — the legacy HTTP client, kept as a rollback path.</li>
 * </ul>
 *
 * <p>Method shapes and error behaviour mirror what {@link RagClient} gave the
 * callers: failures return empty responses / error maps, never throw.</p>
 */
@Slf4j
@Service
public class RagGateway {

    private final String mode;
    /** Origin prefixed to document links handed out through the API; see app.public-url. */
    private final String publicUrl;
    private final RagClient ragClient;
    private final RagClientConfig ragClientConfig;
    private final InternalRagProperties properties;
    private final AgentAssistSuggestionService suggestionService;
    private final AgentAssistDocumentService documentService;
    private final FileStorageService fileStorageService;

    public RagGateway(@Value("${rag.mode:internal}") String mode,
                      @Value("${app.public-url:}") String publicUrl,
                      RagClient ragClient,
                      RagClientConfig ragClientConfig,
                      InternalRagProperties properties,
                      AgentAssistSuggestionService suggestionService,
                      AgentAssistDocumentService documentService,
                      FileStorageService fileStorageService) {
        this.mode = mode;
        this.publicUrl = publicUrl == null ? "" : publicUrl.trim().replaceAll("/+$", "");
        this.ragClient = ragClient;
        this.ragClientConfig = ragClientConfig;
        this.properties = properties;
        this.suggestionService = suggestionService;
        this.documentService = documentService;
        this.fileStorageService = fileStorageService;
        log.info("RAG mode: {} ({})", internal() ? "internal" : "remote",
                internal() ? "in-app lane, Qdrant " + properties.getQdrant().getHost()
                        : "legacy client, " + ragClientConfig.getBaseUrl());
    }

    private boolean internal() {
        return !"remote".equalsIgnoreCase(mode);
    }

    private Long companyId() {
        return ragClientConfig.getCompanyId();
    }

    public boolean isEnabled() {
        return internal() ? properties.isEnabled() : ragClient.isEnabled();
    }

    // ==================== Suggestions ====================

    public RagSuggestionResponse getSuggestions(List<String> conversationHistory,
                                                String latestMessage,
                                                int suggestionCount,
                                                String additionalContext,
                                                String projectName) {
        return getSuggestions(conversationHistory, latestMessage, suggestionCount, additionalContext, projectName, null);
    }

    /** As above; {@code tokenSink} streams the reply text when the internal lane generates it. */
    public RagSuggestionResponse getSuggestions(List<String> conversationHistory,
                                                String latestMessage,
                                                int suggestionCount,
                                                String additionalContext,
                                                String projectName,
                                                Consumer<String> tokenSink) {
        if (!internal()) {
            return ragClient.getSuggestions(conversationHistory, latestMessage,
                    suggestionCount, additionalContext, projectName);
        }
        if (!properties.isEnabled()) {
            log.debug("Internal RAG is disabled");
            return emptySuggestions();
        }
        RagSuggestionRequest request = RagSuggestionRequest.builder()
                .conversationHistory(conversationHistory)
                .latestMessage(latestMessage)
                .suggestionCount(suggestionCount)
                .companyId(companyId())
                .additionalContext(additionalContext)
                .projectName(projectName)
                .build();
        try {
            return suggestionService.generateSuggestions(request, tokenSink);
        } catch (Exception e) {
            // Mirror the old client: an unreachable knowledge base degrades to
            // "no suggestions", the caller's fallback logic handles the rest.
            log.error("Internal RAG suggestions failed: {}", e.getMessage(), e);
            return emptySuggestions();
        }
    }

    private RagSuggestionResponse emptySuggestions() {
        return RagSuggestionResponse.builder()
                .suggestions(Collections.emptyList())
                .sourceDocuments(Collections.emptyList())
                .documentsFound(0)
                .usedContext(false)
                .blocked(false)
                .build();
    }

    // ==================== Documents ====================

    public RagDocumentUploadResponse uploadDocuments(List<MultipartFile> files, String category, String projectName) {
        if (!internal()) {
            return ragClient.uploadDocuments(files, category, projectName);
        }
        try {
            return documentService.uploadDocuments(files, companyId(), category, projectName);
        } catch (Exception e) {
            log.error("Internal RAG upload failed: {}", e.getMessage(), e);
            return RagDocumentUploadResponse.builder()
                    .message("Upload failed: " + e.getMessage())
                    .totalFiles(files.size())
                    .processedFiles(0)
                    .failedFiles(files.size())
                    .build();
        }
    }

    public RagDocumentListResponse listDocuments(int page, int size) {
        return listDocuments(page, size, null);
    }

    /**
     * List documents, optionally narrowed to one project.
     *
     * <p>Failures propagate on purpose. This used to swallow every exception and
     * return an empty page, which the portal rendered as "No documents." — so a
     * Qdrant timeout looked exactly like an empty knowledge base and documents
     * appeared to vanish and come back on their own. The caller decides what to
     * show; it cannot decide if it is never told.</p>
     */
    public RagDocumentListResponse listDocuments(int page, int size, String projectName) {
        if (!internal()) {
            return ragClient.listDocuments(page, size);
        }
        return documentService.listDocuments(companyId(), page, size, projectName);
    }

    /**
     * Whether the knowledge base can actually be reached right now — a real
     * probe when running the internal lane, the configuration flag otherwise
     * (the remote client has nothing cheap to ping).
     */
    public boolean healthy() {
        if (!internal()) {
            return ragClient.isEnabled();
        }
        return documentService.isVectorStoreHealthy();
    }

    public Map<String, Object> deleteDocument(String fileName) {
        if (!internal()) {
            return ragClient.deleteDocument(fileName);
        }
        try {
            long deleted = documentService.deleteDocument(fileName, companyId());
            if (deleted == 0) {
                return Map.of("error", "Document not found", "fileName", fileName);
            }
            return Map.of(
                    "message", "Document deleted successfully",
                    "fileName", fileName,
                    "chunksDeleted", deleted);
        } catch (Exception e) {
            log.error("Internal RAG delete failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "delete failed");
        }
    }

    public Map<String, Object> setDocumentActive(String fileName, boolean active) {
        if (!internal()) {
            return ragClient.setDocumentActive(fileName, active);
        }
        try {
            long updated = documentService.setDocumentActive(fileName, companyId(), active);
            if (updated == 0) {
                return Map.of("error", "Document not found", "fileName", fileName);
            }
            return Map.of(
                    "message", active ? "Document resumed" : "Document paused",
                    "fileName", fileName,
                    "active", active,
                    "chunksUpdated", updated);
        } catch (Exception e) {
            log.error("Internal RAG active-toggle failed: {}", e.getMessage(), e);
            return Map.of("error", e.getMessage() != null ? e.getMessage() : "update failed");
        }
    }

    public Resource downloadDocument(String fileName, Long companyId) {
        if (!internal()) {
            return ragClient.downloadDocument(fileName, companyId);
        }
        Long effectiveCompanyId = companyId != null ? companyId : companyId();
        return fileStorageService.loadAsResource(fileName, effectiveCompanyId);
    }

    /**
     * Download URL for a document. Internal mode returns a path on THIS app
     * (relative — same-origin frontends resolve it; the old absolute URL
     * pointed at the external RAG host, which no longer exists in this mode).
     */
    public String getDocumentDownloadUrl(String fileName) {
        if (!internal()) {
            return ragClient.getDocumentDownloadUrl(fileName);
        }
        if (!isEnabled()) {
            return null;
        }
        try {
            // Absolute, because this link leaves the app: the widget hands it to a
            // document viewer on another origin and pastes it to customers. The
            // legacy remote lane always returned an absolute URL; the in-app lane
            // briefly returned a relative one and previews broke.
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            return publicUrl + "/api/v1/knowledge-base/documents/download/" + encoded
                    + "?companyId=" + companyId();
        } catch (Exception e) {
            log.error("Failed to build download URL: {}", e.getMessage());
            return null;
        }
    }
}
