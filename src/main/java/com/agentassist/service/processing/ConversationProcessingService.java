package com.agentassist.service.processing;

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
import com.agentassist.service.checklist.ChecklistService.OperationType;
import com.agentassist.service.conversation.ConversationService;
import com.agentassist.service.conversation.MessageService;
import com.agentassist.service.salesforce.PolicyCacheService;
import com.agentassist.service.salesforce.SalesforceClient;
import com.agentassist.service.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     * Process message without mobile number (backward compatible).
     */
    @Transactional
    public ConversationResponse processMessage(String interactionId, String from, String messageText) {
        return processMessage(interactionId, from, messageText, null, null);
    }

    /**
     * Process message with optional mobile number for Salesforce policy lookup (backward compatible).
     */
    @Transactional
    public ConversationResponse processMessage(String interactionId, String from, String messageText, String mobileNumber) {
        return processMessage(interactionId, from, messageText, mobileNumber, null);
    }

    /**
     * Process message with optional mobile number and project name for filtering.
     */
    @Transactional
    public ConversationResponse processMessage(String interactionId, String from, String messageText, String mobileNumber, String projectName) {
        log.info("[Process] Processing message - interactionId: {}, from: {}, length: {}, hasMobile: {}, projectName: {}",
                interactionId, from, messageText.length(), mobileNumber != null && !mobileNumber.isBlank(),
                projectName != null ? projectName : "ALL");
        long startTime = System.currentTimeMillis();

        // 1. Detect language
        log.debug("[Process] Step 1: Detecting language...");
        String detectedLang = translationService.detect(messageText);
        boolean isCustomer = "customer".equalsIgnoreCase(from);
        log.info("[Process] Language detected: {}, isCustomer: {}", detectedLang, isCustomer);

        // 2. Initialize base language
        log.debug("[Process] Step 2: Getting/creating conversation...");
        var conv = conversationService.getOrCreate(interactionId);
        if (isCustomer && (conv.getBaseLanguage() == null || conv.getBaseLanguage().isBlank())) {
            conversationService.setBaseLanguage(interactionId, detectedLang);
            log.info("[Process] Set base language to: {}", detectedLang);
        }

        // 3. Convert to English for AI
        log.debug("[Process] Step 3: Translating to English...");
        String english = translationService.toEnglish(messageText);
        log.debug("[Process] English translation length: {}", english.length());

        // 4. Save raw message
        log.debug("[Process] Step 4: Saving message to DB...");
        MessageEntity saved = messageService.save(
                MessageEntity.builder()
                        .interactionId(interactionId)
                        .sender(isCustomer ? SenderType.customer : SenderType.user)
                        .originalText(messageText)
                        .originalLanguage(detectedLang)
                        .englishText(english)
                        .build()
        );
        log.info("[Process] Message saved with ID: {}", saved.getId());

        // 5. Build conversation histories (English + Original for multi-language detection)
        log.debug("[Process] Step 5: Building conversation history...");
        List<MessageEntity> all = messageService.fetchByInteraction(interactionId);
//        List<String> englishConversation = all.stream().map(MessageEntity::getEnglishText).toList();
//        List<String> originalConversation = all.stream().map(MessageEntity::getOriginalText).toList();

        List<String> englishConversation = all.stream()
                .map(m -> m.getSender().name().equalsIgnoreCase("user") ? "agent" : m.getSender() + " : " + m.getEnglishText())
                .toList();
        List<String> originalConversation = all.stream()
                .map(m -> m.getSender().name().equalsIgnoreCase("user") ? "agent" : m.getSender() + " : " + m.getOriginalText())
                .toList();

        log.info("[Process] Conversation has {} messages", englishConversation.size());

        // 5.5 Policy data - NOT fetched automatically on first message
        // Salesforce is only called when needed (fee waiver, home loan via checklist)
        CustomerPolicyData policyData = null;

        // 5.6 Detect and build checklist context (fee waiver, home loan closure)
        log.debug("[Process] Step 5.6: Checking for checklist operations...");
        ChecklistContext checklistContext = checklistCacheService.get(interactionId);
        boolean checklistDetectedThisMessage = false;
        boolean useChecklistForThisMessage = false;

        // Always detect intent for current message (filtered by projectName)
        // METRO: FEE_WAIVER, HOME_LOAN_CLOSURE | ALLIANZ: POLICY, CLAIMS | null: ALL
        ChecklistContext currentMessageContext = checklistService.buildChecklistContext(englishConversation, originalConversation, mobileNumber, projectName);
        OperationType currentIntent = currentMessageContext.operationType();
        log.info("[Process] Current message intent: {} (project: {})", currentIntent, projectName != null ? projectName : "ALL");

        if (currentIntent != OperationType.NONE) {
            // Current message is fee waiver or home loan - use checklist flow
            checklistContext = currentMessageContext;
            checklistCacheService.put(interactionId, checklistContext);
            checklistDetectedThisMessage = true;
            useChecklistForThisMessage = true;
            log.info("[Process] Checklist operation detected: {}, hasCustomerData: {}",
                    checklistContext.operationType(), checklistContext.hasContext());
        } else if (checklistContext != null && checklistContext.hasContext()) {
            // Current message is GENERAL but we have cached Salesforce data
            // Use cached data as context but NOT the checklist-specific prompt
            log.info("[Process] Using cached Salesforce data for GENERAL query (cached operation: {})",
                    checklistContext.operationType());
            useChecklistForThisMessage = false; // Don't use checklist prompt
        }

        // 6. Call AI with appropriate context
        log.debug("[Process] Step 6: Calling AI for analysis...");
        AiAnalysisBundle bundle;

        // Build context from cached Salesforce data if available
        String salesforceContext = null;
        if (checklistContext != null && checklistContext.hasContext()) {
            salesforceContext = checklistContext.getFullContext();
        }
        String policyContext = policyData != null ? policyData.toAiContext() : salesforceContext;

        if (useChecklistForThisMessage && checklistContext != null && checklistContext.hasContext()) {
            // Use checklist-aware analysis (fee waiver / home loan closure)
            log.info("[Process] Using checklist-aware AI analysis for: {}", checklistContext.operationType());
            bundle = analysisService.analyzeConversationWithChecklist(
                    englishConversation, english,
                    checklistContext.getFullContext(),
                    checklistContext.operationType().name());
        } else {
            // Standard analysis - use cached Salesforce data if available
            log.info("[Process] Using standard AI analysis with context: {}", policyContext != null ? "yes" : "no");
            bundle = analysisService.analyzeConversationWithContext(
                    englishConversation, english, policyContext);
        }

        log.info("[Process] AI analysis complete - overall: {}, current: {}, usedChecklist: {}",
                bundle.getOverall_sentiment_score(), bundle.getCurrent_sentiment_score(),
                checklistContext != null && checklistContext.hasContext());

        // 7. Update last message sentiment
        if (isCustomer) {
            saved.setSentiment(bundle.getCurrent_sentiment_label());
            saved.setSentimentScore(bundle.getCurrent_sentiment_score());
            messageService.save(saved);
            log.debug("[Process] Updated message sentiment: {} ({})",
                    bundle.getCurrent_sentiment_label(), bundle.getCurrent_sentiment_score());
        }

        // 8. Build suggestions (only for customer messages, not agent messages)
        log.debug("[Process] Step 7: Building suggestions...");
        List<SuggestedResponse> suggestions;
        List<KnowledgeSource> knowledgeSources;
        int documentsFound = 0;
        boolean usedKnowledgeBase = false;

        // Skip suggestions for agent messages - only process customer messages
        if (!isCustomer) {
            log.info("[Process] Skipping suggestions for agent message");
            suggestions = Collections.emptyList();
            knowledgeSources = Collections.emptyList();
        } else if (currentMessageContext.wasIntentFiltered()) {
            // Intent was detected but filtered out due to project mismatch
            // Provide helpful message instead of going to RAG
            log.info("[Process] Intent {} was filtered for project {}, providing helpful message",
                    currentMessageContext.filteredIntent(), projectName);
            String filteredIntentMessage =
                    intentRegistryService.filteredMessage(currentMessageContext.filteredIntent().name(), projectName);
            SuggestedResponse sr = new SuggestedResponse();
            sr.setEnglishReply(filteredIntentMessage);
            if (!detectedLang.equalsIgnoreCase("en")) {
                sr.setUserLanguageReply(translationService.fromEnglish(filteredIntentMessage, detectedLang));
            }
            suggestions = Collections.singletonList(sr);
            knowledgeSources = Collections.emptyList();
        } else if (useChecklistForThisMessage && checklistContext != null && checklistContext.hasContext()) {
            // CUSTOMER-SPECIFIC QUERY (POLICY/CLAIMS/FEE_WAIVER/HOME_LOAN_CLOSURE with Salesforce data)
            // KNOWLEDGE BASE FIRST: when the FAQ has a document matching this question,
            // answer from the document. The checklist answer (GPT + Salesforce data) is the
            // fallback for when the knowledge base has nothing relevant.
            boolean isSimpleMessage = isGreetingOrSimpleMessage(english);

            // A billing question can only be answered from the customer's own record - no
            // FAQ document knows their balance - so the knowledge base never overrides it.
            // Documents are still fetched for the knowledge-source chips.
            boolean customerDataOnly = checklistContext.operationType() == OperationType.BILLING;

            // Checklist answer - built up front so it is ready as the fallback
            List<SuggestedResponse> checklistSuggestions = toSuggestedResponses(bundle.getSuggestions(), detectedLang);

            if (analysisService.isRagEnabled() && !isSimpleMessage) {
                var ragResult = analysisService.buildReplySuggestionsWithRag(all, policyContext, projectName);
                documentsFound = ragResult.documentsFound();
                knowledgeSources = ragResult.knowledgeSources();
                usedKnowledgeBase = ragResult.usedKnowledgeBase();

                if (!customerDataOnly && documentsFound > 0 && !ragResult.suggestions().isEmpty()) {
                    suggestions = ragResult.suggestions();
                    log.info("[Process] Using KNOWLEDGE BASE suggestion for {} ({} documents matched)",
                            checklistContext.operationType(), documentsFound);
                } else {
                    suggestions = checklistSuggestions;
                    log.info("[Process] Knowledge base found nothing for {}, using CHECKLIST suggestion",
                            checklistContext.operationType());
                }
            } else {
                suggestions = checklistSuggestions;
                knowledgeSources = Collections.emptyList();
                log.info("[Process] Using CHECKLIST suggestion for {} (knowledge base skipped)",
                        checklistContext.operationType());
            }
        } else {
            // GENERAL QUERY - Always use RAG for knowledge base lookup
            // Even if we have cached Salesforce data, RAG should be called for general questions
            // Skip RAG for greetings and simple messages - no need for knowledge base lookup
            boolean isSimpleMessage = isGreetingOrSimpleMessage(english);

            if (analysisService.isRagEnabled() && !isSimpleMessage) {
                // Use RAG for suggestions with knowledge base context + policy data
                log.info("[Process] Using RAG for suggestions with {} messages, projectName: {}...", all.size(), projectName);
                var ragResult = analysisService.buildReplySuggestionsWithRag(all, policyContext, projectName);
                suggestions = ragResult.suggestions();
                knowledgeSources = ragResult.knowledgeSources();
                documentsFound = ragResult.documentsFound();
                usedKnowledgeBase = ragResult.usedKnowledgeBase();
                log.info("[Process] RAG returned {} suggestions, {} knowledge sources",
                        suggestions.size(), knowledgeSources.size());

                // Knowledge base found nothing usable. Only fall back to the AI answer when
                // there is real customer data behind it - otherwise the model has nothing to
                // ground on and will invent figures (it quoted a 10% discount for a document
                // that says 25%). An honest "I don't know" beats a confident wrong number.
                if (documentsFound == 0 || suggestions.isEmpty()) {
                    boolean hasCustomerData = checklistContext != null && checklistContext.hasContext();
                    if (hasCustomerData && !bundle.getSuggestions().isEmpty()) {
                        log.info("[Process] Knowledge base found nothing, using AI suggestions grounded in customer data");
                        suggestions = toSuggestedResponses(bundle.getSuggestions(), detectedLang);
                    } else {
                        log.warn("[Process] Knowledge base found nothing and no customer data - returning no-information reply instead of an ungrounded answer");
                        suggestions = toSuggestedResponses(List.of(
                                promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of())),
                                detectedLang);
                    }
                }
            } else if (isSimpleMessage) {
                // Skip RAG for greetings - use AI suggestions directly
                log.info("[Process] Simple message detected, skipping RAG...");
                suggestions = toSuggestedResponses(bundle.getSuggestions(), detectedLang);
                knowledgeSources = Collections.emptyList();
                log.info("[Process] Generated {} suggestions (skipped RAG for simple message)", suggestions.size());
            } else {
                // Fallback to AI-based suggestions without RAG
                log.info("[Process] RAG disabled, using AI for suggestions...");
                suggestions = toSuggestedResponses(bundle.getSuggestions(), detectedLang);
                knowledgeSources = Collections.emptyList();
                log.info("[Process] Generated {} suggestions (no RAG)", suggestions.size());
            }
        }

        // 9. Build response with policy data and checklist info
        // Get policyData from checklistContext if available
        CustomerPolicyData effectivePolicyData = policyData;
        if (effectivePolicyData == null && checklistContext != null && checklistContext.policyData() != null) {
            effectivePolicyData = checklistContext.policyData();
        }

        String customerName = null;
        if (checklistContext != null && checklistContext.creditCardData() != null) {
            customerName = checklistContext.creditCardData().getCustomerName();
        } else if (checklistContext != null && checklistContext.homeLoanData() != null) {
            customerName = checklistContext.homeLoanData().getCustomerName();
        } else if (checklistContext != null && checklistContext.policyData() != null) {
            customerName = checklistContext.policyData().getCustomerName();
        } else if (checklistContext != null && checklistContext.billingData() != null) {
            customerName = checklistContext.billingData().getCustomerName();
        } else if (effectivePolicyData != null) {
            customerName = effectivePolicyData.getCustomerName();
        }

        int creditCardsFound = checklistContext != null && checklistContext.creditCardData() != null
                && checklistContext.creditCardData().getCreditCards() != null
                ? checklistContext.creditCardData().getCreditCards().size() : 0;

        int homeLoansFound = checklistContext != null && checklistContext.homeLoanData() != null
                && checklistContext.homeLoanData().getHomeLoans() != null
                ? checklistContext.homeLoanData().getHomeLoans().size() : 0;

        int policiesFound = effectivePolicyData != null && effectivePolicyData.getPolicies() != null
                ? effectivePolicyData.getPolicies().size() : 0;

        int claimsFound = effectivePolicyData != null && effectivePolicyData.getClaims() != null
                ? effectivePolicyData.getClaims().size() : 0;

        ConversationResponse resp = ConversationResponse.builder()
                .overallSentiment(bundle.getOverall_sentiment_score())
                .currentSentiment(bundle.getCurrent_sentiment_score())
                .summary(bundle.getSummary())
                .suggestedResponses(suggestions)
                .knowledgeSources(knowledgeSources)
                .documentsFound(documentsFound)
                .usedKnowledgeBase(usedKnowledgeBase)
                .usedPolicyData(effectivePolicyData != null)
                .customerName(customerName)
                .policiesFound(policiesFound)
                .claimsFound(claimsFound)
                .usedChecklist(checklistContext != null && checklistContext.hasContext())
                .checklistOperation(checklistContext != null && checklistContext.operationType() != OperationType.NONE
                        ? checklistContext.operationType().name() : null)
                .creditCardsFound(creditCardsFound)
                .homeLoansFound(homeLoansFound)
                .projectName(projectName)
                .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Process] Message processing completed in {}ms", duration);

        return resp;
    }

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
