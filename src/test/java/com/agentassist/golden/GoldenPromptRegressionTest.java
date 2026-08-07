package com.agentassist.golden;

import com.agentassist.ai.BaseAiProvider;
import com.agentassist.configregistry.ConfigRegistryTestBase;
import com.agentassist.configregistry.PromptService;
import com.agentassist.service.checklist.ChecklistService.OperationType;
import com.agentassist.service.processing.ConversationProcessingService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden-prompt safety net for the Part 1 config-registry restructure.
 *
 * <p>Every prompt the application can send to an LLM is captured here as a
 * fixture file under {@code src/test/resources/golden}. On first run (fixture
 * absent) the rendered prompt is WRITTEN; on every later run it is COMPARED
 * byte-for-byte. The fixtures were generated from the pre-restructure code, so
 * as the prompt source moves from Java constants to the DB registry, any
 * wording drift — including whitespace and {@code %%} handling — fails a test.
 *
 * <p>No LLM is ever called: {@link BaseAiProvider#call} is overridden to
 * capture the outgoing prompt and return a canned parseable response.
 *
 * <p>Since commit 6 the provider resolves its prompts from the registry, so
 * this test now runs against H2 + the real Liquibase seed — making it the
 * END-TO-END gate: provider method → registry template → rendered prompt →
 * fixture bytes.
 */
class GoldenPromptRegressionTest extends ConfigRegistryTestBase {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    // ==================== capture plumbing ====================

    /** Records every prompt instead of calling a model. */
    private static final class CapturingProvider extends BaseAiProvider {
        private final List<String> prompts = new ArrayList<>();
        private String cannedResponse = "{}";

        CapturingProvider(PromptService promptService) {
            super(null, "golden-capture", promptService);
        }

        @Override
        protected String call(String promptText) {
            prompts.add(promptText);
            return cannedResponse;
        }

        String lastPrompt() {
            if (prompts.isEmpty()) {
                throw new IllegalStateException("no prompt was captured");
            }
            return prompts.get(prompts.size() - 1);
        }
    }

    private static void compareOrCapture(String fixtureName, String actual) {
        try {
            Path file = GOLDEN_DIR.resolve(fixtureName + ".txt");
            if (Files.notExists(file)) {
                Files.createDirectories(file.getParent());
                Files.write(file, actual.getBytes(StandardCharsets.UTF_8));
                System.out.println("[golden] CAPTURED " + file);
                return;
            }
            String expected = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertEquals(expected, actual,
                    "Prompt drifted from golden fixture: " + fixtureName);
        } catch (java.io.IOException e) {
            throw new RuntimeException("fixture I/O failed for " + fixtureName, e);
        }
    }

    // ==================== BaseAiProvider prompts ====================

    @Test
    void analyzeText() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeText(GoldenFixtureInputs.TEXT);
        compareOrCapture("ai.analyze_text", p.lastPrompt());
    }

    @Test
    void analyzeConversation() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeConversation(GoldenFixtureInputs.CONVERSATION, GoldenFixtureInputs.LATEST_MESSAGE);
        compareOrCapture("ai.analyze_conversation", p.lastPrompt());
    }

    @Test
    void analyzeConversationWithContext_withContext() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeConversationWithContext(GoldenFixtureInputs.CONVERSATION,
                GoldenFixtureInputs.LATEST_MESSAGE, GoldenFixtureInputs.POLICY_CONTEXT);
        compareOrCapture("ai.analyze_conversation_with_context__with_context", p.lastPrompt());
    }

    @Test
    void analyzeConversationWithContext_noContext() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeConversationWithContext(GoldenFixtureInputs.CONVERSATION,
                GoldenFixtureInputs.LATEST_MESSAGE, null);
        compareOrCapture("ai.analyze_conversation_with_context__no_context", p.lastPrompt());
    }

    @Test
    void analyzeConversationWithChecklist() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeConversationWithChecklist(GoldenFixtureInputs.CONVERSATION,
                GoldenFixtureInputs.LATEST_MESSAGE, GoldenFixtureInputs.CHECKLIST_CONTEXT,
                GoldenFixtureInputs.OPERATION_TYPE);
        compareOrCapture("ai.analyze_conversation_with_checklist", p.lastPrompt());
    }

    @Test
    void translateToEnglish() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "ok";
        p.translateToEnglish(GoldenFixtureInputs.TEXT);
        compareOrCapture("ai.translate_to_english", p.lastPrompt());
    }

    @Test
    void translateFromEnglish_describedLanguage() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "ok";
        p.translateFromEnglish(GoldenFixtureInputs.TEXT, "zh-Hant");
        compareOrCapture("ai.translate_from_english__zh-Hant", p.lastPrompt());
    }

    @Test
    void translateFromEnglish_rawTag() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "ok";
        p.translateFromEnglish(GoldenFixtureInputs.TEXT, "fr");
        compareOrCapture("ai.translate_from_english__fr", p.lastPrompt());
    }

    @Test
    void detectLanguage() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "en";
        p.detectLanguage(GoldenFixtureInputs.TEXT);
        compareOrCapture("ai.detect_language", p.lastPrompt());
    }

    @Test
    void detectOperation() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "GENERAL";
        p.detectOperationType(GoldenFixtureInputs.CONVERSATION, GoldenFixtureInputs.LATEST_MESSAGE);
        compareOrCapture("ai.detect_operation", p.lastPrompt());
    }

    @Test
    void overallSentiment() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.cannedResponse = "{\"overall_sentiment_score\": 0.1}";
        p.computeOverallSentiment(GoldenFixtureInputs.CONVERSATION);
        compareOrCapture("ai.overall_sentiment", p.lastPrompt());
    }

    @Test
    void regenerateSuggestions() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.regenerateSuggestions(GoldenFixtureInputs.CONVERSATION,
                GoldenFixtureInputs.LATEST_MESSAGE, GoldenFixtureInputs.PREVIOUS_SUGGESTION);
        compareOrCapture("ai.regenerate_suggestions", p.lastPrompt());
    }

    @Test
    void regenerateSuggestionsWithContext() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.regenerateSuggestionsWithContext(GoldenFixtureInputs.CONVERSATION,
                GoldenFixtureInputs.LATEST_MESSAGE, GoldenFixtureInputs.PREVIOUS_SUGGESTION,
                GoldenFixtureInputs.CHECKLIST_CONTEXT, GoldenFixtureInputs.CUSTOMER_NAME);
        compareOrCapture("ai.regenerate_suggestions_with_context", p.lastPrompt());
    }

    @Test
    void followUpCheck() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeFollowUpRequirement(GoldenFixtureInputs.TRANSCRIPT, GoldenFixtureInputs.CUSTOMER_NAME);
        compareOrCapture("ai.follow_up_check", p.lastPrompt());
    }

    @Test
    void compliance() {
        CapturingProvider p = new CapturingProvider(promptService);
        p.analyzeCompliance(GoldenFixtureInputs.AGENT_MESSAGES, GoldenFixtureInputs.INTERACTION_ID);
        compareOrCapture("ai.compliance", p.lastPrompt());
    }

    // ==================== ChecklistService checklists ====================
    // Moved to ChecklistServiceGoldenTest (DB-backed): the rewired service
    // resolves its checklists from the registry, so the golden comparison now
    // runs through H2 + Liquibase with the real ChecklistService.

    // ==================== ConversationProcessingService ====================

    private ConversationProcessingService processingService() {
        return new ConversationProcessingService(null, null, null, null, null, null, null, null);
    }

    @Test
    void noKnowledgeReply() throws Exception {
        Field f = ConversationProcessingService.class.getDeclaredField("NO_KNOWLEDGE_REPLY");
        f.setAccessible(true);
        compareOrCapture("system.no_knowledge_reply", (String) f.get(null));
    }

    @Test
    void filteredIntentMessages() throws Exception {
        Method m = ConversationProcessingService.class.getDeclaredMethod(
                "getFilteredIntentMessage", OperationType.class, String.class);
        m.setAccessible(true);
        ConversationProcessingService svc = processingService();

        for (OperationType intent : new OperationType[]{
                OperationType.POLICY, OperationType.CLAIMS, OperationType.FEE_WAIVER,
                OperationType.HOME_LOAN_CLOSURE, OperationType.BILLING, OperationType.TELCO}) {
            compareOrCapture("filtered." + intent.name() + "__HOSPITALITY",
                    (String) m.invoke(svc, intent, "HOSPITALITY"));
            compareOrCapture("filtered." + intent.name() + "__null",
                    (String) m.invoke(svc, intent, (String) null));
        }
    }
}
