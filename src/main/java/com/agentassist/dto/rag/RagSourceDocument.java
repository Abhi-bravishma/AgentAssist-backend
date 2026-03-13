package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a source document from RAG response.
 * Contains metadata about the document retrieved from vector store.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSourceDocument {

    /**
     * Name of the source file.
     */
    private String fileName;

    /**
     * Similarity score (0.0 to 1.0) indicating relevance to the query.
     */
    private Double score;

    /**
     * Type of document (pdf, docx, txt, etc.).
     */
    private String sourceType;

    /**
     * Category the document belongs to.
     */
    private String category;

    /**
     * Chunk index within the document.
     */
    private Integer chunkIndex;

    /**
     * Preview/snippet of the content used.
     */
    private String contentPreview;
}
