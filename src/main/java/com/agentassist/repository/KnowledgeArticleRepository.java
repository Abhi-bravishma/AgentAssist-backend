package com.agentassist.repository;

import com.agentassist.model.KnowledgeArticleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticleEntity, Long> {

    List<KnowledgeArticleEntity> findByNameContainingIgnoreCaseOrContentContainingIgnoreCase(
            String name, String content);

    List<KnowledgeArticleEntity> findByType(String type);
}
