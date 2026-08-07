package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "aa_intent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false, length = 50)
    private String code;

    @Column(name = "display_name")
    private String displayName;

    /** Reserved for Part 2 — classifier prompt generation. */
    @Column(name = "description")
    private String description;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = Boolean.TRUE;

    /** "Not available through this service" message; supports {project}. */
    @Column(name = "filtered_message_template")
    private String filteredMessageTemplate;

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
