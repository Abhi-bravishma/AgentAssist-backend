package com.agentassist.controller;

import com.agentassist.dto.requestDTO.ConversationRequest;
import com.agentassist.model.ProcessResultEntity;
import com.agentassist.model.ProcessResultEntity.Status;
import com.agentassist.service.results.ProcessResultHub;
import com.agentassist.service.results.ProcessResultService;
import com.agentassist.service.results.ProcessResultStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Message processing as separately deliverable pieces.
 *
 * <pre>
 * POST /process/async                                  → 202 {processId}
 * GET  /results/{processId}/sentiment
 * GET  /results/{processId}/sentiment/sse
 * GET  /results/{processId}/convo-summary
 * GET  /results/{processId}/convo-summary/sse
 * GET  /results/{processId}/suggested-response
 * GET  /results/{processId}/suggested-response/sse     (token by token)
 * GET  /results/{processId}/result                     (the full /process response)
 * GET  /{interactionId}/latest/…                       (same five, newest message)
 * </pre>
 *
 * <p>The plain GETs answer {@code 202 pending} until their piece is stored, so a
 * request/response client — the agent desktop, Avaya — can poll each one and
 * show whatever is ready. The SSE GETs push the piece the moment it lands, or
 * immediately if it already has. Same pipeline, same prompts, same values as
 * {@code /process}, which remains exactly as it was.</p>
 */
@RestController
@RequestMapping("/api/v1/agent-assistant")
@RequiredArgsConstructor
@Tag(name = "Agent Assistant (results)")
public class ProcessResultController {

    private static final long EMITTER_TIMEOUT_MS = 180_000L;

    private final ProcessResultService results;
    private final ProcessResultStore store;
    private final ProcessResultHub hub;

    // ==================== submit ====================

