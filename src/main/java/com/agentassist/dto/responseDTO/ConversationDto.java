package com.agentassist.dto.responseDTO;



import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class ConversationDto {
    private String interactionId;
    private Instant createdAt;
    private Instant updatedAt;
    private String baseLanguage;
    private List<MessageDto> messages;
}
