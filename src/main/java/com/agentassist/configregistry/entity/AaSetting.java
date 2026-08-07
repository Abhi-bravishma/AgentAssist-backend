package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Runtime-changeable setting. Part 1 uses a single global row:
 * {@code ai.active_provider} = openai | ollama. projectId exists for symmetry
 * with the other registry tables and is unused for now.
 */
@Entity
@Table(name = "aa_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaSetting {

    public static final String AI_ACTIVE_PROVIDER = "ai.active_provider";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100)
    private String settingKey;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PreUpdate
    @PrePersist
    protected void onWrite() {
        updatedAt = Instant.now();
    }
}
