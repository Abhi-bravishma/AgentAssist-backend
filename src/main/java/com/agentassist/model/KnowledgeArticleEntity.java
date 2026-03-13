package com.agentassist.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "knowledge_article")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeArticleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String type;

    @Column(columnDefinition = "TEXT")
    private String content;
}
