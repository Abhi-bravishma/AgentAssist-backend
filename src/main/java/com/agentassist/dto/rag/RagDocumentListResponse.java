package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for listing RAG documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentListResponse {

    private List<RagDocumentInfo> documents;
    private int totalDocuments;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagDocumentInfo {
        private String fileName;
        private String category;
        private String uploadDate;
        private int chunkCount;
    }
}
