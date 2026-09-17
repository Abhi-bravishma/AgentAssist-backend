package com.agentassist.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor for the pieces of message processing that can run concurrently.
 *
 * <p>Sized small on purpose: this exists to overlap two calls within one
 * request, not to fan work out. Every task here is an outbound OpenAI call, so
 * the pool is really a cap on how much extra concurrent load one request can
 * put on the provider's rate limit.
 *
 * <p>{@code CallerRunsPolicy} is the safety net that makes this change
 * un-scary: if the pool and its queue are full, the submitting thread runs the
 * task itself — which is exactly the sequential behaviour this app had before.
 * Saturation degrades to "as slow as yesterday", never to a rejected request.
 */
@Configuration
@EnableScheduling
public class PipelineAsyncConfig {

    /**
     * Runs a whole streaming request. Kept separate from {@code ragExecutor} on
     * purpose: a streaming request occupies one of these for its full duration
     * and then submits retrieval to the other, so sharing a pool would let
     * streams starve the very work they are waiting on.
     */
    @Bean("streamExecutor")
    public Executor streamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcPropagating());
        executor.initialize();
        return executor;
    }

    @Bean("ragExecutor")
    public Executor ragExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(32);
        executor.setThreadNamePrefix("rag-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setTaskDecorator(mdcPropagating());
        executor.initialize();
        return executor;
    }

    /**
     * MDC is thread-local: without this the request id vanishes the moment work
     * crosses onto a pool thread, and half of every request would log
     * untraceable lines. Copying the map over is the whole fix.
     */
    private static TaskDecorator mdcPropagating() {
        return task -> {
            Map<String, String> caller = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (caller != null) {
                    MDC.setContextMap(caller);
                }
                try {
                    task.run();
                } finally {
                    // Pool threads are reused; leave the slate as we found it.
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
