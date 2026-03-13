package com.agentassist.controller;

import com.agentassist.dto.responseDTO.ChatMessage;
import com.agentassist.dto.responseDTO.FetchMessagesResponse;
import com.agentassist.service.conversation.MessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Messages", description = "Fetch conversation messages")
public class MessagesController {

    private final MessageService messageService;

    @GetMapping("/{interactionId}/messages")
    public ResponseEntity<FetchMessagesResponse> fetchMessages(@PathVariable String interactionId) {

        List<ChatMessage> chatMessages = messageService.fetchByInteraction(interactionId).stream()
                .sorted(Comparator.comparing(m -> m.getCreatedAt() == null ? LocalDateTime.MIN : m.getCreatedAt()))
                .map(m -> new ChatMessage(
                        m.getSender() != null ? m.getSender().name() : "USER",
                        m.getOriginalText()
                ))
                .toList();

        FetchMessagesResponse response = new FetchMessagesResponse(interactionId, chatMessages);
        return ResponseEntity.ok(response);
    }
}
