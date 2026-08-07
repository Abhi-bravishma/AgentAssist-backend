package com.agentassist.rag;

import com.agentassist.configregistry.ConfigRegistryTestBase;
import com.agentassist.configregistry.TemplateKeys;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rag.suggestions template (verbatim from bravishma-rag) renders from the
 * seeded registry. Critical detail: the prompt PROSE contains literal
 * {Customer Name} and {credit cards count} — the renderer must substitute only
 * the four snake_case placeholders and leave those untouched, not flag them as
 * missing variables.
 */
class RagSuggestionPromptTest extends ConfigRegistryTestBase {

    @Test
    void rendersWithExactlyTheFourVariables() {
        String prompt = promptService.renderDefault(TemplateKeys.RAG_SUGGESTIONS, Map.of(
                "conversation_history", "Customer: I want to waive my annual fee",
                "latest_message", "My card ends in 1116",
                "context", "No relevant documents found.",
                "suggestion_count", "1"));

        assertTrue(prompt.contains("CONVERSATION HISTORY:\nCustomer: I want to waive my annual fee"), prompt);
        assertTrue(prompt.contains("LATEST CUSTOMER MESSAGE:\nMy card ends in 1116"), prompt);
        assertTrue(prompt.contains("Generate exactly 1 reply suggestion"), prompt);
        // Literal prose braces survive rendering untouched
        assertTrue(prompt.contains("\"Hi {Customer Name},\""), prompt);
        assertTrue(prompt.contains("{credit cards count}"), prompt);
        // No unresolved snake_case placeholder remains
        assertFalse(prompt.contains("{conversation_history}"), prompt);
        assertFalse(prompt.contains("{latest_message}"), prompt);
        assertFalse(prompt.contains("{context}"), prompt);
        assertFalse(prompt.contains("{suggestion_count}"), prompt);
    }

    @Test
    void suggestionParsingMatchesUpstreamBehaviour() {
        AgentAssistSuggestionService service =
                new AgentAssistSuggestionService(null, null, null, null);

        // Numbered response -> extracted without the prefix
        List<String> parsed = service.parseSuggestions(
                "1. Hi Maria, your Rewards card (1116) qualifies for the annual fee waiver.", 1);
        assertEquals(1, parsed.size());
        assertEquals("Hi Maria, your Rewards card (1116) qualifies for the annual fee waiver.",
                parsed.get(0));

        // Continuation lines are folded into the suggestion
        parsed = service.parseSuggestions(
                "1) Hi Maria,\nyour card qualifies.\n\n2) Second option here.", 2);
        assertEquals(List.of("Hi Maria, your card qualifies.", "Second option here."), parsed);

        // Unnumbered response -> whole text as a single suggestion
        parsed = service.parseSuggestions("Hi Maria, you are eligible.", 1);
        assertEquals(List.of("Hi Maria, you are eligible."), parsed);

        // Blank -> empty
        assertTrue(service.parseSuggestions("   ", 1).isEmpty());
    }
}
