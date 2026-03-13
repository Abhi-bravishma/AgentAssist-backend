package com.agentassist.service.processing;

import com.agentassist.dto.responseDTO.AiAnalysisBundle;
import com.agentassist.dto.responseDTO.KnowledgeSource;
import com.agentassist.dto.responseDTO.OneShotResponse;
import com.agentassist.dto.responseDTO.SuggestedResponse;
import com.agentassist.model.MessageEntity;
import com.agentassist.model.SenderType;
import com.agentassist.service.analysis.AnalysisService;
import com.agentassist.service.conversation.ConversationService;
import com.agentassist.service.conversation.MessageService;
import com.agentassist.service.translation.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationProcessingService {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final TranslationService translationService;
    private final AnalysisService analysisService;

    @Transactional
    public OneShotResponse processMessage(String interactionId, String from, String messageText) {
        log.info("[Process] Processing message - interactionId: {}, from: {}, length: {}",
                interactionId, from, messageText.length());
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

        // 5. Build english conversation
        log.debug("[Process] Step 5: Building conversation history...");
        List<MessageEntity> all = messageService.fetchByInteraction(interactionId);
        List<String> englishConversation = all.stream().map(MessageEntity::getEnglishText).toList();
        log.info("[Process] Conversation has {} messages", englishConversation.size());

        // 6. Call AI
        log.debug("[Process] Step 6: Calling AI for analysis...");
        AiAnalysisBundle bundle = analysisService.analyzeConversation(englishConversation, english);
        log.info("[Process] AI analysis complete - overall: {}, current: {}",
                bundle.getOverall_sentiment_score(), bundle.getCurrent_sentiment_score());

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
        } else {
            // Skip RAG for greetings and simple messages - no need for knowledge base lookup
            boolean isSimpleMessage = isGreetingOrSimpleMessage(english);

            if (analysisService.isRagEnabled() && !isSimpleMessage) {
                // Use RAG for suggestions with knowledge base context
                // Pass the already-fetched messages to avoid transaction isolation issues
                log.info("[Process] Using RAG for suggestions with {} messages...", all.size());
                var ragResult = analysisService.buildReplySuggestionsWithRag(all);
                suggestions = ragResult.suggestions();
                knowledgeSources = ragResult.knowledgeSources();
                documentsFound = ragResult.documentsFound();
                usedKnowledgeBase = ragResult.usedKnowledgeBase();
                log.info("[Process] RAG returned {} suggestions, {} knowledge sources",
                        suggestions.size(), knowledgeSources.size());
            } else if (isSimpleMessage) {
                // Skip RAG for greetings - use AI suggestions directly
                log.info("[Process] Simple message detected, skipping RAG...");
                boolean isEnglishUser = detectedLang.equalsIgnoreCase("en");

                suggestions = bundle.getSuggestions().stream()
                        .map(s -> {
                            SuggestedResponse sr = new SuggestedResponse();
                            sr.setEnglishReply(s);
                            if (!isEnglishUser) {
                                sr.setUserLanguageReply(translationService.fromEnglish(s, detectedLang));
                            }
                            return sr;
                        }).toList();
                knowledgeSources = Collections.emptyList();
                log.info("[Process] Generated {} suggestions (skipped RAG for simple message)", suggestions.size());
            } else {
                // Fallback to AI-based suggestions without RAG
                log.info("[Process] RAG disabled, using AI for suggestions...");
                boolean isEnglishUser = detectedLang.equalsIgnoreCase("en");

                suggestions = bundle.getSuggestions().stream()
                        .map(s -> {
                            SuggestedResponse sr = new SuggestedResponse();
                            sr.setEnglishReply(s);
                            if (!isEnglishUser) {
                                sr.setUserLanguageReply(translationService.fromEnglish(s, detectedLang));
                            }
                            return sr;
                        }).toList();
                knowledgeSources = Collections.emptyList();
                log.info("[Process] Generated {} suggestions (no RAG)", suggestions.size());
            }
        }

        // 9. Build response
        OneShotResponse resp = OneShotResponse.builder()
                .overallSentiment(bundle.getOverall_sentiment_score())
                .currentSentiment(bundle.getCurrent_sentiment_score())
                .summary(bundle.getSummary())
                .suggestedResponses(suggestions)
                .knowledgeSources(knowledgeSources)
                .documentsFound(documentsFound)
                .usedKnowledgeBase(usedKnowledgeBase)
                .build();

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Process] Message processing completed in {}ms", duration);

        return resp;
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
