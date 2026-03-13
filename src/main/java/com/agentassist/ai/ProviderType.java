package com.agentassist.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing available AI provider types.
 * Used to select which provider(s) to use for AI operations.
 */
public enum ProviderType {
    /**
     * Use OpenAI provider only (default)
     */
    OPENAI("openai"),

    /**
     * Use Ollama provider only
     */
    OLLAMA("ollama"),

    /**
     * Use both providers and return comparison results
     */
    BOTH("both");

    private final String value;

    ProviderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ProviderType fromValue(String value) {
        if (value == null) {
            return OPENAI; // default
        }
        for (ProviderType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return OPENAI; // default fallback
    }
}
