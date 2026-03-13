package com.agentassist.ai.ollama;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * Ollama implementation of AiProvider (uses Mistral 7B by default).
 * Always created when OllamaChatModel is available.
 */
@Slf4j
@Service("ollamaProvider")
@ConditionalOnBean(OllamaChatModel.class)
public class OllamaProvider extends BaseAiProvider {

    public OllamaProvider(OllamaChatModel chatModel, AiConfig config) {
        super(chatModel, "Ollama:" + config.getOllama().getModel());
        log.info("========================================");
        log.info("AI PROVIDER: Ollama initialized");
        log.info("Model: {}", config.getOllama().getModel());
        log.info("========================================");
    }
}
