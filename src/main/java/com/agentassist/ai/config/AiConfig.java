package com.agentassist.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiConfig {

    /**
     * Active AI provider: "openai" or "ollama"
     */
    private String provider = "ollama";

    private OpenAiConfig openai = new OpenAiConfig();
    private OllamaConfig ollama = new OllamaConfig();

    // NOTE: the live temperature comes from spring.ai.*.chat.options.temperature,
    // not from here - the old ai.*.temperature keys were read by nothing.
    @Data
    public static class OpenAiConfig {
        private String model = "gpt-4o-mini";
    }

    @Data
    public static class OllamaConfig {
        private String model = "mistral:7b";
    }
}
