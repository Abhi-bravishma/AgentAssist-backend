package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Versioned prompt template. Resolution rule: highest PUBLISHED version for
 * (templateKey, projectId); if none, highest PUBLISHED version for
 * (templateKey, NULL projectId).
 */
@Entity
@Table(name = "aa_prompt_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaPromptTemplate {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    /** NULL = default template, non-NULL = project override. */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
