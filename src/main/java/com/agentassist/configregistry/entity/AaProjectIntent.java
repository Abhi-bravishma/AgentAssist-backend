package com.agentassist.configregistry.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Whitelist row. Semantics (must match the old switch exactly): a project with
 * NO rows allows ALL intents; a project with rows allows only those with
 * {@code enabled = true}. NONE is always allowed and never stored.
 */
@Entity
@Table(name = "aa_project_intent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AaProjectIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "intent_id", nullable = false)
    private Long intentId;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = Boolean.TRUE;
}
