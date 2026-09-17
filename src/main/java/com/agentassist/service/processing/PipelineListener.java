package com.agentassist.service.processing;

/**
 * Optional hook for watching a message being processed, stage by stage.
 *
 * <p>Exists so the streaming endpoint can push partial results as they become
 * ready — sentiment and summary land seconds before the knowledge-base answer,
 * and an agent staring at a blank panel would rather see them than wait.
 *
 * <p>The non-streaming {@code /process} path passes {@link #NONE}, so it
 * behaves exactly as it always has: same calls, same order, same response, and
 * no branch that only exists for streaming.</p>
 */
@FunctionalInterface
public interface PipelineListener {

    /** Fired on the processing thread; implementations must not block or throw. */
    void on(String event, Object payload);

    /** No-op used by every non-streaming caller. */
    PipelineListener NONE = (event, payload) -> {
    };
}
