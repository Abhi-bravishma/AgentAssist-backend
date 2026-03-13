package com.agentassist.controller;

import com.agentassist.dto.requestDTO.OneShotRequest;
import com.agentassist.dto.responseDTO.OneShotResponse;
import com.agentassist.service.processing.ConversationProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent-assistant")
@RequiredArgsConstructor
public class OneShotController {

    private final ConversationProcessingService processor;

    @PostMapping("/process")
    public ResponseEntity<OneShotResponse> process(@Valid @RequestBody OneShotRequest req) {
        return ResponseEntity.ok(
                processor.processMessage(req.getInteractionId(), req.getFrom(), req.getMessage())
        );
    }
}
