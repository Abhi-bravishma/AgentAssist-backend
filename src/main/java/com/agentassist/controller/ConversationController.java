package com.agentassist.controller;

import com.agentassist.dto.requestDTO.ConversationRequest;
import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.service.processing.ConversationProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for processing conversation messages.
 * Main endpoint for the Agent Assist functionality.
 */
@RestController
@RequestMapping("/api/v1/agent-assistant")
@RequiredArgsConstructor
@Tag(name = "Conversation", description = "Process conversation messages and get AI-powered suggestions")
public class ConversationController {

    private final ConversationProcessingService processor;

    @PostMapping("/process")
    @Operation(summary = "Process a conversation message",
               description = "Analyzes the message, detects sentiment, generates summary and reply suggestions using RAG")
    public ResponseEntity<ConversationResponse> process(@Valid @RequestBody ConversationRequest request) {
        if(request.getProjectName().isEmpty() || request.getProjectName() == null){
            System.out.println("Setting default project name to SCB");
            request.setProjectName("SCB");
        }
        return ResponseEntity.ok(
                processor.processMessage(
                        request.getInteractionId(),
                        request.getFrom(),
                        request.getMessage(),
                        request.getMobileNumber(),
                        request.getProjectName()
                )
        );
    }
}
