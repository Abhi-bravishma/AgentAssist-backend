package com.agentassist.service.checklist;

import com.agentassist.ai.AiProvider;
import com.agentassist.ai.AiProviderFactory;
import com.agentassist.configregistry.ConfigRegistryTestBase;
import com.agentassist.configregistry.IntentCodes;
import com.agentassist.configregistry.entity.AaIntent;
import com.agentassist.configregistry.repository.AaIntentRepository;
import com.agentassist.service.checklist.ChecklistService.ChecklistContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Golden gate for the REWIRED ChecklistService: getChecklistPrompt now goes
 * through PromptService/BrandService, and every rendering must still be
 * byte-identical to the fixtures captured from the pre-restructure code.
 * Replaces the checklist coverage that lived in GoldenPromptRegressionTest
 * while the checklists were Java constants.
 *
 * <p>Part 2c: operations are plain String codes (the OperationType enum is
 * gone), so this class also proves the payoff — a registry-added intent passes
 * through detection and dispatch without any Java change.
 */
class ChecklistServiceGoldenTest extends ConfigRegistryTestBase {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    @Autowired
    private AaIntentRepository intentRepository;

    private ChecklistService service() {
        // Salesforce and AI provider are untouched by getChecklistPrompt
        return new ChecklistService(null, null, promptService, brandService, intentRegistryService);
    }

    /** Service whose classifier is canned to return {@code aiResult}. */
    private ChecklistService serviceDetecting(String aiResult) {
        AiProvider provider = mock(AiProvider.class);
        when(provider.detectOperationType(anyList(), any())).thenReturn(aiResult);
        AiProviderFactory factory = mock(AiProviderFactory.class);
        when(factory.active()).thenReturn(provider);
        return new ChecklistService(null, factory, promptService, brandService, intentRegistryService);
    }

    private void assertGolden(String fixtureName, String actual) {
        try {
            String expected = new String(
                    Files.readAllBytes(GOLDEN_DIR.resolve(fixtureName + ".txt")),
                    StandardCharsets.UTF_8);
            assertEquals(expected, actual,
                    "checklist drifted from golden fixture: " + fixtureName);
        } catch (IOException e) {
            throw new UncheckedIOException("missing golden fixture: " + fixtureName, e);
        }
    }

    @Test
    void checklistFeeWaiver() {
        ChecklistService s = service();
        assertGolden("checklist.fee_waiver__default",
                s.getChecklistPrompt(IntentCodes.FEE_WAIVER, null));
        assertGolden("checklist.fee_waiver__METRO",
                s.getChecklistPrompt(IntentCodes.FEE_WAIVER, "METRO"));
        assertGolden("checklist.fee_waiver__SCB",
                s.getChecklistPrompt(IntentCodes.FEE_WAIVER, "SCB"));
        // Unknown project: bank_name falls back to the PROJECT CODE ITSELF,
        // hotline to the neutral default (plan §4.6).
        assertGolden("checklist.fee_waiver__HOSPITALITY",
                s.getChecklistPrompt(IntentCodes.FEE_WAIVER, "HOSPITALITY"));
    }

    @Test
    void checklistHomeLoanClosure() {
        ChecklistService s = service();
        assertGolden("checklist.home_loan_closure__default",
                s.getChecklistPrompt(IntentCodes.HOME_LOAN_CLOSURE, null));
        assertGolden("checklist.home_loan_closure__METRO",
                s.getChecklistPrompt(IntentCodes.HOME_LOAN_CLOSURE, "METRO"));
        assertGolden("checklist.home_loan_closure__HOSPITALITY",
                s.getChecklistPrompt(IntentCodes.HOME_LOAN_CLOSURE, "HOSPITALITY"));
    }

    @Test
    void projectIndependentChecklists() {
        ChecklistService s = service();
        assertGolden("checklist.billing", s.getChecklistPrompt(IntentCodes.BILLING, "METRO"));
        assertGolden("checklist.policy", s.getChecklistPrompt(IntentCodes.POLICY, null));
        assertGolden("checklist.claims", s.getChecklistPrompt(IntentCodes.CLAIMS, null));
        assertGolden("checklist.telco", s.getChecklistPrompt(IntentCodes.TELCO, null));
    }

    @Test
    void noneReturnsEmptyWithoutTouchingTheRegistry() {
        assertEquals("", service().getChecklistPrompt(IntentCodes.NONE, null));
    }

    @Test
    void isOperationValidForProjectDelegatesToRegistry() {
        ChecklistService s = service();
        assertTrue(s.isOperationValidForProject(IntentCodes.FEE_WAIVER, "METRO"));
        assertFalse(s.isOperationValidForProject(IntentCodes.POLICY, "METRO"));
        assertTrue(s.isOperationValidForProject(IntentCodes.POLICY, "ALLIANZ"));
        assertFalse(s.isOperationValidForProject(IntentCodes.TELCO, "SCB"));
        assertTrue(s.isOperationValidForProject(IntentCodes.NONE, "METRO"));
        assertTrue(s.isOperationValidForProject(IntentCodes.POLICY, "HOSPITALITY"));
        assertTrue(s.isOperationValidForProject(IntentCodes.POLICY, null));
    }

    // ==================== Part 2c: string-code passthrough ====================

    @Test
    void detectOperationMapsGeneralToNoneAndPassesCodesThrough() {
        assertEquals(IntentCodes.NONE,
                serviceDetecting("GENERAL").detectOperation(List.of("hi")));
        assertEquals(IntentCodes.TELCO,
                serviceDetecting("TELCO").detectOperation(List.of("my data pack")));
        // Registry-vocabulary code with no Java constant survives detection —
        // the old enum switch folded this to NONE.
        assertEquals("ROOM_BOOKING",
                serviceDetecting("ROOM_BOOKING").detectOperation(List.of("book a room")));
    }

    @Test
    void registryAddedIntentDispatchesToGenericContext() {
        // A new intent added purely as DATA (one aa_intent row) must flow end
        // to end: detected, past project gating, into a generic context with
        // no checklist template and no Salesforce data — never folded to NONE.
        intentRepository.save(AaIntent.builder()
                .code("ROOM_BOOKING").displayName("ROOM_BOOKING").active(true)
                .description("Return \"ROOM_BOOKING\" if customer asks to book a room.").build());

        ChecklistContext ctx = serviceDetecting("ROOM_BOOKING")
                .buildChecklistContext(List.of("I want to book a room"), null, "9876543210", null);

        assertEquals("ROOM_BOOKING", ctx.operationType());
        assertNull(ctx.checklistPrompt());   // no checklist.room_booking template seeded
        assertNull(ctx.customerDataContext());
        assertFalse(ctx.hasContext());       // degrades to knowledge-base flow
        assertFalse(ctx.wasIntentFiltered());
    }

    @Test
    void registryAddedIntentStillGatedByProject() {
        // METRO has explicit aa_project_intent rows that do NOT include the
        // new code, so for METRO it is filtered — reported via filteredIntent.
        intentRepository.save(AaIntent.builder()
                .code("ROOM_BOOKING").displayName("ROOM_BOOKING").active(true)
                .description("Return \"ROOM_BOOKING\" if customer asks to book a room.").build());

        ChecklistContext ctx = serviceDetecting("ROOM_BOOKING")
                .buildChecklistContext(List.of("I want to book a room"), null, "9876543210", "METRO");

        assertEquals(IntentCodes.NONE, ctx.operationType());
        assertEquals("ROOM_BOOKING", ctx.filteredIntent());
        assertTrue(ctx.wasIntentFiltered());
    }
}
