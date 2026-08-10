package com.agentassist.repository;

import com.agentassist.model.Compliance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplianceRepository extends JpaRepository<Compliance, Long> {

    /**
     * Find the latest compliance check for an interaction.
     */
    Optional<Compliance> findTopByInteractionIdOrderByCheckedAtDesc(String interactionId);
}
