package com.agentassist.ai;

import com.agentassist.ai.support.ChatCaller;
import com.agentassist.ai.support.ComplianceAnalyzer;
import com.agentassist.ai.support.ConversationAnalyzer;
import com.agentassist.ai.support.FollowUpAnalyzer;
import com.agentassist.ai.support.IntentClassifier;
import com.agentassist.ai.support.TranslationEngine;
import com.agentassist.configregistry.IntentRegistryService;
import com.agentassist.configregistry.LanguageRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.AiAnalysisResult;
import com.agentassist.dto.responseDTO.ComplianceResponse;
import com.agentassist.dto.responseDTO.FollowUpCheckResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Facade over the per-domain AI components (Part 2 split). The only
 * provider-specific behaviour left here is {@link #call} — everything else
 * lives in {@code com.agentassist.ai.support}, wired with this facade's call
 * so subclass overrides (the golden-test capture provider) still intercept
 * every outgoing prompt.
 */
@Slf4j
public abstract class BaseAiProvider implements AiProvider {

    protected final ChatModel chatModel;
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final String providerName;
    protected final PromptService promptService;

    private final ConversationAnalyzer conversationAnalyzer;
    private final TranslationEngine translationEngine;
    private final IntentClassifier intentClassifier;
    private final FollowUpAnalyzer followUpAnalyzer;
    private final ComplianceAnalyzer complianceAnalyzer;

    protected BaseAiProvider(ChatModel chatModel, String providerName, PromptService promptService,
                             IntentRegistryService intentRegistryService,
                             LanguageRegistryService languageRegistryService) {
        this.chatModel = chatModel;
        this.providerName = providerName;
        this.promptService = promptService;

        // this::call keeps virtual dispatch — a subclass override of call()
        // (the golden capture provider) sees every component's prompt.
        ChatCaller chat = this::call;
        this.conversationAnalyzer = new ConversationAnalyzer(chat, providerName, promptService, mapper);
        this.translationEngine = new TranslationEngine(chat, providerName, promptService, languageRegistryService);
        this.intentClassifier = new IntentClassifier(chat, providerName, promptService, intentRegistryService);
        this.followUpAnalyzer = new FollowUpAnalyzer(chat, providerName, promptService, mapper);
        this.complianceAnalyzer = new ComplianceAnalyzer(chat, providerName, promptService, mapper);

        log.info("[AI] Provider initialized: {}", providerName);
    }

    @Override
    public String complete(String prompt) {
        return call(prompt);
    }

    // ==================== conversation analysis ====================

    @Override
    public AiAnalysisResult analyzeText(String text) {
        return conversationAnalyzer.analyzeText(text);
    }

    @Override
    public AiAnalysisBundle analyzeConversation(List<String> messages, String latestUserMsg) {
        return conversationAnalyzer.analyzeConversation(messages, latestUserMsg);
    }

    @Override
    public AiAnalysisBundle analyzeConversationWithContext(List<String> messages, String latestUserMsg, String policyContext) {
        return conversationAnalyzer.analyzeConversationWithContext(messages, latestUserMsg, policyContext);
    }

    @Override
    public AiAnalysisBundle analyzeConversationWithChecklist(List<String> messages, String latestUserMsg,
                                                             String checklistContext, String operationType) {
        return conversationAnalyzer.analyzeConversationWithChecklist(messages, latestUserMsg, checklistContext, operationType);
    }

    @Override
    public double computeOverallSentiment(List<String> messages) {
        return conversationAnalyzer.computeOverallSentiment(messages);
    }

    @Override
    public AiAnalysisBundle regenerateSuggestions(List<String> messages, String latestUserMsg, String previousSuggestion) {
        return conversationAnalyzer.regenerateSuggestions(messages, latestUserMsg, previousSuggestion);
    }

    @Override
    public AiAnalysisBundle regenerateSuggestionsWithContext(List<String> messages, String latestUserMsg,
                                                             String previousSuggestion, String checklistContext,
                                                             String customerName) {
        return conversationAnalyzer.regenerateSuggestionsWithContext(
                messages, latestUserMsg, previousSuggestion, checklistContext, customerName);
    }

    // ==================== translation & language ====================

    @Override
    public String translateToEnglish(String text) {
        return translationEngine.translateToEnglish(text);
    }

    @Override
    public String translateFromEnglish(String english, String targetLang) {
        return translationEngine.translateFromEnglish(english, targetLang);
    }

    @Override
    public String detectLanguage(String text) {
        return translationEngine.detectLanguage(text);
    }

    // ==================== intent / follow-up / compliance ====================

    @Override
    public String detectOperationType(List<String> messages, String latestMessage) {
        return intentClassifier.detectOperationType(messages, latestMessage);
    }

    @Override
    public FollowUpCheckResponse analyzeFollowUpRequirement(List<String> transcript, String customerName) {
        return followUpAnalyzer.analyzeFollowUpRequirement(transcript, customerName);
    }

    @Override
    public ComplianceResponse analyzeCompliance(List<String> agentMessages, String interactionId) {
        return complianceAnalyzer.analyzeCompliance(agentMessages, interactionId);
    }

    // ==================== the provider-specific part ====================

    /**
     * Call the underlying chat model.
     */
    protected String call(String promptText) {
        try {
            log.info("[AI:{}] Calling model, prompt length: {}", providerName, promptText.length());
            long startTime = System.currentTimeMillis();

            Prompt prompt = new Prompt(promptText);
            ChatResponse response = chatModel.call(prompt);

            if (response == null || response.getResult() == null) {
                log.warn("[AI:{}] Model returned null response", providerName);
                return null;
            }

            String content = response.getResult().getOutput().getText();
            long duration = System.currentTimeMillis() - startTime;

            log.info("[AI:{}] Response received in {}ms, length: {}", providerName, duration, content != null ? content.length() : 0);
            return content;

        } catch (Exception e) {
            log.error("[AI:{}] Model call failed: {} - {}", providerName, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                log.error("[AI:{}] Caused by: {}", providerName, e.getCause().getMessage());
            }
            return null;
        }
    }
}