    @PostMapping("/process/async")
    @Operation(summary = "Submit a message for processing and return immediately",
               description = "Returns a processId; fetch pieces from /results/{processId}/… as they become available.")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody ConversationRequest request) {
        String processId = results.submitAsync(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processId", processId);
        body.put("interactionId", request.getInteractionId());
        body.put("status", Status.PROCESSING.name());
        return ResponseEntity.accepted().body(body);
    }

    // ==================== by processId ====================

    @GetMapping("/results/{processId}/sentiment")
    public ResponseEntity<?> sentiment(@PathVariable String processId) {
        return byProcess(processId, this::sentimentOf);
    }

    @GetMapping(value = "/results/{processId}/sentiment/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sentimentStream(@PathVariable String processId) {
        return streamByProcess(processId, ProcessResultHub.PIECE_SENTIMENT, "sentiment", this::sentimentPayload);
    }

    @GetMapping("/results/{processId}/convo-summary")
    public ResponseEntity<?> summary(@PathVariable String processId) {
        return byProcess(processId, this::summaryOf);
    }

    @GetMapping(value = "/results/{processId}/convo-summary/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> summaryStream(@PathVariable String processId) {
        return streamByProcess(processId, ProcessResultHub.PIECE_SUMMARY, "summary", this::summaryPayload);
    }

    @GetMapping("/results/{processId}/suggested-response")
    public ResponseEntity<?> suggested(@PathVariable String processId) {
        return byProcess(processId, this::suggestedOf);
    }

    @GetMapping(value = "/results/{processId}/suggested-response/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> suggestedStream(@PathVariable String processId) {
        return streamByProcess(processId, ProcessResultHub.PIECE_SUGGESTED, "suggestions", this::suggestedPayload);
    }

    @GetMapping("/results/{processId}/result")
    public ResponseEntity<?> result(@PathVariable String processId) {
        return byProcess(processId, this::resultOf);
    }

    // ==================== newest for an interaction ====================

    @GetMapping("/{interactionId}/latest/sentiment")
    public ResponseEntity<?> latestSentiment(@PathVariable String interactionId) {
        return byLatest(interactionId, this::sentimentOf);
    }

    @GetMapping(value = "/{interactionId}/latest/sentiment/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> latestSentimentStream(@PathVariable String interactionId) {
        return streamOf(latestOrThrow(interactionId), ProcessResultHub.PIECE_SENTIMENT, "sentiment", this::sentimentPayload);
    }

    @GetMapping("/{interactionId}/latest/convo-summary")
    public ResponseEntity<?> latestSummary(@PathVariable String interactionId) {
        return byLatest(interactionId, this::summaryOf);
    }

    @GetMapping(value = "/{interactionId}/latest/convo-summary/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> latestSummaryStream(@PathVariable String interactionId) {
        return streamOf(latestOrThrow(interactionId), ProcessResultHub.PIECE_SUMMARY, "summary", this::summaryPayload);
    }

    @GetMapping("/{interactionId}/latest/suggested-response")
    public ResponseEntity<?> latestSuggested(@PathVariable String interactionId) {
        return byLatest(interactionId, this::suggestedOf);
    }

    @GetMapping(value = "/{interactionId}/latest/suggested-response/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> latestSuggestedStream(@PathVariable String interactionId) {
        return streamOf(latestOrThrow(interactionId), ProcessResultHub.PIECE_SUGGESTED, "suggestions", this::suggestedPayload);
    }

    @GetMapping("/{interactionId}/latest/result")
    public ResponseEntity<?> latestResult(@PathVariable String interactionId) {
        return byLatest(interactionId, this::resultOf);
    }

    // ==================== piece shaping ====================

    /** Null while the piece is not yet available. */
    private Map<String, Object> sentimentPayload(ProcessResultEntity r) {
        if (r.getOverallSentiment() == null && r.getCurrentSentiment() == null) {
            return null;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("overallSentiment", r.getOverallSentiment());
        p.put("currentSentiment", r.getCurrentSentiment());
        p.put("label", r.getSentimentLabel());
        return p;
    }

    private Map<String, Object> summaryPayload(ProcessResultEntity r) {
        if (r.getSummary() == null) {
            return null;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("summary", r.getSummary());
        return p;
    }

    private Map<String, Object> suggestedPayload(ProcessResultEntity r) {
        if (r.getSuggestionsJson() == null) {
            return null;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("suggestedResponses", store.json(r.getSuggestionsJson()));
        p.put("knowledgeSources", store.json(r.getKnowledgeSourcesJson()));
        p.put("documentsFound", r.getDocumentsFound());
        p.put("usedKnowledgeBase", r.getUsedKnowledgeBase());
        return p;
    }

    private Map<String, Object> resultPayload(ProcessResultEntity r) {
        if (r.getResponseJson() == null) {
            return null;
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("response", store.json(r.getResponseJson()));
        return p;
    }

    private ResponseEntity<?> sentimentOf(ProcessResultEntity r) { return pieceOrPending(r, sentimentPayload(r)); }
    private ResponseEntity<?> summaryOf(ProcessResultEntity r)   { return pieceOrPending(r, summaryPayload(r)); }
    private ResponseEntity<?> suggestedOf(ProcessResultEntity r) { return pieceOrPending(r, suggestedPayload(r)); }
    private ResponseEntity<?> resultOf(ProcessResultEntity r)    { return pieceOrPending(r, resultPayload(r)); }

    // ==================== plumbing ====================

    private ResponseEntity<?> byProcess(String processId, Function<ProcessResultEntity, ResponseEntity<?>> f) {
        Optional<ProcessResultEntity> r = store.find(processId);
        return r.isPresent() ? f.apply(r.get()) : notFound("Unknown processId " + processId);
    }

    private ResponseEntity<?> byLatest(String interactionId, Function<ProcessResultEntity, ResponseEntity<?>> f) {
        Optional<ProcessResultEntity> r = store.latest(interactionId);
        return r.isPresent() ? f.apply(r.get()) : notFound("No processed message for interaction " + interactionId);
    }

    private ResponseEntity<SseEmitter> streamByProcess(String processId, String piece, String event,
                                                       Function<ProcessResultEntity, Map<String, Object>> payload) {
        ProcessResultEntity r = store.find(processId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown processId " + processId));
        return streamOf(r, piece, event, payload);
    }

    private ProcessResultEntity latestOrThrow(String interactionId) {
        return store.latest(interactionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No processed message for interaction " + interactionId));
    }

    /**
     * 200 with the piece, 202 while it is still being produced, 500 if the run
     * failed. The envelope always carries processId and status so a poller can
     * tell the three apart without inspecting the body shape.
     */
    private ResponseEntity<?> pieceOrPending(ProcessResultEntity r, Map<String, Object> piece) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processId", r.getProcessId());
        body.put("interactionId", r.getInteractionId());
        body.put("status", r.getStatus().name());
        if (piece != null) {
            body.putAll(piece);
            return ResponseEntity.ok(body);
        }
        if (r.getStatus() == Status.FAILED) {
            body.put("error", r.getError());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
        body.put("status", "pending");
        return ResponseEntity.accepted().body(body);
    }

    /**
     * Declared as {@code ResponseEntity<SseEmitter>} on purpose: Spring routes a
     * ResponseEntity to the streaming handler only when its declared generic is
     * an emitter type. With a wildcard it serialises the emitter as a JSON body
     * and the client sees no events at all.
     *
     * <p>Already there: send it and close. Still coming: subscribe, then re-check
     * the store so a piece that landed between the two cannot leave the client
     * waiting for an event that already fired.
     */
    private ResponseEntity<SseEmitter> streamOf(ProcessResultEntity r, String piece, String event,
                                                Function<ProcessResultEntity, Map<String, Object>> payload) {
        SseEmitter emitter;
        Map<String, Object> ready = payload.apply(r);

        if (ready != null) {
            emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
            ProcessResultHub.send(emitter, event, ready);
            ProcessResultHub.send(emitter, "done", Map.of("piece", piece));
            emitter.complete();
        } else if (r.getStatus() == Status.FAILED) {
            emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
            ProcessResultHub.send(emitter, "error", Map.of("error", String.valueOf(r.getError())));
            emitter.complete();
        } else {
            emitter = hub.subscribe(r.getProcessId(), piece);
            Optional<Map<String, Object>> landed = store.find(r.getProcessId()).map(payload);
            if (landed.isPresent()) {
                ProcessResultHub.send(emitter, event, landed.get());
                ProcessResultHub.send(emitter, "done", Map.of("piece", piece));
                emitter.complete();
            }
        }

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    private ResponseEntity<?> notFound(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
