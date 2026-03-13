package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Search filter parameters for RAG queries.
 * Mirrors the SearchFilter in the RAG application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSearchFilter {

    /**
     * Filter by specific file names.
     */
    private List<String> fileNames;

    /**
     * Filter by document categories.
     */
    private List<String> categories;

    /**
     * Filter documents uploaded on or after this date.
     */
    private LocalDate dateFrom;

    /**
     * Filter documents uploaded on or before this date.
     */
    private LocalDate dateTo;

    /**
     * Minimum similarity score threshold (0.0 to 1.0).
     */
    private Double minScore;

    /**
     * Maximum number of results to return.
     */
    private Integer topK;

    /**
     * Filter by document source type (e.g., "pdf", "docx").
     */
    private String sourceType;
}
