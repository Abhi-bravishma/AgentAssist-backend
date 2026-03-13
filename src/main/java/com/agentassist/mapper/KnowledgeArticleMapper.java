package com.agentassist.mapper;

import com.agentassist.dto.responseDTO.KnowledgeArticleDTO;
import com.agentassist.model.KnowledgeArticleEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KnowledgeArticleMapper {

    KnowledgeArticleDTO toDto(KnowledgeArticleEntity entity);
}
