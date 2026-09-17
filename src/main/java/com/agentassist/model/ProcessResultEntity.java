package com.agentassist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per processed message, filled in piece by piece as the pipeline
 * produces them: sentiment and summary the moment analysis returns, the
 * suggested reply when it is built, the full response at the end.
 *
 * <p>This is what lets the per-piece endpoints answer before processing has
 * finished, and what lets a request/response client (the agent desktop, Avaya)
 * poll for whichever piece is ready instead of waiting for all of them.
 *
 * <p>Same conventions as {@link MessageEntity}: Hibernate-managed, TEXT columns
 * for anything unbounded. The JSON columns hold the exact objects the
 * {@code /process} response carries, so a stored piece is byte-identical to the
 * live one.</p>
 */
@Entity
@Table(name = "process_results", indexes = {
        @Index(name = "idx_process_results_process_id", columnList = "processId", unique = true),
        @Index(name = "idx_process_results_interaction", columnList = "interactionId, createdAt")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessResultEntity {

    public enum Status { PROCESSING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Issued at submit time, before anything runs — the handle clients poll with. */
    @Column(nullable = false, length = 40)
    private String processId;

    @Column(nullable = false)
    private String interactionId;

    /** The saved message this result belongs to; null until the pipeline persists it. */
    private Long messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    // --- sentiment: available once analysis returns ---
    private Double overallSentiment;
    private Double currentSentiment;
    private String sentimentLabel;

    // --- summary: same moment as sentiment (one analysis call produces both) ---
    @Column(columnDefinition = "TEXT")
    private String summary;

    // --- suggested reply: later, after retrieval + generation ---
    @Column(columnDefinition = "TEXT")
    private String suggestionsJson;

    @Column(columnDefinition = "TEXT")
    private String knowledgeSourcesJson;

    private Integer documentsFound;
    private Boolean usedKnowledgeBase;

    // --- the complete /process response, once everything is done ---
    @Column(columnDefinition = "TEXT")
    private String responseJson;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
