package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AaSettingRepository extends JpaRepository<AaSetting, Long> {

    Optional<AaSetting> findBySettingKeyAndProjectIdIsNull(String settingKey);
}
