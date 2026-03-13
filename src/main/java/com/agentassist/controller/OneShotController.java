package com.agentassist.controller;

import com.agentassist.ai.ProviderType;
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

    /**
     * Process a message with AI analysis.
     *
     * @param req Request containing interactionId, from, message, and optional provider
     *            provider options: "openai" (default), "ollama", "both" (comparison mode)
     * @return OneShotResponse with sentiment, summary, suggestions, and optionally comparison data
     */
    @PostMapping("/process")
    public ResponseEntity<OneShotResponse> process(@Valid @RequestBody OneShotRequest req) {
        ProviderType provider = req.getProvider() != null ? req.getProvider() : ProviderType.OPENAI;
        return ResponseEntity.ok(
                processor.processMessage(req.getInteractionId(), req.getFrom(), req.getMessage(), provider)
        );
    }
}
