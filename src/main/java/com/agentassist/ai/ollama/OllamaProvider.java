package com.agentassist.ai.ollama;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import com.agentassist.configregistry.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

/**
 * Ollama implementation of AiProvider (uses Mistral 7B by default).
 * Always registered; whether it is USED is decided per call by
 * {@link com.agentassist.ai.AiProviderFactory} from the registry setting
 * ai.active_provider (portal-switchable, one provider active at a time).
 */
@Slf4j
@Service
public class OllamaProvider extends BaseAiProvider {

    public OllamaProvider(OllamaChatModel chatModel, AiConfig config, PromptService promptService) {
        super(chatModel, "Ollama:" + config.getOllama().getModel(), promptService);
        log.info("========================================");
        log.info("AI PROVIDER: Ollama (Local)");
        log.info("Model: {}", config.getOllama().getModel());
        log.info("========================================");
    }
}
