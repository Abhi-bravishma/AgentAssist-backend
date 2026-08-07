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

        /** Project the document belongs to, e.g. HOSPITALITY, METRO. Null when untagged. */
        private String projectName;

        /**
         * False when the document is paused and excluded from retrieval.
         * <p>
         * Boxed deliberately: a RAG build without this field must deserialise to null,
         * not to a primitive default of false, which would render every document as
         * paused in the portal. Null and true both mean active.
         */
        private Boolean active;
    }
}
