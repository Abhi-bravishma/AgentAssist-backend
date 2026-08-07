package com.agentassist.configregistry;

import com.agentassist.golden.GoldenFixtureInputs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE Part 1 acceptance gate: renders every SEEDED template from the H2
 * registry with the same variable composition the rewired callers use, and
 * asserts byte-identity with the golden fixtures captured from the
 * pre-restructure code. If this passes, the DB rows ARE the old prompts.
 */
class RegistryGoldenPromptTest extends ConfigRegistryTestBase {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    private static String fixture(String name) {
        try {
            return new String(Files.readAllBytes(GOLDEN_DIR.resolve(name + ".txt")),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("missing golden fixture: " + name, e);
        }
    }

    /** Numbered list exactly as the BaseAiProvider loops build it. */
    private static String numbered(List<String> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append(i + 1).append(") ").append(messages.get(i)).append("\n");
        }
        return sb.toString();
    }

    /** Newline-joined transcript exactly as analyzeFollowUpRequirement builds it. */
    private static String joined(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private void assertGolden(String fixtureName, String actual) {
        assertEquals(fixture(fixtureName), actual,
                "seeded template drifted from golden fixture: " + fixtureName);
    }

    // ==================== ai.* ====================

    @Test
    void analyzeText() {
        assertGolden("ai.analyze_text", promptService.renderDefault(
                TemplateKeys.AI_ANALYZE_TEXT, Map.of("text", GoldenFixtureInputs.TEXT)));
    }

    @Test
    void analyzeConversation() {
        assertGolden("ai.analyze_conversation", promptService.renderDefault(
                TemplateKeys.AI_ANALYZE_CONVERSATION, Map.of(
                        "latest_message", GoldenFixtureInputs.LATEST_MESSAGE,
                        "message_count", String.valueOf(GoldenFixtureInputs.CONVERSATION.size()),
                        "conversation", numbered(GoldenFixtureInputs.CONVERSATION))));
    }

    @Test
    void analyzeConversationWithContext_bothVariants() {
        Map<String, String> base = Map.of(
                "latest_message", GoldenFixtureInputs.LATEST_MESSAGE,
                "message_count", String.valueOf(GoldenFixtureInputs.CONVERSATION.size()),
                "conversation", numbered(GoldenFixtureInputs.CONVERSATION));

        Map<String, String> withContext = new HashMap<>(base);
        withContext.put("context_section",
                "CUSTOMER DATA:\n" + GoldenFixtureInputs.POLICY_CONTEXT + "\n");
        assertGolden("ai.analyze_conversation_with_context__with_context",
                promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION_WITH_CONTEXT, withContext));

        Map<String, String> noContext = new HashMap<>(base);
        noContext.put("context_section", "");
        assertGolden("ai.analyze_conversation_with_context__no_context",
                promptService.renderDefault(TemplateKeys.AI_ANALYZE_CONVERSATION_WITH_CONTEXT, noContext));
    }

    @Test
    void analyzeConversationWithChecklist() {
        assertGolden("ai.analyze_conversation_with_checklist", promptService.renderDefault(
                TemplateKeys.AI_ANALYZE_CONVERSATION_WITH_CHECKLIST, Map.of(
                        "latest_message", GoldenFixtureInputs.LATEST_MESSAGE,
                        "checklist_context", GoldenFixtureInputs.CHECKLIST_CONTEXT,
                        "message_count", String.valueOf(GoldenFixtureInputs.CONVERSATION.size()),
                        "conversation", numbered(GoldenFixtureInputs.CONVERSATION))));
    }

    @Test
    void translateToEnglish() {
        assertGolden("ai.translate_to_english", promptService.renderDefault(
                TemplateKeys.AI_TRANSLATE_TO_ENGLISH, Map.of("text", GoldenFixtureInputs.TEXT)));
    }

    @Test
    void translateFromEnglish_bothVariants() {
        // Described language (what describeLanguage produces for zh-Hant)
        assertGolden("ai.translate_from_english__zh-Hant", promptService.renderDefault(
                TemplateKeys.AI_TRANSLATE_FROM_ENGLISH, Map.of(
                        "target_language",
                        "Traditional Chinese (繁體中文), using Traditional characters only",
                        "text", GoldenFixtureInputs.TEXT)));
        // Raw tag passthrough
        assertGolden("ai.translate_from_english__fr", promptService.renderDefault(
                TemplateKeys.AI_TRANSLATE_FROM_ENGLISH, Map.of(
                        "target_language", "fr",
                        "text", GoldenFixtureInputs.TEXT)));
    }

    @Test
    void detectLanguage() {
        assertGolden("ai.detect_language", promptService.renderDefault(
                TemplateKeys.AI_DETECT_LANGUAGE, Map.of("text", GoldenFixtureInputs.TEXT)));
    }

    @Test
    void detectOperation() {
        assertGolden("ai.detect_operation", promptService.renderDefault(
                TemplateKeys.AI_DETECT_OPERATION,
                Map.of("latest_message", GoldenFixtureInputs.LATEST_MESSAGE)));
    }

    @Test
    void overallSentiment() {
        assertGolden("ai.overall_sentiment", promptService.renderDefault(
                TemplateKeys.AI_OVERALL_SENTIMENT,
                Map.of("conversation", numbered(GoldenFixtureInputs.CONVERSATION))));
    }

