package com.agentassist.ai.support;

import java.util.function.Consumer;

/**
 * Streaming twin of {@link ChatCaller}: same prompt in, same complete text out,
 * but every chunk the model produces is handed to {@code onToken} on the way.
 *
 * <p>Components hold this next to {@link ChatCaller} and pick one per call: no
 * sink means the blocking path (which the golden-test capture provider
 * intercepts); a sink means this one. The returned text is what the component
 * parses, exactly as it would have parsed the blocking result.</p>
 */
@FunctionalInterface
public interface ChatStreamer {

    /** Returns the model's complete raw text, or null on failure (never throws). */
    String call(String prompt, Consumer<String> onToken);
}
