package com.agentassist.ai.openai;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.ai.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * OpenAI implementation of AiProvider.
 * Always created when OpenAiChatModel is available.
 */
@Slf4j
@Service("openAiProvider")
@ConditionalOnBean(OpenAiChatModel.class)
public class OpenAiProvider extends BaseAiProvider {

    public OpenAiProvider(OpenAiChatModel chatModel, AiConfig config) {
        super(chatModel, "OpenAI:" + config.getOpenai().getModel());
        log.info("========================================");
        log.info("AI PROVIDER: OpenAI initialized");
        log.info("Model: {}", config.getOpenai().getModel());
        log.info("========================================");
    }
}
