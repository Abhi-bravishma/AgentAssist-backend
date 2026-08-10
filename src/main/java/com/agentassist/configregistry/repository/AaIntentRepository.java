package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AaIntentRepository extends JpaRepository<AaIntent, Long> {

    Optional<AaIntent> findByCode(String code);

    List<AaIntent> findByActiveTrue();

    /** Deterministic order for classifier prompt assembly (seed/id order). */
    List<AaIntent> findByActiveTrueOrderByIdAsc();
}
