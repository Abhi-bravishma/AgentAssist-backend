package com.agentassist.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String interactionId;

    @Enumerated(EnumType.STRING)
    private SenderType sender;

    @Column(nullable = false)
    private String originalText;

    private String originalLanguage;

    @Column(nullable = false)
    private String englishText;

    private String sentiment;

    private Double sentimentScore;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
