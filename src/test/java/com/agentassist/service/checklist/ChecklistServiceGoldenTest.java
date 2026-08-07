package com.agentassist.service.checklist;

import com.agentassist.configregistry.ConfigRegistryTestBase;
import com.agentassist.service.checklist.ChecklistService.OperationType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden gate for the REWIRED ChecklistService: getChecklistPrompt now goes
 * through PromptService/BrandService, and every rendering must still be
 * byte-identical to the fixtures captured from the pre-restructure code.
 * Replaces the checklist coverage that lived in GoldenPromptRegressionTest
 * while the checklists were Java constants.
 */
class ChecklistServiceGoldenTest extends ConfigRegistryTestBase {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");

    private ChecklistService service() {
        // Salesforce and AI provider are untouched by getChecklistPrompt
        return new ChecklistService(null, null, promptService, brandService, intentRegistryService);
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
                s.getChecklistPrompt(OperationType.FEE_WAIVER, null));
        assertGolden("checklist.fee_waiver__METRO",
                s.getChecklistPrompt(OperationType.FEE_WAIVER, "METRO"));
        assertGolden("checklist.fee_waiver__SCB",
                s.getChecklistPrompt(OperationType.FEE_WAIVER, "SCB"));
        // Unknown project: bank_name falls back to the PROJECT CODE ITSELF,
        // hotline to the neutral default (plan §4.6).
        assertGolden("checklist.fee_waiver__HOSPITALITY",
                s.getChecklistPrompt(OperationType.FEE_WAIVER, "HOSPITALITY"));
    }

    @Test
    void checklistHomeLoanClosure() {
        ChecklistService s = service();
        assertGolden("checklist.home_loan_closure__default",
                s.getChecklistPrompt(OperationType.HOME_LOAN_CLOSURE, null));
        assertGolden("checklist.home_loan_closure__METRO",
                s.getChecklistPrompt(OperationType.HOME_LOAN_CLOSURE, "METRO"));
        assertGolden("checklist.home_loan_closure__HOSPITALITY",
                s.getChecklistPrompt(OperationType.HOME_LOAN_CLOSURE, "HOSPITALITY"));
    }

    @Test
    void projectIndependentChecklists() {
        ChecklistService s = service();
        assertGolden("checklist.billing", s.getChecklistPrompt(OperationType.BILLING, "METRO"));
        assertGolden("checklist.policy", s.getChecklistPrompt(OperationType.POLICY, null));
        assertGolden("checklist.claims", s.getChecklistPrompt(OperationType.CLAIMS, null));
        assertGolden("checklist.telco", s.getChecklistPrompt(OperationType.TELCO, null));
    }

    @Test
    void noneReturnsEmptyWithoutTouchingTheRegistry() {
        assertEquals("", service().getChecklistPrompt(OperationType.NONE, null));
    }

    @Test
    void isOperationValidForProjectDelegatesToRegistry() {
        ChecklistService s = service();
        assertTrue(s.isOperationValidForProject(OperationType.FEE_WAIVER, "METRO"));
        assertFalse(s.isOperationValidForProject(OperationType.POLICY, "METRO"));
        assertTrue(s.isOperationValidForProject(OperationType.POLICY, "ALLIANZ"));
        assertFalse(s.isOperationValidForProject(OperationType.TELCO, "SCB"));
        assertTrue(s.isOperationValidForProject(OperationType.NONE, "METRO"));
        assertTrue(s.isOperationValidForProject(OperationType.POLICY, "HOSPITALITY"));
        assertTrue(s.isOperationValidForProject(OperationType.POLICY, null));
    }
}
