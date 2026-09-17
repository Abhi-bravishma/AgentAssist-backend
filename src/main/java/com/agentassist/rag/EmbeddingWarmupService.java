package com.agentassist.rag;

import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps the OpenAI embeddings connection warm.
 *
 * <p>A warm embedding call takes ~0.5s. The first call on a cold connection
 * takes ~9s, because it pays DNS, TCP and the TLS handshake to api.openai.com —
 * measured, not estimated. Idle connections get dropped, so without this the
 * first agent of the morning eats that cost, and so does anyone arriving after
 * a quiet spell.
 *
 * <p>One throwaway embedding on startup and one every few minutes keeps the
 * connection established, so real requests always take the warm path. The text
 * is deliberately tiny — this is about the socket, not the vector, and the cost
 * is a rounding error against a single real message.
 *
 * <p>Failures are logged and swallowed: a warm-up that cannot reach OpenAI must
 * never stop the application starting or break a scheduled tick.</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "rag.internal.embedding.warmup.enabled",
        havingValue = "true", matchIfMissing = true)
public class EmbeddingWarmupService {

    private static final String PING = "ping";

    /** By name: several EmbeddingModel beans exist and @Qualifier is not copied
     *  onto a Lombok-generated constructor. */
    @Resource(name = "agentAssistEmbeddingModel")
    private EmbeddingModel embeddingModel;

    @Value("${rag.internal.embedding.warmup.enabled:true}")
    private boolean enabled;

    @EventListener(ApplicationReadyEvent.class)
    public void warmOnStartup() {
        warm("startup");
    }

    /**
     * Every four minutes: short enough to stay inside the idle window of both
     * OpenAI's edge and any NAT between here and it, long enough to be free.
     */
    @Scheduled(fixedDelayString = "${rag.internal.embedding.warmup.interval-ms:240000}",
            initialDelayString = "${rag.internal.embedding.warmup.interval-ms:240000}")
    public void keepWarm() {
        warm("scheduled");
    }

    private void warm(String trigger) {
        if (!enabled) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            embeddingModel.embed(PING);
            long ms = System.currentTimeMillis() - start;
            // Logged at DEBUG when quick, INFO when not: a slow warm-up is the
            // earliest warning that the next real request would have been slow.
            if (ms > 2000) {
                log.info("[InternalRag] Embedding warm-up ({}) took {}ms - connection was cold", trigger, ms);
            } else {
                log.debug("[InternalRag] Embedding warm-up ({}) {}ms", trigger, ms);
            }
        } catch (Exception e) {
            log.warn("[InternalRag] Embedding warm-up ({}) failed: {}", trigger, e.getMessage());
        }
    }
}
