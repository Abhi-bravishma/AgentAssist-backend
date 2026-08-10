package com.agentassist.ai.support;

/**
 * The one thing that differs between providers: sending a prompt to a model.
 * Components receive the facade's {@code call} as this interface, so virtual
 * dispatch is preserved — the golden-test capture provider overrides
 * {@code BaseAiProvider.call} and still intercepts every component's prompt.
 */
@FunctionalInterface
public interface ChatCaller {

    /** Returns the model's raw text, or null on failure (never throws). */
    String call(String prompt);
}
