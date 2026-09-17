package com.agentassist.service.results;

import com.agentassist.service.processing.PipelineListener;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * The pipeline listener that turns stage events into stored pieces and live
 * pushes. One instance per process; wraps an optional delegate so a combined
 * stream can observe the same events.
 *
 * <p>Never throws: a storage or subscriber problem must not fail the message
 * being processed. Whatever went wrong is logged and the pipeline carries on.</p>
 */
@Slf4j
public class ResultRecordingListener implements PipelineListener {

    private final String processId;
    private final ProcessResultStore store;
    private final ProcessResultHub hub;
    private final PipelineListener delegate;

    public ResultRecordingListener(String processId, ProcessResultStore store, ProcessResultHub hub,
                                   PipelineListener delegate) {
        this.processId = processId;
        this.store = store;
        this.hub = hub;
        this.delegate = delegate == null ? PipelineListener.NONE : delegate;
    }

    @Override
    public void on(String event, Object payload) {
        try {
            record(event, payload);
        } catch (Exception e) {
            log.warn("[Results] Failed to record '{}' for {}: {}", event, processId, e.getMessage());
        }
        try {
            delegate.on(event, payload);
        } catch (Exception e) {
            log.debug("[Results] Delegate listener failed on '{}': {}", event, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void record(String event, Object payload) {
        switch (event) {
            case "message-saved" -> store.attachMessage(processId, (Long) payload);

            case "sentiment" -> {
                Map<String, Object> p = (Map<String, Object>) payload;
                store.saveSentiment(processId,
                        asDouble(p.get("overallSentiment")),
                        asDouble(p.get("currentSentiment")),
                        (String) p.get("label"));
                hub.publish(processId, ProcessResultHub.PIECE_SENTIMENT, "sentiment", p);
                hub.complete(processId, ProcessResultHub.PIECE_SENTIMENT);
            }

            case "summary" -> {
                Map<String, Object> p = (Map<String, Object>) payload;
                String summary = (String) p.get("summary");
                store.saveSummary(processId, summary);
                hub.publish(processId, ProcessResultHub.PIECE_SUMMARY, "summary", p);
                hub.complete(processId, ProcessResultHub.PIECE_SUMMARY);
            }

            case "suggestion-token" -> hub.token(processId, String.valueOf(payload));

            case "summary-token" -> hub.publish(processId, ProcessResultHub.PIECE_SUMMARY, "token",
                    Map.of("text", String.valueOf(payload)));

            case "suggestions" -> {
                Map<String, Object> p = (Map<String, Object>) payload;
                store.saveSuggestions(processId,
                        p.get("suggestedResponses"),
                        p.get("knowledgeSources"),
                        asInt(p.get("documentsFound")),
                        Boolean.TRUE.equals(p.get("usedKnowledgeBase")));
                hub.publish(processId, ProcessResultHub.PIECE_SUGGESTED, "suggestions", p);
                hub.complete(processId, ProcessResultHub.PIECE_SUGGESTED);
            }

            default -> { /* stage progress etc. — delegate only */ }
        }
    }

    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
