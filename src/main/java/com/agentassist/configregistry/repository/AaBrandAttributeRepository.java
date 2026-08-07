package com.agentassist.configregistry.repository;

import com.agentassist.configregistry.entity.AaBrandAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AaBrandAttributeRepository extends JpaRepository<AaBrandAttribute, Long> {

    Optional<AaBrandAttribute> findFirstByProjectIdAndAttrKey(Long projectId, String attrKey);

    Optional<AaBrandAttribute> findFirstByProjectIdIsNullAndAttrKey(String attrKey);
}
