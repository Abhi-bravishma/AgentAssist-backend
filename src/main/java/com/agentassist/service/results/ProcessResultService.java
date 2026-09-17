package com.agentassist.service.results;

import com.agentassist.dto.requestDTO.ConversationRequest;
import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.service.processing.ConversationProcessingService;
import com.agentassist.service.processing.PipelineListener;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Runs the message pipeline under a processId, recording every piece as it is
 * produced. Entry point for both delivery modes:
 *
 * <ul>
 *   <li>{@link #submitAsync}: returns the processId immediately and runs in the
 *       background — for request/response clients that poll the piece endpoints
 *       or open the per-piece SSE streams.</li>
 *   <li>{@link #run}: runs inline on the caller's thread with an extra
 *       listener — for the combined {@code /process/stream} endpoint.</li>
 * </ul>
 *
 * <p>{@code /process} itself does not come through here and is unchanged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessResultService {

    /** Same default the /process controller applies when no project is given. */
    private static final String DEFAULT_PROJECT = "HOSPITALITY";

    private final ConversationProcessingService processor;
    private final ProcessResultStore store;
    private final ProcessResultHub hub;

    @Resource(name = "streamExecutor")
    private Executor streamExecutor;

    public String submitAsync(ConversationRequest request) {
        String processId = newProcessId();
        store.start(processId, request.getInteractionId());
        streamExecutor.execute(() -> {
            try {
                run(processId, request, PipelineListener.NONE);
            } catch (RuntimeException e) {
                // Already recorded and published by run(); nothing else to do on a pool thread.
                log.debug("[Results] async process {} ended in failure: {}", processId, e.getMessage());
            }
        });
        return processId;
    }

    public ConversationResponse run(String processId, ConversationRequest request, PipelineListener delegate) {
        String project = request.getProjectName() == null || request.getProjectName().isEmpty()
                ? DEFAULT_PROJECT
                : request.getProjectName();

        PipelineListener listener = new ResultRecordingListener(processId, store, hub, delegate);
        try {
            ConversationResponse response = processor.processMessage(
                    request.getInteractionId(),
                    request.getFrom(),
                    request.getMessage(),
                    request.getMobileNumber(),
                    project,
                    listener);
            store.complete(processId, response);
            return response;
        } catch (RuntimeException e) {
            log.error("[Results] process {} failed: {}", processId, e.getMessage(), e);
            store.fail(processId, e.getMessage());
            hub.fail(processId, e.getMessage());
            throw e;
        }
    }

    /** Pre-creates the row so the id is valid the instant the client receives it. */
    public String startTracked(String interactionId) {
        String processId = newProcessId();
        store.start(processId, interactionId);
        return processId;
    }

    private static String newProcessId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
