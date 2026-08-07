package com.agentassist.ai.ollama;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import com.agentassist.configregistry.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Ollama implementation of AiProvider (uses Mistral 7B by default).
 * Activated when ai.provider=ollama (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaProvider extends BaseAiProvider {

    public OllamaProvider(OllamaChatModel chatModel, AiConfig config, PromptService promptService) {
        super(chatModel, "Ollama:" + config.getOllama().getModel(), promptService);
        log.info("========================================");
        log.info("AI PROVIDER: Ollama (Local)");
        log.info("Model: {}", config.getOllama().getModel());
        log.info("========================================");
    }
}
