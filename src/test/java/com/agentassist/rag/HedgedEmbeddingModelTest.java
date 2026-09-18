package com.agentassist.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.NonTransientAiException;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hedge exists for one measured situation: an embedding call that is
 * stuck for 5-36 seconds while an identical call fired a moment later answers
 * in under a second. These pin that the hedge fires only when needed, that the
 * first answer wins, and that nothing is fired after the last delay.
 */
class HedgedEmbeddingModelTest {

    /** A delegate whose n-th call behaves as scripted (sleep-then-answer, or throw). */
    static final class Scripted implements EmbeddingModel {
        final List<Callable<float[]>> steps;
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger interrupted = new AtomicInteger();

        Scripted(List<Callable<float[]>> steps) {
            this.steps = steps;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            int n = calls.incrementAndGet();
            try {
                return new EmbeddingResponse(List.of(new Embedding(steps.get(n - 1).call(), 0)));
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public float[] embed(Document document) {
            return embed(document.getText());
        }
    }

    static Callable<float[]> answerAfter(long ms, float value) {
        return () -> {
            Thread.sleep(ms);
            return new float[] {value};
        };
    }

    static Callable<float[]> failing(RuntimeException e) {
        return () -> {
            throw e;
        };
    }

    static HedgedEmbeddingModel hedged(Scripted delegate, long... delays) {
        return new HedgedEmbeddingModel(delegate, delays, Duration.ofSeconds(5));
    }

    @Test
    void fastAnswerNeverFiresAHedge() throws Exception {
        Scripted delegate = new Scripted(List.of(answerAfter(30, 1f), answerAfter(30, 2f)));
        try (HedgedEmbeddingModel model = hedged(delegate, 200, 400)) {
            assertArrayEquals(new float[] {1f}, model.embed("x"));
            Thread.sleep(500); // past every hedge delay
            assertEquals(1, delegate.calls.get());
        }
    }

    @Test
    void hedgeOvertakesAStuckAttempt() throws Exception {
        Scripted delegate = new Scripted(List.of(answerAfter(3000, 1f), answerAfter(30, 2f)));
        try (HedgedEmbeddingModel model = hedged(delegate, 150, 2000)) {
            long start = System.nanoTime();
            float[] result = model.embed("x");
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertArrayEquals(new float[] {2f}, result, "the hedge's answer wins");
            assertTrue(elapsedMs < 1000, "answered at " + elapsedMs + "ms, not after the stuck 3s");
            assertEquals(2, delegate.calls.get(), "no third attempt: the answer arrived before its delay");
            Thread.sleep(100);
            assertEquals(1, delegate.interrupted.get(), "the stuck attempt is cancelled, not left running");
        }
    }

    @Test
    void stopsFiringAfterTheLastDelay() {
        RuntimeException boom = new IllegalStateException("read timed out");
        Scripted delegate = new Scripted(List.of(failing(boom), failing(boom), failing(boom), failing(boom)));
        try (HedgedEmbeddingModel model = hedged(delegate, 50, 100)) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> model.embed("x"));
            assertEquals("read timed out", e.getMessage());
            assertEquals(3, delegate.calls.get(), "first attempt + two hedges, then nothing");
        }
    }

    @Test
    void nonTransientErrorFailsAtOnce() {
        Scripted delegate = new Scripted(List.of(failing(new NonTransientAiException("401 Unauthorized"))));
        try (HedgedEmbeddingModel model = hedged(delegate, 500, 1000)) {
            long start = System.nanoTime();
            assertThrows(NonTransientAiException.class, () -> model.embed("x"));
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMs < 400, "did not wait for hedges that cannot help");
            assertEquals(1, delegate.calls.get());
        }
    }

    @Test
    void withoutDelaysItIsAPlainPassThrough() {
        Scripted delegate = new Scripted(List.of(answerAfter(10, 7f)));
        try (HedgedEmbeddingModel model = hedged(delegate)) {
            assertArrayEquals(new float[] {7f}, model.embed("x"));
            assertEquals(1, delegate.calls.get());
        }
    }
}
