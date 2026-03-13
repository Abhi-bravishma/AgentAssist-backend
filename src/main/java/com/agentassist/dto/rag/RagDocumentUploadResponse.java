package com.agentassist.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for RAG document upload operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentUploadResponse {

    private String message;
    private int totalFiles;
    private int processedFiles;
    private int failedFiles;
    private List<String> fileNames;
    private List<String> failedFileNames;
}
