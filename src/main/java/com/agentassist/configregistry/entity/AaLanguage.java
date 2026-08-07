package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Language known to the app. {@code displayName} is the phrase handed to the
 * translation prompt (e.g. "Traditional Chinese (繁體中文), using Traditional
 * characters only"). Nothing consults this table until phase 1c.
 */
@Entity
@Table(name = "aa_language")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaLanguage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 20)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "script", length = 20)
    private String script;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
