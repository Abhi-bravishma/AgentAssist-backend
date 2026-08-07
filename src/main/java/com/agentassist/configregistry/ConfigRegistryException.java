package com.agentassist.configregistry;

/**
 * Thrown when the config registry cannot serve a required value — a missing
 * template key, an unresolvable brand attribute, or a placeholder with no
 * variable. Deliberately a fail-LOUD exception: an empty prompt sent to the
 * model is worse than a failed request, so there is no silent fallback.
 */
public class ConfigRegistryException extends RuntimeException {

    public ConfigRegistryException(String message) {
        super(message);
    }

    public ConfigRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
