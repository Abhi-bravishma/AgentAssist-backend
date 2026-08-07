package com.agentassist.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * VERBATIM COPY of bravishma-rag's {@code AgentAssistSearchFilter} (the frozen
 * agent-assist fork), brought in-app so this service no longer depends on the
 * external RAG application. Only the package changed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentAssistSearchFilter {

    /** Filter by specific file names. */
    private List<String> fileNames;

    /** Filter by document categories. */
    private List<String> categories;

    /** Filter documents uploaded on or after this date. */
    private LocalDate dateFrom;

    /** Filter documents uploaded on or before this date. */
    private LocalDate dateTo;

    /** Minimum similarity score (overrides default if provided). */
    private Double minScore;

    /** Maximum number of results (overrides default if provided). */
    private Integer topK;

    /** Filter by document source type (e.g., "pdf", "docx"). */
    private String sourceType;

    /**
     * Filter by use case. Always set to "agent_assist" by the suggestion
     * service, matching how every existing document in the collection is tagged.
     */
    private String useCase;

    /**
     * Project/Bank name (e.g., "ALLIANZ", "METRO", "SCB").
     * If null/empty, searches all projects.
     */
    private String projectName;

    /** True if any pre-filter is set. */
    public boolean hasPreFilters() {
        return (fileNames != null && !fileNames.isEmpty())
                || (categories != null && !categories.isEmpty())
                || dateFrom != null
                || dateTo != null
                || (sourceType != null && !sourceType.isEmpty())
                || (useCase != null && !useCase.isEmpty())
                || (projectName != null && !projectName.isEmpty());
    }

    /** True if any date filter is set. */
    public boolean hasDateFilter() {
        return dateFrom != null || dateTo != null;
    }
}
