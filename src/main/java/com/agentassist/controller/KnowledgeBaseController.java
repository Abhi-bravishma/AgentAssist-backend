package com.agentassist.controller;

import com.agentassist.dto.rag.RagDocumentListResponse;
import com.agentassist.dto.rag.RagDocumentUploadResponse;
import com.agentassist.rag.RagGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing Agent Assist knowledge base documents.
 * Provides endpoints for uploading, listing, and deleting documents
 * that are used for generating AI-powered suggestions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge-base")
@RequiredArgsConstructor
@Tag(name = "Knowledge Base", description = "Manage documents for AI-powered agent suggestions")
public class KnowledgeBaseController {

    private final RagGateway ragClient;

    /**
     * Upload documents to the knowledge base.
     * Documents are processed and stored in the RAG system for use in generating suggestions.
     */
    @Operation(
            summary = "Upload documents",
            description = "Upload documents to be used for generating AI-powered suggestions. " +
                    "Supported formats: PDF, DOCX, DOC, TXT, MD, XLSX, XLS, CSV, PPTX, PPT. " +
                    "Optionally specify projectName (e.g., 'METRO', 'ALLIANZ', 'SCB') for project-based filtering."
    )
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocuments(
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String projectName) {

        log.info("POST /api/v1/knowledge-base/documents/upload - Uploading {} files, category: {}, projectName: {}",
                files != null ? files.size() : 0, category, projectName);

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No files provided"));
        }

        if (!ragClient.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Knowledge base integration is not enabled"));
        }

        try {
            RagDocumentUploadResponse response = ragClient.uploadDocuments(files, category, projectName);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload documents: " + e.getMessage()));
        }
    }

    /**
     * List all documents in the knowledge base.
     */
    @Operation(
            summary = "List documents",
            description = "Get a paginated list of all documents in the knowledge base"
    )
    @GetMapping("/documents")
    public ResponseEntity<?> listDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/knowledge-base/documents - page: {}, size: {}", page, size);

        if (!ragClient.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Knowledge base integration is not enabled"));
        }

        try {
            RagDocumentListResponse response = ragClient.listDocuments(page, size);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to list documents: " + e.getMessage()));
        }
    }

    /**
     * Delete a document from the knowledge base.
     */
    @Operation(
            summary = "Delete document",
            description = "Delete a document from the knowledge base by filename"
    )
    @DeleteMapping("/documents/{fileName}")
    public ResponseEntity<?> deleteDocument(@PathVariable String fileName) {
        log.info("DELETE /api/v1/knowledge-base/documents/{}", fileName);

        if (!ragClient.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Knowledge base integration is not enabled"));
        }

        if (fileName == null || fileName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File name is required"));
        }

        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> response = ragClient.deleteDocument(decodedFileName);

            if (response.containsKey("error")) {
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to delete document: " + e.getMessage()));
        }
    }

    /**
     * Pause or resume a document without deleting it.
     */
    @Operation(
            summary = "Pause or resume a document",
            description = "Set whether a document is used for suggestions. A paused document keeps "
                    + "its embeddings and is excluded from retrieval, so resuming is instant."
    )
    @PatchMapping("/documents/{fileName}/active")
    public ResponseEntity<?> setDocumentActive(
            @PathVariable String fileName,
            @RequestBody Map<String, Object> body) {

        Object activeValue = body != null ? body.get("active") : null;
        if (!(activeValue instanceof Boolean active)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "`active` (boolean) is required in the request body"));
        }

        log.info("PATCH /api/v1/knowledge-base/documents/{}/active - active={}", fileName, active);

        if (!ragClient.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Knowledge base integration is not enabled"));
        }

        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> response = ragClient.setDocumentActive(decodedFileName, active);

            if (response.containsKey("error")) {
                return ResponseEntity.badRequest().body(response);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update document status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to update document status: " + e.getMessage()));
        }
    }

    /**
     * Check if knowledge base integration is enabled.
     */
    @Operation(
            summary = "Check status",
            description = "Check if the knowledge base integration is enabled and available"
    )
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", ragClient.isEnabled(),
                "message", ragClient.isEnabled()
                        ? "Knowledge base integration is active"
                        : "Knowledge base integration is disabled"
        ));
    }

    /**
     * Download a document from the knowledge base.
     * Returns the original uploaded file for viewing.
     */
    @Operation(
            summary = "Download document",
            description = "Download the original document file for viewing or saving"
    )
    @GetMapping("/documents/download/{fileName}")
    public ResponseEntity<?> downloadDocument(
            @PathVariable String fileName,
            @RequestParam(required = false) Long companyId) {
        log.info("GET /api/v1/knowledge-base/documents/download/{}, companyId={}", fileName, companyId);

        if (!ragClient.isEnabled()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Knowledge base integration is not enabled"));
        }

        if (fileName == null || fileName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "File name is required"));
        }

        try {
            String decodedFileName = java.net.URLDecoder.decode(fileName, java.nio.charset.StandardCharsets.UTF_8);
            Resource resource = ragClient.downloadDocument(decodedFileName, companyId);

            if (resource == null) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "File not found", "fileName", decodedFileName));
            }

            // Determine content type from file extension
            String contentType = getContentType(decodedFileName);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + decodedFileName + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Failed to download document", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to download document: " + e.getMessage()));
        }
    }

    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }
}
