package com.agentassist.ai.openai;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import com.agentassist.configregistry.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

/**
 * OpenAI implementation of AiProvider.
 * Always registered; whether it is USED is decided per call by
 * {@link com.agentassist.ai.AiProviderFactory} from the registry setting
 * ai.active_provider (portal-switchable, one provider active at a time).
 */
@Slf4j
@Service
public class OpenAiProvider extends BaseAiProvider {

    public OpenAiProvider(OpenAiChatModel chatModel, AiConfig config, PromptService promptService) {
        super(chatModel, "OpenAI:" + config.getOpenai().getModel(), promptService);
        log.info("========================================");
        log.info("AI PROVIDER: OpenAI");
        log.info("Model: {}", config.getOpenai().getModel());
        log.info("========================================");
    }
}
