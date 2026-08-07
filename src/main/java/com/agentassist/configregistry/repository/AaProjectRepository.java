package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AaProjectRepository extends JpaRepository<AaProject, Long> {

    Optional<AaProject> findByCode(String code);
}
