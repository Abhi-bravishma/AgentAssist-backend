package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaProjectIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AaProjectIntentRepository extends JpaRepository<AaProjectIntent, Long> {

    List<AaProjectIntent> findByProjectId(Long projectId);
}
