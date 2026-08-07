package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AaLanguageRepository extends JpaRepository<AaLanguage, Long> {

    Optional<AaLanguage> findByCode(String code);
}
