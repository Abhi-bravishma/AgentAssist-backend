package com.agentassist.controller;

import com.agentassist.dto.responseDTO.ConversationDto;
import com.agentassist.dto.responseDTO.ConversationSummaryResponse;
import com.agentassist.dto.responseDTO.MessageDto;
import com.agentassist.mapper.MessageMapper;
import com.agentassist.model.Conversation;
import com.agentassist.service.analysis.AnalysisService;
import com.agentassist.service.conversation.ConversationService;
import com.agentassist.service.conversation.MessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/v1/conversation")
@RequiredArgsConstructor
@Tag(name = "Conversation", description = "Fetch full conversation with messages")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final MessageMapper messageMapper;
    private final AnalysisService analysisService;

    // --------------------------------------------------------------------------------------
    // FETCH FULL CONVERSATION WITH SORTED MESSAGES
    // --------------------------------------------------------------------------------------
    @GetMapping("/{interactionId}")
    public ResponseEntity<ConversationDto> getConversation(@PathVariable String interactionId) {

        Conversation conv = conversationService.getOrCreate(interactionId);

        List<MessageDto> messages = messageService.fetchByInteraction(interactionId).stream()
                .map(messageMapper::toDto)
                .sorted(Comparator.comparing(
                        m -> m.getCreatedAt() == null ? Instant.EPOCH : m.getCreatedAt()
                ))
                .toList();

        ConversationDto dto = new ConversationDto();
        dto.setInteractionId(conv.getInteractionId());
        dto.setCreatedAt(conv.getCreatedAt());
        dto.setUpdatedAt(conv.getUpdatedAt());
        dto.setBaseLanguage(conv.getBaseLanguage());
        dto.setMessages(messages);

        return ResponseEntity.ok(dto);
    }

    // --------------------------------------------------------------------------------------
    // CONVERSATION SUMMARY
    // --------------------------------------------------------------------------------------
    @GetMapping("/{interactionId}/summary")
    public ResponseEntity<ConversationSummaryResponse> summary(@PathVariable String interactionId) {

        Conversation conv = conversationService.getOrCreate(interactionId);

        // 1. Get basic summary from DB (ONE METHOD ONLY)
        ConversationSummaryResponse resp = messageService.getDBSummary(interactionId);

        // 2. Add missing fields
        resp.setInteractionId(interactionId);
        resp.setBaseLanguage(conv.getBaseLanguage());

        // 3. Add AI summary
        String summary = analysisService.buildConversationSummary(interactionId);
        resp.setSummary(summary);

        return ResponseEntity.ok(resp);
    }

}
