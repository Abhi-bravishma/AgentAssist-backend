package com.agentassist.service.processing;

import com.agentassist.configregistry.IntentCodes;
import com.agentassist.configregistry.IntentRegistryService;
import com.agentassist.configregistry.PromptService;
import com.agentassist.configregistry.TemplateKeys;
import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.ConversationResponse;
import com.agentassist.dto.responseDTO.KnowledgeSource;
import com.agentassist.dto.responseDTO.SuggestedResponse;
import com.agentassist.dto.salesforce.CustomerPolicyData;
import com.agentassist.model.MessageEntity;
import com.agentassist.model.SenderType;
import com.agentassist.service.analysis.AnalysisService;
import com.agentassist.service.checklist.ChecklistCacheService;
import com.agentassist.service.checklist.ChecklistService;
import com.agentassist.service.checklist.ChecklistService.ChecklistContext;
import com.agentassist.service.conversation.ConversationService;
import com.agentassist.service.conversation.MessageService;
import com.agentassist.service.salesforce.PolicyCacheService;
import com.agentassist.service.salesforce.SalesforceClient;
import com.agentassist.service.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Main message-processing pipeline (Part 3a restructure).
 *
 * <p>{@link #processMessage(String, String, String, String, String)} runs a
 * fixed sequence of stages, each a private method that reads and writes a
 * shared per-request {@link Pipeline} state object:
 *
 * <ol>
 *   <li>{@link #detectLanguage} — language of the incoming text</li>
 *   <li>{@link #initConversation} — get/create conversation, set base language</li>
 *   <li>{@link #translateAndSave} — English translation + persist the message</li>
 *   <li>{@link #loadHistory} — conversation history (English + original)</li>
 *   <li>{@link #resolveChecklist} — intent detection, checklist cache, project gating</li>
 *   <li>{@link #analyze} — AI sentiment/summary/suggestion bundle</li>
 *   <li>{@link #updateMessageSentiment} — persist current-message sentiment</li>
 *   <li>{@link #buildSuggestions} — dispatch to one of the suggestion branches</li>
 *   <li>{@link #assembleResponse} — response DTO</li>
 * </ol>
 *
 * <p>Part 3a is behavior-preserving: every stage body was moved VERBATIM from
 * the old monolithic method; only the structure changed. Behaviour changes
 * (language fixes §4.9–4.11) land as separate commits on top.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationProcessingService {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final TranslationService translationService;
    private final AnalysisService analysisService;
    private final SalesforceClient salesforceClient;
    private final PolicyCacheService policyCacheService;
    private final ChecklistService checklistService;
    private final ChecklistCacheService checklistCacheService;
    private final PromptService promptService;
    private final IntentRegistryService intentRegistryService;

    /**
     * Mutable per-request state threaded through the pipeline stages.
     * Package-private for tests.
     */
    static final class Pipeline {
        final String interactionId;
        final String from;
        final String messageText;
        final String mobileNumber;
        final String projectName;

        boolean isCustomer;
        String detectedLang;
        String english;
        MessageEntity saved;

        List<MessageEntity> all;
        List<String> englishConversation;
        List<String> originalConversation;

        // Policy data - NOT fetched automatically on first message. Salesforce is
        // only called when needed (fee waiver, home loan via checklist).
        CustomerPolicyData policyData;

        ChecklistContext checklistContext;
        ChecklistContext currentMessageContext;
        boolean useChecklistForThisMessage;

        String policyContext;
        AiAnalysisBundle bundle;

        List<SuggestedResponse> suggestions;
        List<KnowledgeSource> knowledgeSources;
        int documentsFound;
        boolean usedKnowledgeBase;

        Pipeline(String interactionId, String from, String messageText,
                 String mobileNumber, String projectName) {
            this.interactionId = interactionId;
            this.from = from;
            this.messageText = messageText;
            this.mobileNumber = mobileNumber;
            this.projectName = projectName;
        }
    }

    /**
     * Process message without mobile number (backward compatible).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse processMessage(String interactionId, String from, String messageText) {
        return processMessage(interactionId, from, messageText, null, null);
    }

    /**
     * Process message with optional mobile number for Salesforce policy lookup (backward compatible).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse processMessage(String interactionId, String from, String messageText, String mobileNumber) {
        return processMessage(interactionId, from, messageText, mobileNumber, null);
    }

    /**
     * Process message with optional mobile number and project name for filtering.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConversationResponse processMessage(String interactionId, String from, String messageText, String mobileNumber, String projectName) {
        log.info("[Process] Processing message - interactionId: {}, from: {}, length: {}, hasMobile: {}, projectName: {}",
                interactionId, from, messageText.length(), mobileNumber != null && !mobileNumber.isBlank(),
                projectName != null ? projectName : "ALL");
        long startTime = System.currentTimeMillis();

        Pipeline ctx = new Pipeline(interactionId, from, messageText, mobileNumber, projectName);

        detectLanguage(ctx);
        initConversation(ctx);
        translateAndSave(ctx);
        loadHistory(ctx);
        resolveChecklist(ctx);
        analyze(ctx);
        updateMessageSentiment(ctx);
        buildSuggestions(ctx);
        ConversationResponse resp = assembleResponse(ctx);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Process] Message processing completed in {}ms", duration);

        return resp;
    }

    // ==================== stage 1: language detection ====================

    private void detectLanguage(Pipeline ctx) {
        log.debug("[Process] Step 1: Detecting language...");
        ctx.detectedLang = translationService.detect(ctx.messageText);
        ctx.isCustomer = "customer".equalsIgnoreCase(ctx.from);
        log.info("[Process] Language detected: {}, isCustomer: {}", ctx.detectedLang, ctx.isCustomer);
    }

    // ==================== stage 2: conversation + base language ====================

    private void initConversation(Pipeline ctx) {
        log.debug("[Process] Step 2: Getting/creating conversation...");
        var conv = conversationService.getOrCreate(ctx.interactionId);
        if (ctx.isCustomer && (conv.getBaseLanguage() == null || conv.getBaseLanguage().isBlank())) {
            conversationService.setBaseLanguage(ctx.interactionId, ctx.detectedLang);
            log.info("[Process] Set base language to: {}", ctx.detectedLang);
        }
    }

    // ==================== stage 3+4: translate + persist ====================

    private void translateAndSave(Pipeline ctx) {
        log.debug("[Process] Step 3: Translating to English...");
        ctx.english = translationService.toEnglish(ctx.messageText);
        log.debug("[Process] English translation length: {}", ctx.english.length());

        log.debug("[Process] Step 4: Saving message to DB...");
        ctx.saved = messageService.save(
                MessageEntity.builder()
                        .interactionId(ctx.interactionId)
                        .sender(ctx.isCustomer ? SenderType.customer : SenderType.user)
                        .originalText(ctx.messageText)
                        .originalLanguage(ctx.detectedLang)
                        .englishText(ctx.english)
                        .build()
        );
        log.info("[Process] Message saved with ID: {}", ctx.saved.getId());
    }

    // ==================== stage 5: history ====================

    private void loadHistory(Pipeline ctx) {
        log.debug("[Process] Step 5: Building conversation history...");
        ctx.all = messageService.fetchByInteraction(ctx.interactionId);

        ctx.englishConversation = ctx.all.stream()
                .map(m -> m.getSender().name().equalsIgnoreCase("user") ? "agent" : m.getSender() + " : " + m.getEnglishText())
                .toList();
        ctx.originalConversation = ctx.all.stream()
                .map(m -> m.getSender().name().equalsIgnoreCase("user") ? "agent" : m.getSender() + " : " + m.getOriginalText())
                .toList();

        log.info("[Process] Conversation has {} messages", ctx.englishConversation.size());
    }

    // ==================== stage 5.6: checklist / intent ====================

    private void resolveChecklist(Pipeline ctx) {
        log.debug("[Process] Step 5.6: Checking for checklist operations...");
        ctx.checklistContext = checklistCacheService.get(ctx.interactionId);
        ctx.useChecklistForThisMessage = false;

        // Always detect intent for current message (filtered by projectName)
        // METRO: FEE_WAIVER, HOME_LOAN_CLOSURE | ALLIANZ: POLICY, CLAIMS | null: ALL
        ctx.currentMessageContext = checklistService.buildChecklistContext(
                ctx.englishConversation, ctx.originalConversation, ctx.mobileNumber, ctx.projectName);
        String currentIntent = ctx.currentMessageContext.operationType();
        log.info("[Process] Current message intent: {} (project: {})", currentIntent,
                ctx.projectName != null ? ctx.projectName : "ALL");

        if (!IntentCodes.NONE.equals(currentIntent)) {
            // Current message is fee waiver or home loan - use checklist flow
            ctx.checklistContext = ctx.currentMessageContext;
            checklistCacheService.put(ctx.interactionId, ctx.checklistContext);
            ctx.useChecklistForThisMessage = true;
            log.info("[Process] Checklist operation detected: {}, hasCustomerData: {}",
                    ctx.checklistContext.operationType(), ctx.checklistContext.hasContext());
        } else if (ctx.checklistContext != null && ctx.checklistContext.hasContext()) {
            // Current message is GENERAL but we have cached Salesforce data
            // Use cached data as context but NOT the checklist-specific prompt
            log.info("[Process] Using cached Salesforce data for GENERAL query (cached operation: {})",
                    ctx.checklistContext.operationType());
            ctx.useChecklistForThisMessage = false; // Don't use checklist prompt
        }
    }

    // ==================== stage 6: AI analysis ====================

    private void analyze(Pipeline ctx) {
        log.debug("[Process] Step 6: Calling AI for analysis...");

        // Build context from cached Salesforce data if available
        String salesforceContext = null;
        if (ctx.checklistContext != null && ctx.checklistContext.hasContext()) {
            salesforceContext = ctx.checklistContext.getFullContext();
        }
        ctx.policyContext = ctx.policyData != null ? ctx.policyData.toAiContext() : salesforceContext;

        if (ctx.useChecklistForThisMessage && ctx.checklistContext != null && ctx.checklistContext.hasContext()) {
            // Use checklist-aware analysis (fee waiver / home loan closure)
            log.info("[Process] Using checklist-aware AI analysis for: {}", ctx.checklistContext.operationType());
            ctx.bundle = analysisService.analyzeConversationWithChecklist(
                    ctx.englishConversation, ctx.english,
                    ctx.checklistContext.getFullContext(),
                    ctx.checklistContext.operationType());
        } else {
            // Standard analysis - use cached Salesforce data if available
            log.info("[Process] Using standard AI analysis with context: {}", ctx.policyContext != null ? "yes" : "no");
            ctx.bundle = analysisService.analyzeConversationWithContext(
                    ctx.englishConversation, ctx.english, ctx.policyContext);
        }

        log.info("[Process] AI analysis complete - overall: {}, current: {}, usedChecklist: {}",
                ctx.bundle.getOverall_sentiment_score(), ctx.bundle.getCurrent_sentiment_score(),
                ctx.checklistContext != null && ctx.checklistContext.hasContext());
    }

    // ==================== stage 7: message sentiment ====================

    private void updateMessageSentiment(Pipeline ctx) {
        if (ctx.isCustomer) {
            ctx.saved.setSentiment(ctx.bundle.getCurrent_sentiment_label());
            ctx.saved.setSentimentScore(ctx.bundle.getCurrent_sentiment_score());
            messageService.save(ctx.saved);
            log.debug("[Process] Updated message sentiment: {} ({})",
                    ctx.bundle.getCurrent_sentiment_label(), ctx.bundle.getCurrent_sentiment_score());
        }
    }

    // ==================== stage 8: suggestions (branch dispatch) ====================

    private void buildSuggestions(Pipeline ctx) {
        log.debug("[Process] Step 7: Building suggestions...");
        ctx.documentsFound = 0;
        ctx.usedKnowledgeBase = false;

        // Skip suggestions for agent messages - only process customer messages
        if (!ctx.isCustomer) {
            log.info("[Process] Skipping suggestions for agent message");
            ctx.suggestions = Collections.emptyList();
            ctx.knowledgeSources = Collections.emptyList();
        } else if (ctx.currentMessageContext.wasIntentFiltered()) {
            filteredIntentSuggestion(ctx);
        } else if (ctx.useChecklistForThisMessage && ctx.checklistContext != null && ctx.checklistContext.hasContext()) {
            checklistSuggestions(ctx);
        } else {
            generalSuggestions(ctx);
        }
    }

    /**
     * Intent was detected but filtered out due to project mismatch.
     * Provide helpful message instead of going to RAG.
     */
    private void filteredIntentSuggestion(Pipeline ctx) {
        log.info("[Process] Intent {} was filtered for project {}, providing helpful message",
                ctx.currentMessageContext.filteredIntent(), ctx.projectName);
        String filteredIntentMessage =
                intentRegistryService.filteredMessage(ctx.currentMessageContext.filteredIntent(), ctx.projectName);
        SuggestedResponse sr = new SuggestedResponse();
        sr.setEnglishReply(filteredIntentMessage);
        if (!ctx.detectedLang.equalsIgnoreCase("en")) {
            sr.setUserLanguageReply(translationService.fromEnglish(filteredIntentMessage, ctx.detectedLang));
        }
        ctx.suggestions = Collections.singletonList(sr);
        ctx.knowledgeSources = Collections.emptyList();
    }

    /**
     * CUSTOMER-SPECIFIC QUERY (POLICY/CLAIMS/FEE_WAIVER/HOME_LOAN_CLOSURE with Salesforce data).
     * KNOWLEDGE BASE FIRST: when the FAQ has a document matching this question,
     * answer from the document. The checklist answer (GPT + Salesforce data) is the
     * fallback for when the knowledge base has nothing relevant.
     */
    private void checklistSuggestions(Pipeline ctx) {
        boolean isSimpleMessage = isGreetingOrSimpleMessage(ctx.english);

        // A billing question can only be answered from the customer's own record - no
        // FAQ document knows their balance - so the knowledge base never overrides it.
        // Documents are still fetched for the knowledge-source chips.
        boolean customerDataOnly = IntentCodes.BILLING.equals(ctx.checklistContext.operationType());

        // Checklist answer - built up front so it is ready as the fallback
        List<SuggestedResponse> checklistSuggestions = toSuggestedResponses(ctx.bundle.getSuggestions(), ctx.detectedLang);

        if (analysisService.isRagEnabled() && !isSimpleMessage) {
            var ragResult = analysisService.buildReplySuggestionsWithRag(ctx.all, ctx.policyContext, ctx.projectName);
            ctx.documentsFound = ragResult.documentsFound();
            ctx.knowledgeSources = ragResult.knowledgeSources();
            ctx.usedKnowledgeBase = ragResult.usedKnowledgeBase();

            if (!customerDataOnly && ctx.documentsFound > 0 && !ragResult.suggestions().isEmpty()) {
                ctx.suggestions = ragResult.suggestions();
                log.info("[Process] Using KNOWLEDGE BASE suggestion for {} ({} documents matched)",
                        ctx.checklistContext.operationType(), ctx.documentsFound);
            } else {
                ctx.suggestions = checklistSuggestions;
                log.info("[Process] Knowledge base found nothing for {}, using CHECKLIST suggestion",
                        ctx.checklistContext.operationType());
            }
        } else {
            ctx.suggestions = checklistSuggestions;
            ctx.knowledgeSources = Collections.emptyList();
            log.info("[Process] Using CHECKLIST suggestion for {} (knowledge base skipped)",
                    ctx.checklistContext.operationType());
        }
    }

    /**
     * GENERAL QUERY - Always use RAG for knowledge base lookup.
     * Even if we have cached Salesforce data, RAG should be called for general questions.
     * Skip RAG for greetings and simple messages - no need for knowledge base lookup.
     */
    private void generalSuggestions(Pipeline ctx) {
        boolean isSimpleMessage = isGreetingOrSimpleMessage(ctx.english);

        if (analysisService.isRagEnabled() && !isSimpleMessage) {
            // Use RAG for suggestions with knowledge base context + policy data
            log.info("[Process] Using RAG for suggestions with {} messages, projectName: {}...", ctx.all.size(), ctx.projectName);
            var ragResult = analysisService.buildReplySuggestionsWithRag(ctx.all, ctx.policyContext, ctx.projectName);
            ctx.suggestions = ragResult.suggestions();
            ctx.knowledgeSources = ragResult.knowledgeSources();
            ctx.documentsFound = ragResult.documentsFound();
            ctx.usedKnowledgeBase = ragResult.usedKnowledgeBase();
            log.info("[Process] RAG returned {} suggestions, {} knowledge sources",
                    ctx.suggestions.size(), ctx.knowledgeSources.size());

            // Knowledge base found nothing usable. Only fall back to the AI answer when
            // there is real customer data behind it - otherwise the model has nothing to
            // ground on and will invent figures (it quoted a 10% discount for a document
            // that says 25%). An honest "I don't know" beats a confident wrong number.
            if (ctx.documentsFound == 0 || ctx.suggestions.isEmpty()) {
                boolean hasCustomerData = ctx.checklistContext != null && ctx.checklistContext.hasContext();
                if (hasCustomerData && !ctx.bundle.getSuggestions().isEmpty()) {
                    log.info("[Process] Knowledge base found nothing, using AI suggestions grounded in customer data");
                    ctx.suggestions = toSuggestedResponses(ctx.bundle.getSuggestions(), ctx.detectedLang);
                } else {
                    log.warn("[Process] Knowledge base found nothing and no customer data - returning no-information reply instead of an ungrounded answer");
                    ctx.suggestions = toSuggestedResponses(List.of(
                            promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of())),
                            ctx.detectedLang);
                }
            }
        } else if (isSimpleMessage) {
            // Skip RAG for greetings - use AI suggestions directly
            log.info("[Process] Simple message detected, skipping RAG...");
            ctx.suggestions = toSuggestedResponses(ctx.bundle.getSuggestions(), ctx.detectedLang);
            ctx.knowledgeSources = Collections.emptyList();
            log.info("[Process] Generated {} suggestions (skipped RAG for simple message)", ctx.suggestions.size());
        } else {
            // Fallback to AI-based suggestions without RAG
            log.info("[Process] RAG disabled, using AI for suggestions...");
            ctx.suggestions = toSuggestedResponses(ctx.bundle.getSuggestions(), ctx.detectedLang);
            ctx.knowledgeSources = Collections.emptyList();
            log.info("[Process] Generated {} suggestions (no RAG)", ctx.suggestions.size());
        }
    }

    // ==================== stage 9: response assembly ====================

    private ConversationResponse assembleResponse(Pipeline ctx) {
        // Get policyData from checklistContext if available
        CustomerPolicyData effectivePolicyData = ctx.policyData;
        if (effectivePolicyData == null && ctx.checklistContext != null && ctx.checklistContext.policyData() != null) {
            effectivePolicyData = ctx.checklistContext.policyData();
        }

        String customerName = null;
        if (ctx.checklistContext != null && ctx.checklistContext.creditCardData() != null) {
            customerName = ctx.checklistContext.creditCardData().getCustomerName();
        } else if (ctx.checklistContext != null && ctx.checklistContext.homeLoanData() != null) {
            customerName = ctx.checklistContext.homeLoanData().getCustomerName();
        } else if (ctx.checklistContext != null && ctx.checklistContext.policyData() != null) {
            customerName = ctx.checklistContext.policyData().getCustomerName();
        } else if (ctx.checklistContext != null && ctx.checklistContext.billingData() != null) {
            customerName = ctx.checklistContext.billingData().getCustomerName();
        } else if (effectivePolicyData != null) {
            customerName = effectivePolicyData.getCustomerName();
        }

        int creditCardsFound = ctx.checklistContext != null && ctx.checklistContext.creditCardData() != null
                && ctx.checklistContext.creditCardData().getCreditCards() != null
                ? ctx.checklistContext.creditCardData().getCreditCards().size() : 0;

        int homeLoansFound = ctx.checklistContext != null && ctx.checklistContext.homeLoanData() != null
                && ctx.checklistContext.homeLoanData().getHomeLoans() != null
                ? ctx.checklistContext.homeLoanData().getHomeLoans().size() : 0;

        int policiesFound = effectivePolicyData != null && effectivePolicyData.getPolicies() != null
                ? effectivePolicyData.getPolicies().size() : 0;

        int claimsFound = effectivePolicyData != null && effectivePolicyData.getClaims() != null
                ? effectivePolicyData.getClaims().size() : 0;

        return ConversationResponse.builder()
                .overallSentiment(ctx.bundle.getOverall_sentiment_score())
                .currentSentiment(ctx.bundle.getCurrent_sentiment_score())
                .summary(ctx.bundle.getSummary())
                .suggestedResponses(ctx.suggestions)
                .knowledgeSources(ctx.knowledgeSources)
                .documentsFound(ctx.documentsFound)
                .usedKnowledgeBase(ctx.usedKnowledgeBase)
                .usedPolicyData(effectivePolicyData != null)
                .customerName(customerName)
                .policiesFound(policiesFound)
                .claimsFound(claimsFound)
                .usedChecklist(ctx.checklistContext != null && ctx.checklistContext.hasContext())
                .checklistOperation(ctx.checklistContext != null && !IntentCodes.NONE.equals(ctx.checklistContext.operationType())
                        ? ctx.checklistContext.operationType() : null)
                .creditCardsFound(creditCardsFound)
                .homeLoansFound(homeLoansFound)
                .projectName(ctx.projectName)
                .build();
    }

    // ==================== helpers ====================

    /**
     * Wrap English suggestions as SuggestedResponse, translating into the customer's
     * language when it is not English.
     */
    private List<SuggestedResponse> toSuggestedResponses(List<String> englishSuggestions, String detectedLang) {
        if (englishSuggestions == null || englishSuggestions.isEmpty()) {
            return Collections.emptyList();
        }
        boolean isEnglishUser = detectedLang.equalsIgnoreCase("en");
        return englishSuggestions.stream()
                .map(s -> {
                    SuggestedResponse sr = new SuggestedResponse();
                    sr.setEnglishReply(s);
                    if (!isEnglishUser) {
                        sr.setUserLanguageReply(translationService.fromEnglish(s, detectedLang));
                    }
                    return sr;
                }).toList();
    }

    /**
     * Check if the message is a greeting or simple message that doesn't need RAG lookup.
     * This helps skip expensive knowledge base queries for casual conversation.
     */
    private boolean isGreetingOrSimpleMessage(String englishText) {
        if (englishText == null || englishText.isBlank()) {
            return true;
        }

        String normalized = englishText.toLowerCase().trim();

        // Common greetings
        String[] greetings = {
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening",
            "good night", "howdy", "greetings", "hiya", "yo", "sup", "what's up",
            "how are you", "how do you do", "nice to meet you", "pleased to meet you"
        };

        // Farewells
        String[] farewells = {
            "bye", "goodbye", "see you", "take care", "later", "cya", "farewell",
            "good bye", "see ya", "talk to you later", "ttyl", "catch you later"
        };

        // Thanks
        String[] thanks = {
            "thank you", "thanks", "thx", "ty", "thank u", "thanks a lot",
            "much appreciated", "appreciate it", "cheers"
        };

        // Simple acknowledgments
        String[] acknowledgments = {
            "ok", "okay", "sure", "yes", "no", "yep", "nope", "yeah", "nah",
            "alright", "got it", "understood", "i see", "makes sense"
        };

        // Positive feedback phrases (keywords to check with contains)
        String[] positiveFeedback = {
            "helpful", "great help", "very helpful", "truly helpful", "really helpful",
            "that helped", "that helps", "you helped", "this helped", "so helpful",
            "great job", "good job", "well done", "awesome", "excellent", "perfect",
            "wonderful", "amazing", "fantastic", "brilliant", "superb"
        };

        // Check exact matches or starts with
        for (String greeting : greetings) {
            if (normalized.equals(greeting) || normalized.startsWith(greeting + " ") ||
                normalized.startsWith(greeting + ",") || normalized.startsWith(greeting + "!")) {
                return true;
            }
        }

        for (String farewell : farewells) {
            if (normalized.equals(farewell) || normalized.startsWith(farewell + " ") ||
                normalized.startsWith(farewell + ",") || normalized.startsWith(farewell + "!")) {
                return true;
            }
        }

        for (String thank : thanks) {
            if (normalized.equals(thank) || normalized.startsWith(thank + " ") ||
                normalized.startsWith(thank + ",") || normalized.startsWith(thank + "!")) {
                return true;
            }
        }

        for (String ack : acknowledgments) {
            if (normalized.equals(ack) || normalized.equals(ack + ".") || normalized.equals(ack + "!")) {
                return true;
            }
        }

        // Check for positive feedback (contains check)
        for (String feedback : positiveFeedback) {
            if (normalized.contains(feedback)) {
                return true;
            }
        }

        // Very short messages (less than 5 words) that are likely casual
        String[] words = normalized.split("\\s+");
        if (words.length <= 2 && normalized.length() < 15) {
            return true;
        }

        return false;
    }
}
