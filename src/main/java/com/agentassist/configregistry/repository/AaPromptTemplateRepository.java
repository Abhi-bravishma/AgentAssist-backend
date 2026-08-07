package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AaPromptTemplateRepository extends JpaRepository<AaPromptTemplate, Long> {

    /** Highest version with the given status for a project override. */
    Optional<AaPromptTemplate> findFirstByTemplateKeyAndProjectIdAndStatusOrderByVersionDesc(
            String templateKey, Long projectId, String status);

    /** Highest version with the given status for the default (NULL project). */
    Optional<AaPromptTemplate> findFirstByTemplateKeyAndProjectIdIsNullAndStatusOrderByVersionDesc(
            String templateKey, String status);
}
