package com.agentassist.controller;

import com.agentassist.dto.requestDTO.ConversationRequest;
import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.service.results.ProcessResultHub;
import com.agentassist.service.results.ProcessResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Server-sent-events twin of {@code POST /api/v1/agent-assistant/process}:
 * everything on one connection, each piece the moment it exists.
 *
 * <p>Same pipeline, same prompts, same final payload — only the delivery
 * differs. {@code /process} is untouched. For per-piece streams (a browser
 * {@code EventSource} can only GET) see {@code ProcessResultController}.
 *
 * <p>Events, in the order they normally arrive:
 * <ul>
 *   <li>{@code started}     — {@code {processId}}: the same id the per-piece endpoints accept</li>
 *   <li>{@code stage}       — {@code {stage, ms}} per pipeline stage</li>
 *   <li>{@code sentiment}   — {@code {overallSentiment, currentSentiment, label}}</li>
 *   <li>{@code summary-token} — {@code {text}}: the summary as the model writes it</li>
 *   <li>{@code summary}     — {@code {summary}}: the final text</li>
 *   <li>{@code token}       — {@code {text}}: the suggested reply as the model writes it</li>
 *   <li>{@code suggestions} — {@code {suggestedResponses, knowledgeSources, documentsFound, usedKnowledgeBase}}</li>
 *   <li>{@code done}        — the complete {@link ConversationResponse}</li>
 *   <li>{@code error}       — {@code {error}} if processing failed</li>
 * </ul>
 *
 * <p>{@code X-Accel-Buffering: no} tells any nginx in front to pass events
 * through as they are written instead of holding the response until it ends.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent-assistant")
@RequiredArgsConstructor
@Tag(name = "Agent Assistant (streaming)")
public class ConversationStreamController {

    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ProcessResultService results;

    @Resource(name = "streamExecutor")
    private Executor streamExecutor;

    @PostMapping(value = "/process/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process a message, streaming every piece as it becomes available",
               description = "Identical processing to /process. Emits started, stage, sentiment, "
                       + "summary, token, suggestions and done events.")
    public ResponseEntity<SseEmitter> processStream(@Valid @RequestBody ConversationRequest request) {
        String processId = results.startTracked(request.getInteractionId());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        streamExecutor.execute(() -> {
            ProcessResultHub.send(emitter, "started", Map.of("processId", processId));
            try {
                ConversationResponse response = results.run(processId, request, (event, payload) -> {
                    // Reply tokens go out as "token", summary tokens as "summary-token";
                    // both carry {text}. Everything else passes through under its own name.
                    String name = "suggestion-token".equals(event) ? "token" : event;
                    Object body = event.endsWith("-token") ? Map.of("text", String.valueOf(payload)) : payload;
                    ProcessResultHub.send(emitter, name, body);
                });
                ProcessResultHub.send(emitter, "done", response);
                emitter.complete();
            } catch (Exception e) {
                log.error("[Stream] Processing failed: {}", e.getMessage(), e);
                ProcessResultHub.send(emitter, "error", Map.of("error", String.valueOf(e.getMessage())));
                emitter.completeWithError(e);
            }
        });

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .body(emitter);
    }
}
