package com.agentassist.service.results;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live subscriptions to the pieces of an in-flight process.
 *
 * <p>A client opens {@code .../convo-summary/sse} or
 * {@code .../suggested-response/sse} for a processId; the pipeline publishes
 * each piece here as it lands, and every subscriber for that piece receives it
 * and is completed. Tokens of the suggested reply are relayed the same way.
 *
 * <p>In-memory by design: this app runs as a single instance, and a
 * subscription is only meaningful while the process it watches is running.
 * A client that arrives after the piece is stored never reaches this class —
 * the controller serves it straight from the store.</p>
 */
@Slf4j
@Component
public class ProcessResultHub {

    public static final String PIECE_SENTIMENT = "sentiment";
    public static final String PIECE_SUMMARY = "convo-summary";
    public static final String PIECE_SUGGESTED = "suggested-response";

    /** Generous: must outlive the slowest pipeline run. */
    private static final long EMITTER_TIMEOUT_MS = 180_000L;

    private final Map<String, Map<String, List<SseEmitter>>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String processId, String piece) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> list = subscribers
                .computeIfAbsent(processId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(piece, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable detach = () -> list.remove(emitter);
        emitter.onCompletion(detach);
        emitter.onTimeout(detach);
        emitter.onError(e -> detach.run());
        return emitter;
    }

    /** Send one named event to every subscriber of a piece. */
    public void publish(String processId, String piece, String event, Object payload) {
        for (SseEmitter emitter : subscribersOf(processId, piece)) {
            send(emitter, event, payload);
        }
    }

    /** A chunk of the suggested reply as the model writes it. */
    public void token(String processId, String text) {
        publish(processId, PIECE_SUGGESTED, "token", Map.of("text", text));
    }

    /** The piece is final: tell subscribers and close their streams. */
    public void complete(String processId, String piece) {
        for (SseEmitter emitter : subscribersOf(processId, piece)) {
            send(emitter, "done", Map.of("piece", piece));
            emitter.complete();
        }
        Map<String, List<SseEmitter>> pieces = subscribers.get(processId);
        if (pieces != null) {
            pieces.remove(piece);
            if (pieces.isEmpty()) {
                subscribers.remove(processId);
            }
        }
    }

    /** Processing died: every open stream for this process learns why, then closes. */
    public void fail(String processId, String message) {
        Map<String, List<SseEmitter>> pieces = subscribers.remove(processId);
        if (pieces == null) {
            return;
        }
        pieces.values().stream().flatMap(List::stream).forEach(emitter -> {
            send(emitter, "error", Map.of("error", message == null ? "processing failed" : message));
            emitter.complete();
        });
    }

    private List<SseEmitter> subscribersOf(String processId, String piece) {
        Map<String, List<SseEmitter>> pieces = subscribers.get(processId);
        if (pieces == null) {
            return List.of();
        }
        List<SseEmitter> list = pieces.get(piece);
        return list == null ? List.of() : list;
    }

    /**
     * A client that has gone away must not take the pipeline with it: sending
     * to a dead emitter throws, which is expected and logged at DEBUG.
     */
    public static void send(SseEmitter emitter, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("[Results] Subscriber gone, dropping '{}': {}", event, e.getMessage());
        }
    }
}
