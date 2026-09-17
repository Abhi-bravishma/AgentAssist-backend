package com.agentassist.repository;

import com.agentassist.model.ProcessResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessResultRepository extends JpaRepository<ProcessResultEntity, Long> {

    Optional<ProcessResultEntity> findByProcessId(String processId);

    /** The newest result for an interaction — what "latest" resolves to. */
    Optional<ProcessResultEntity> findTopByInteractionIdOrderByCreatedAtDesc(String interactionId);
}
