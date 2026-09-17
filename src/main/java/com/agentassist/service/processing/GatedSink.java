package com.agentassist.service.processing;

import java.util.function.Consumer;

/**
 * A token sink that holds what it receives until told whether to pass it on.
 *
 * <p>Knowledge-base retrieval now starts before the intent is known. Almost
 * always the reply it produces is the one shown, so its tokens should flow as
 * they arrive — but if the intent turns out to be one this project filters
 * out, the branch discards that reply for a canned message, and text that had
 * been typing on screen would vanish. So tokens buffer until the intent lands
 * (~1s), then either flush and flow live, or are dropped. Retrieval's first
 * token rarely beats the intent call, so the buffer is usually empty when it
 * opens and nobody notices it existed.</p>
 */
final class GatedSink implements Consumer<String> {

    private enum State { BUFFERING, OPEN, DISCARDED }

    private final Consumer<String> downstream;
    private final StringBuilder buffer = new StringBuilder();
    private State state = State.BUFFERING;

    GatedSink(Consumer<String> downstream) {
        this.downstream = downstream;
    }

    @Override
    public synchronized void accept(String text) {
        switch (state) {
            case BUFFERING -> buffer.append(text);
            case OPEN -> downstream.accept(text);
            case DISCARDED -> { /* the reply will not be shown; nothing to relay */ }
        }
    }

    /** Let everything buffered through, then everything after it, live. */
    synchronized void open() {
        if (state != State.BUFFERING) {
            return;
        }
        state = State.OPEN;
        if (buffer.length() > 0) {
            downstream.accept(buffer.toString());
            buffer.setLength(0);
        }
    }

    /** The reply is not going to be used: drop what is held and what follows. */
    synchronized void discard() {
        state = State.DISCARDED;
        buffer.setLength(0);
    }
}
