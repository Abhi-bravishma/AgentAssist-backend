package com.agentassist.mapper;

import com.agentassist.dto.responseDTO.MessageDto;
import com.agentassist.model.MessageEntity;
import org.mapstruct.Mapper;

import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    default MessageDto toDto(MessageEntity e) {
        if (e == null) return null;

        MessageDto dto = new MessageDto();
        dto.setId(e.getId());
        dto.setInteractionId(e.getInteractionId());
        dto.setSender(e.getSender() != null ? e.getSender().name() : null);
        dto.setOriginalText(e.getOriginalText());
        dto.setOriginalLanguage(e.getOriginalLanguage());
        dto.setEnglishText(e.getEnglishText());
        dto.setSentiment(e.getSentiment());
        dto.setSentimentScore(e.getSentimentScore());

        // Convert LocalDateTime → Instant
        if (e.getCreatedAt() != null) {
            dto.setCreatedAt(e.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant());
        }

        return dto;
    }
}
