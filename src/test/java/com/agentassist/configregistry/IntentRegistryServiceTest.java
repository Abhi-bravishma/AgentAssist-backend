package com.agentassist.configregistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every branch of the old isOperationValidForProject switch, verified against
 * the seeded aa_project_intent rows.
 */
class IntentRegistryServiceTest extends ConfigRegistryTestBase {

    // ---- METRO: FEE_WAIVER, HOME_LOAN_CLOSURE, BILLING ----

    @Test
    void metroMatrix() {
        assertTrue(intentRegistryService.isIntentAllowed("FEE_WAIVER", "METRO"));
        assertTrue(intentRegistryService.isIntentAllowed("HOME_LOAN_CLOSURE", "METRO"));
        assertTrue(intentRegistryService.isIntentAllowed("BILLING", "METRO"));
        assertFalse(intentRegistryService.isIntentAllowed("POLICY", "METRO"));
        assertFalse(intentRegistryService.isIntentAllowed("CLAIMS", "METRO"));
        assertFalse(intentRegistryService.isIntentAllowed("TELCO", "METRO"));
    }

    // ---- ALLIANZ: POLICY, CLAIMS ----

    @Test
    void allianzMatrix() {
        assertTrue(intentRegistryService.isIntentAllowed("POLICY", "ALLIANZ"));
        assertTrue(intentRegistryService.isIntentAllowed("CLAIMS", "ALLIANZ"));
        assertFalse(intentRegistryService.isIntentAllowed("FEE_WAIVER", "ALLIANZ"));
        assertFalse(intentRegistryService.isIntentAllowed("HOME_LOAN_CLOSURE", "ALLIANZ"));
        assertFalse(intentRegistryService.isIntentAllowed("BILLING", "ALLIANZ"));
        assertFalse(intentRegistryService.isIntentAllowed("TELCO", "ALLIANZ"));
    }

    // ---- SCB: FEE_WAIVER, BILLING ----

    @Test
    void scbMatrix() {
        assertTrue(intentRegistryService.isIntentAllowed("FEE_WAIVER", "SCB"));
        assertTrue(intentRegistryService.isIntentAllowed("BILLING", "SCB"));
        assertFalse(intentRegistryService.isIntentAllowed("HOME_LOAN_CLOSURE", "SCB"));
        assertFalse(intentRegistryService.isIntentAllowed("POLICY", "SCB"));
        assertFalse(intentRegistryService.isIntentAllowed("CLAIMS", "SCB"));
        assertFalse(intentRegistryService.isIntentAllowed("TELCO", "SCB"));
    }

    // ---- TELCO: TELCO only ----

    @Test
    void telcoMatrix() {
        assertTrue(intentRegistryService.isIntentAllowed("TELCO", "TELCO"));
        assertFalse(intentRegistryService.isIntentAllowed("FEE_WAIVER", "TELCO"));
        assertFalse(intentRegistryService.isIntentAllowed("POLICY", "TELCO"));
        assertFalse(intentRegistryService.isIntentAllowed("BILLING", "TELCO"));
    }

    // ---- fallthrough cases ----

    @Test
    void unknownProjectAllowsEverything() {
        for (String intent : new String[]{"FEE_WAIVER", "HOME_LOAN_CLOSURE", "POLICY",
                "CLAIMS", "TELCO", "BILLING"}) {
            assertTrue(intentRegistryService.isIntentAllowed(intent, "HOSPITALITY"), intent);
        }
    }

    @Test
    void nullOrBlankProjectAllowsEverything() {
        assertTrue(intentRegistryService.isIntentAllowed("POLICY", null));
        assertTrue(intentRegistryService.isIntentAllowed("POLICY", "  "));
    }

    @Test
    void noneIsAlwaysAllowed() {
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "METRO"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "ALLIANZ"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "SCB"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "TELCO"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "HOSPITALITY"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", null));
    }

    @Test
    void projectCodeIsNormalizedBeforeLookup() {
        assertTrue(intentRegistryService.isIntentAllowed("FEE_WAIVER", " metro "));
        assertFalse(intentRegistryService.isIntentAllowed("POLICY", " metro "));
    }

    // ---- filteredMessage ----

    @Test
    void filteredMessageInterpolatesRawProject() {
        String msg = intentRegistryService.filteredMessage("POLICY", "HOSPITALITY");
        assertTrue(msg.contains("HOSPITALITY"), msg);
        assertFalse(msg.contains("{project}"), msg);
    }

    @Test
    void filteredMessageNullProjectSaysThisService() {
        String msg = intentRegistryService.filteredMessage("POLICY", null);
        assertTrue(msg.contains("this service"), msg);
    }

    // ---- allowedIntents ----

    @Test
    void allowedIntentsForMetro() {
        List<String> allowed = intentRegistryService.allowedIntents("METRO");
        assertEquals(List.of("FEE_WAIVER", "HOME_LOAN_CLOSURE", "BILLING").stream().sorted().toList(),
                allowed.stream().sorted().toList());
    }

    @Test
    void allowedIntentsForUnknownProjectIsAllActive() {
        assertEquals(6, intentRegistryService.allowedIntents("HOSPITALITY").size());
        assertEquals(6, intentRegistryService.allowedIntents(null).size());
    }
}
