package com.agentassist.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Entity to store compliance check results for agent conversations.
 * Tracks compliance with: Greeting, Empathy, Clarity, Product T&C, Valediction.
 */
@Entity
@Table(name = "compliance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interaction_id", nullable = false)
    private String interactionId;

    @Column(name = "greeting")
    private Boolean greeting;

    @Column(name = "empathy")
    private Boolean empathy;

    @Column(name = "clarity")
    private Boolean clarity;

    @Column(name = "product_tnc")
    private Boolean productTnC;

    @Column(name = "valediction")
    private Boolean valediction;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (checkedAt == null) {
            checkedAt = Instant.now();
        }
    }
}
