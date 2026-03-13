package com.agentassist.dto.responseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FetchMessagesResponse {
    private String interactionId;
    private List<ChatMessage> messages;
}
