package com.agentassist.rag;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Hedged embedding calls: fire the request, and if no answer has come back by
 * each configured delay, fire it again; the first answer wins and the rest are
 * cancelled. After the last delay nothing more is fired.
 *
 * <p>Measured against api.openai.com the embedding itself takes ~50ms of server
 * time, but the wall time is a lottery: ~1s most of the time, then 5, 12, 24,
 * 36 seconds for no reason on the server side (OpenAI's own processing-time
 * header says 44-69ms on those calls). Each request draws independently — a
 * call stuck for 36s had a twin, fired at the same moment, answer in 0.6s. So
 * the cheapest fix is a second draw: ten paired calls went from "≤3s in 8/10,
 * avg 5.5s, worst 36.5s" to "≤3s in 9/10, avg 2.1s, worst 12.6s" with a hedge
 * at 1.5s. The tokens are tiny, so the extra calls cost nothing that matters.
 * This replaces per-attempt retry, which was the same idea applied 12 seconds
 * too late.</p>
 *
 * <p>What it does not fix: a stretch where every draw is slow. That needs
 * embeddings off this path altogether (in-process, or Azure OpenAI).</p>
 */
@Slf4j
public final class HedgedEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private final EmbeddingModel delegate;
    private final long[] hedgeDelaysMs;
    /** Hard ceiling on one hedged call: last hedge delay + one attempt's own budget. */
    private final Duration overallCap;
    private final ExecutorService workers;
    private final ScheduledExecutorService timer;

    /**
     * @param hedgeDelaysMs when to fire attempts 2, 3, ... (ms after the first); empty = plain pass-through
     * @param attemptBudget the most one attempt can take on its own (connect + read timeout)
     */
    public HedgedEmbeddingModel(EmbeddingModel delegate, long[] hedgeDelaysMs, Duration attemptBudget) {
        this.delegate = delegate;
        this.hedgeDelaysMs = hedgeDelaysMs == null ? new long[0] : hedgeDelaysMs.clone();
        long lastDelay = this.hedgeDelaysMs.length == 0 ? 0 : this.hedgeDelaysMs[this.hedgeDelaysMs.length - 1];
        this.overallCap = attemptBudget.plusMillis(lastDelay).plusSeconds(1);
        this.workers = Executors.newCachedThreadPool(named("embed-hedge-"));
        this.timer = Executors.newSingleThreadScheduledExecutor(named("embed-hedge-timer"));
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return hedged(() -> delegate.call(request));
    }

    @Override
    public float[] embed(Document document) {
        return hedged(() -> delegate.embed(document));
    }

    private <T> T hedged(Supplier<T> attempt) {
        if (hedgeDelaysMs.length == 0) {
            return attempt.get();
        }
        long start = System.nanoTime();
        int total = hedgeDelaysMs.length + 1;
        CompletableFuture<T> winner = new CompletableFuture<>();
        List<Future<?>> inFlight = new CopyOnWriteArrayList<>();
        AtomicInteger failed = new AtomicInteger();
        AtomicReference<Throwable> lastError = new AtomicReference<>();
        Map<String, String> mdc = MDC.getCopyOfContextMap(); // keep the request id on the hedge threads

        inFlight.add(workers.submit(() -> run(1, attempt, winner, failed, total, lastError, start, mdc)));
        for (int i = 0; i < hedgeDelaysMs.length; i++) {
            int n = i + 2;
            inFlight.add(timer.schedule(() -> {
                if (winner.isDone()) {
                    return;
                }
                if (mdc != null) {
                    MDC.setContextMap(mdc); // so this line carries the request id too
                }
                try {
                    log.info("[InternalRag] embedding: no answer after {}ms - firing attempt {}", ms(start), n);
                } finally {
                    MDC.clear();
                }
                inFlight.add(workers.submit(() -> run(n, attempt, winner, failed, total, lastError, start, mdc)));
            }, hedgeDelaysMs[i], TimeUnit.MILLISECONDS));
        }

        try {
            return winner.get(overallCap.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw rethrow(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Embedding did not answer within " + overallCap.toMillis()
                    + "ms across " + total + " attempts", lastError.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an embedding", e);
        } finally {
            inFlight.forEach(f -> f.cancel(true)); // pending hedges never fire; running ones are interrupted
        }
    }

    private <T> void run(int n, Supplier<T> attempt, CompletableFuture<T> winner, AtomicInteger failed, int total,
                         AtomicReference<Throwable> lastError, long start, Map<String, String> mdc) {
        if (winner.isDone()) {
            return;
        }
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
        try {
            T result = attempt.get();
            if (winner.complete(result) && n > 1) {
                log.info("[InternalRag] embedding answered by attempt {} in {}ms - earlier attempt abandoned", n, ms(start));
            }
        } catch (Throwable t) {
            if (winner.isDone()) {
                return; // a cancelled loser: nothing to report
            }
            lastError.set(t);
            if (t instanceof NonTransientAiException || failed.incrementAndGet() == total) {
                winner.completeExceptionally(t); // a 4xx will not get better on a second draw
            } else {
                log.warn("[InternalRag] embedding attempt {} failed after {}ms: {}", n, ms(start), t.toString());
            }
        } finally {
            MDC.clear();
        }
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException re) {
            return re;
        }
        if (cause instanceof Error err) {
            throw err;
        }
        return new IllegalStateException(cause);
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static ThreadFactory named(String prefix) {
        AtomicInteger seq = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @Override
    public void close() {
        timer.shutdownNow();
        workers.shutdownNow();
    }
}