    @Test
    void regenerateSuggestions() {
        assertGolden("ai.regenerate_suggestions", promptService.renderDefault(
                TemplateKeys.AI_REGENERATE_SUGGESTIONS, Map.of(
                        "previous_suggestion", GoldenFixtureInputs.PREVIOUS_SUGGESTION,
                        "conversation", numbered(GoldenFixtureInputs.CONVERSATION),
                        "latest_message", GoldenFixtureInputs.LATEST_MESSAGE)));
    }

    @Test
    void regenerateSuggestionsWithContext() {
        assertGolden("ai.regenerate_suggestions_with_context", promptService.renderDefault(
                TemplateKeys.AI_REGENERATE_SUGGESTIONS_WITH_CONTEXT, Map.of(
                        "checklist_context", GoldenFixtureInputs.CHECKLIST_CONTEXT,
                        "previous_suggestion", GoldenFixtureInputs.PREVIOUS_SUGGESTION,
                        "customer_name", GoldenFixtureInputs.CUSTOMER_NAME,
                        "conversation", numbered(GoldenFixtureInputs.CONVERSATION),
                        "latest_message", GoldenFixtureInputs.LATEST_MESSAGE)));
    }

    @Test
    void followUpCheck() {
        assertGolden("ai.follow_up_check", promptService.renderDefault(
                TemplateKeys.AI_FOLLOW_UP_CHECK, Map.of(
                        "transcript", joined(GoldenFixtureInputs.TRANSCRIPT),
                        "customer_name", GoldenFixtureInputs.CUSTOMER_NAME)));
    }

    @Test
    void compliance() {
        assertGolden("ai.compliance", promptService.renderDefault(
                TemplateKeys.AI_COMPLIANCE,
                Map.of("agent_messages", numbered(GoldenFixtureInputs.AGENT_MESSAGES))));
    }

    // ==================== checklists ====================

    /** Brand vars exactly as the rewired ChecklistService composes them. */
    private Map<String, String> brandVars(String project) {
        String bankName = brandService.attr(project, BrandService.BANK_NAME);
        return Map.of(
                "bank_name", bankName,
                "bank_name_upper", bankName.toUpperCase(),
                "hotline", brandService.attr(project, BrandService.HOTLINE),
                "loan_email", brandService.attr(project, BrandService.LOAN_EMAIL));
    }

    @Test
    void checklistFeeWaiver_allProjects() {
        assertGolden("checklist.fee_waiver__default",
                promptService.render(TemplateKeys.CHECKLIST_FEE_WAIVER, null, brandVars(null)));
        assertGolden("checklist.fee_waiver__METRO",
                promptService.render(TemplateKeys.CHECKLIST_FEE_WAIVER, "METRO", brandVars("METRO")));
        // SCB resolves the project OVERRIDE row (extra brand vars are ignored)
        assertGolden("checklist.fee_waiver__SCB",
                promptService.render(TemplateKeys.CHECKLIST_FEE_WAIVER, "SCB", brandVars("SCB")));
        // Unknown project: default template + bank_name = the project code (§4.6)
        assertGolden("checklist.fee_waiver__HOSPITALITY",
                promptService.render(TemplateKeys.CHECKLIST_FEE_WAIVER, "HOSPITALITY", brandVars("HOSPITALITY")));
    }

    @Test
    void checklistHomeLoanClosure_allProjects() {
        assertGolden("checklist.home_loan_closure__default",
                promptService.render(TemplateKeys.CHECKLIST_HOME_LOAN_CLOSURE, null, brandVars(null)));
        assertGolden("checklist.home_loan_closure__METRO",
                promptService.render(TemplateKeys.CHECKLIST_HOME_LOAN_CLOSURE, "METRO", brandVars("METRO")));
        assertGolden("checklist.home_loan_closure__HOSPITALITY",
                promptService.render(TemplateKeys.CHECKLIST_HOME_LOAN_CLOSURE, "HOSPITALITY", brandVars("HOSPITALITY")));
    }

    @Test
    void projectIndependentChecklists() {
        assertGolden("checklist.billing",
                promptService.render(TemplateKeys.CHECKLIST_BILLING, "METRO", brandVars("METRO")));
        assertGolden("checklist.policy",
                promptService.render(TemplateKeys.CHECKLIST_POLICY, null, brandVars(null)));
        assertGolden("checklist.claims",
                promptService.render(TemplateKeys.CHECKLIST_CLAIMS, null, brandVars(null)));
        assertGolden("checklist.telco",
                promptService.render(TemplateKeys.CHECKLIST_TELCO, null, brandVars(null)));
    }

    // ==================== filtered messages + system ====================

    @Test
    void filteredIntentMessages() {
        for (String intent : new String[]{"POLICY", "CLAIMS", "FEE_WAIVER",
                "HOME_LOAN_CLOSURE", "BILLING", "TELCO"}) {
            assertGolden("filtered." + intent + "__HOSPITALITY",
                    intentRegistryService.filteredMessage(intent, "HOSPITALITY"));
            assertGolden("filtered." + intent + "__null",
                    intentRegistryService.filteredMessage(intent, null));
        }
    }

    @Test
    void noKnowledgeReply() {
        assertGolden("system.no_knowledge_reply",
                promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of()));
    }
}
