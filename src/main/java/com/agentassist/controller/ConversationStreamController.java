package com.agentassist.controller;

import com.agentassist.dto.requestDTO.ConversationRequest;
import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.service.processing.ConversationProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Server-sent-events twin of {@code POST /api/v1/agent-assistant/process}.
 *
 * <p>Same pipeline, same calls, same final payload — the difference is when the
 * client hears about it. Sentiment and summary are ready seconds before the
 * knowledge-base answer, so this pushes them out as they land instead of
 * holding everything until the slowest stage finishes.
 *
 * <p>The existing {@code /process} endpoint is deliberately untouched: callers
 * that cannot consume SSE keep working exactly as before, and this is purely
 * additive.
 *
 * <p>Event sequence:
 * <ul>
 *   <li>{@code stage}       — one per pipeline stage: {@code {stage, ms}}</li>
 *   <li>{@code analysis}    — sentiment scores and summary, as soon as analysed</li>
 *   <li>{@code suggestions} — reply suggestions once built</li>
 *   <li>{@code done}        — the complete {@link ConversationResponse}, identical
 *                             to what {@code /process} returns</li>
 *   <li>{@code error}       — message text if processing failed</li>
 * </ul>
 *
 * <p>Note for deployment: any proxy in front of this must not buffer the
 * response, or the events arrive in one lump at the end and the whole point is
 * lost. The container's nginx already sets {@code proxy_buffering off} for
 * {@code /api/}; a host nginx or CDN in front needs the same.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent-assistant")
@RequiredArgsConstructor
@Tag(name = "Agent Assistant (streaming)")
public class ConversationStreamController {

    /** Generous: the emitter must outlive the slowest possible pipeline run. */
    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ConversationProcessingService processor;

    @Resource(name = "streamExecutor")
    private Executor streamExecutor;

    @PostMapping(value = "/process/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Process a message, streaming partial results",
               description = "Identical processing to /process; emits stage, analysis, "
                       + "suggestions and done events as they become available.")
    public SseEmitter processStream(@Valid @RequestBody ConversationRequest request) {
        if (request.getProjectName() == null || request.getProjectName().isEmpty()) {
            request.setProjectName("HOSPITALITY");
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);

        streamExecutor.execute(() -> {
            try {
                ConversationResponse response = processor.processMessage(
                        request.getInteractionId(),
                        request.getFrom(),
                        request.getMessage(),
                        request.getMobileNumber(),
                        request.getProjectName(),
                        (event, payload) -> send(emitter, event, payload));

                send(emitter, "done", response);
                emitter.complete();
            } catch (Exception e) {
                log.error("[Stream] Processing failed: {}", e.getMessage(), e);
                send(emitter, "error", Map.of("error", String.valueOf(e.getMessage())));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * A dead client must not take the pipeline down with it. If the browser has
     * gone, sending throws — that is expected, not an error worth a stack trace,
     * and processing carries on so the conversation is still persisted.
     */
    private void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("[Stream] Client gone, dropping '{}' event: {}", event, e.getMessage());
        }
    }
}
