package com.agentassist.ai.openai;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import com.agentassist.configregistry.PromptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * OpenAI implementation of AiProvider.
 * Activated when ai.provider=openai.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiProvider extends BaseAiProvider {

    public OpenAiProvider(OpenAiChatModel chatModel, AiConfig config, PromptService promptService) {
        super(chatModel, "OpenAI:" + config.getOpenai().getModel(), promptService);
        log.info("========================================");
        log.info("AI PROVIDER: OpenAI");
        log.info("Model: {}", config.getOpenai().getModel());
        log.info("========================================");
    }
}
