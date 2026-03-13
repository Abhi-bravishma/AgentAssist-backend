package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a knowledge source document used for generating suggestions.
 * Shows which documents from the knowledge base were used as context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource {

    /**
     * Name of the source file.
     */
    private String fileName;

    /**
     * Relevance score (0.0 to 1.0).
     */
    private Double relevanceScore;

    /**
     * Type of document (pdf, docx, txt, etc.).
     */
    private String documentType;

    /**
     * Category the document belongs to.
     */
    private String category;

    /**
     * Preview/snippet of the relevant content.
     */
    private String contentPreview;

    /**
     * URL to download the original document.
     */
    private String downloadUrl;
}
