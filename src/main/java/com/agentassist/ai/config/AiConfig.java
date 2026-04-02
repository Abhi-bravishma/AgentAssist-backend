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

    @Data
    public static class OpenAiConfig {
        private String model = "gpt-4o-mini";
        private double temperature = 0.2;
    }

    @Data
    public static class OllamaConfig {
        private String model = "mistral:7b";
        private double temperature = 0.2;
    }
}
