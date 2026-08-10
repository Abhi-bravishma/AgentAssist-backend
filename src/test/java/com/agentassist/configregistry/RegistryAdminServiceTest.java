package com.agentassist.configregistry;

import com.agentassist.configregistry.RegistryAdminService.BrandAttributeView;
import com.agentassist.configregistry.RegistryAdminService.IntentView;
import com.agentassist.configregistry.RegistryAdminService.ProjectView;
import com.agentassist.configregistry.repository.AaBrandAttributeRepository;
import com.agentassist.configregistry.repository.AaIntentRepository;
import com.agentassist.configregistry.repository.AaProjectIntentRepository;
import com.agentassist.configregistry.repository.AaProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part 5 CRUD against the real seed. The interesting assertions are the
 * downstream ones: what the ADMIN writes must be exactly what the PIPELINE
 * reads (isIntentAllowed, classifierVars, brand attr resolution).
 */
class RegistryAdminServiceTest extends ConfigRegistryTestBase {

    @Autowired
    private AaProjectRepository projectRepository;
    @Autowired
    private AaIntentRepository intentRepository;
    @Autowired
    private AaProjectIntentRepository projectIntentRepository;
    @Autowired
    private AaBrandAttributeRepository brandAttributeRepository;

    private RegistryAdminService service;

    @BeforeEach
    void newService() {
        service = new RegistryAdminService(projectRepository, intentRepository,
                projectIntentRepository, brandAttributeRepository, brandService);
    }

    // ==================== projects ====================

    @Test
    void seedProjectsAreListedWithWhitelistState() {
        List<ProjectView> projects = service.listProjects();
        List<String> codes = projects.stream().map(ProjectView::code).toList();
        assertTrue(codes.containsAll(List.of("METRO", "SCB", "ALLIANZ", "TELCO")));
        ProjectView metro = projects.stream().filter(p -> p.code().equals("METRO")).findFirst().orElseThrow();
        assertTrue(metro.restricted());
        assertEquals(List.of("BILLING", "FEE_WAIVER", "HOME_LOAN_CLOSURE"), metro.enabledIntents());
    }

    @Test
    void newProjectStartsUnrestricted() {
        ProjectView created = service.createProject("hospitality", "Hospitality Demo");
        assertEquals("HOSPITALITY", created.code()); // normalized to uppercase
        assertFalse(created.restricted());
        // The pipeline agrees: everything is allowed until the whitelist says otherwise
        assertTrue(intentRegistryService.isIntentAllowed("POLICY", "HOSPITALITY"));
        assertTrue(intentRegistryService.isIntentAllowed("TELCO", "HOSPITALITY"));
    }

    @Test
    void duplicateAndInvalidProjectCodesRejected() {
        assertThrows(ConfigRegistryException.class, () -> service.createProject("METRO", null));
        assertThrows(ConfigRegistryException.class, () -> service.createProject("bad code!", null));
        assertThrows(ConfigRegistryException.class, () -> service.createProject(null, null));
    }

    @Test
    void whitelistRestrictAndRelease() {
        service.createProject("HOSPITALITY", null);

        // Restrict to POLICY only → everything else filtered
        ProjectView restricted = service.setProjectIntents("HOSPITALITY", true, List.of("POLICY"));
        assertTrue(restricted.restricted());
        assertEquals(List.of("POLICY"), restricted.enabledIntents());
        assertTrue(intentRegistryService.isIntentAllowed("POLICY", "HOSPITALITY"));
        assertFalse(intentRegistryService.isIntentAllowed("TELCO", "HOSPITALITY"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "HOSPITALITY"));

        // Back to unrestricted → rows deleted, allow-all semantics return
        ProjectView released = service.setProjectIntents("HOSPITALITY", false, null);
        assertFalse(released.restricted());
        assertTrue(intentRegistryService.isIntentAllowed("TELCO", "HOSPITALITY"));
    }

    @Test
    void denyAllIsRepresentable() {
        service.createProject("LOCKED", null);
        ProjectView locked = service.setProjectIntents("LOCKED", true, List.of());
        assertTrue(locked.restricted());
        assertEquals(List.of(), locked.enabledIntents());
        assertFalse(intentRegistryService.isIntentAllowed("FEE_WAIVER", "LOCKED"));
        assertTrue(intentRegistryService.isIntentAllowed("NONE", "LOCKED")); // NONE always allowed
    }

    @Test
    void unknownIntentInWhitelistRejected() {
        assertThrows(ConfigRegistryException.class,
                () -> service.setProjectIntents("METRO", true, List.of("NO_SUCH_INTENT")));
    }

    // ==================== intents ====================

    @Test
    void createdIntentFlowsIntoClassifierAndGating() {
        service.createIntent("room_booking", "Room Booking",
                "Return \"ROOM_BOOKING\" if the customer asks to book a room.",
                "Room booking is not available through {project}.");

        // Classifier sees it (2b) …
        assertTrue(intentRegistryService.validClassifierCodes().contains("ROOM_BOOKING"));
        assertTrue(intentRegistryService.classifierVars().get("intent_codes").endsWith("ROOM_BOOKING"));
        // … unrestricted projects allow it, restricted seed projects filter it (2c)
        assertTrue(intentRegistryService.isIntentAllowed("ROOM_BOOKING", null));
        assertFalse(intentRegistryService.isIntentAllowed("ROOM_BOOKING", "METRO"));
        // … and the filtered message renders with the project name
        assertEquals("Room booking is not available through METRO.",
                intentRegistryService.filteredMessage("ROOM_BOOKING", "METRO"));
    }

    @Test
    void reservedDuplicateAndRuleLessIntentsRejected() {
        assertThrows(ConfigRegistryException.class,
                () -> service.createIntent("NONE", null, "rule", null));
        assertThrows(ConfigRegistryException.class,
                () -> service.createIntent("GENERAL", null, "rule", null));
        assertThrows(ConfigRegistryException.class,
                () -> service.createIntent("TELCO", null, "rule", null)); // seeded already
        assertThrows(ConfigRegistryException.class,
                () -> service.createIntent("VALID_CODE", null, "  ", null)); // no rule
    }

    @Test
    void deactivatedIntentLeavesTheClassifier() {
        IntentView updated = service.updateIntent("TELCO", null, null, null, false);
        assertFalse(updated.active());
        assertFalse(intentRegistryService.validClassifierCodes().contains("TELCO"));
        assertFalse(intentRegistryService.classifierVars().get("intent_codes").contains("TELCO"));
        // Reactivate → back
        service.updateIntent("TELCO", null, null, null, true);
        assertTrue(intentRegistryService.validClassifierCodes().contains("TELCO"));
    }

    // ==================== brand attributes ====================

    @Test
    void brandUpsertIsLiveImmediately() {
        // New project has no overrides → bank_name falls back to the PROJECT
        // CODE ITSELF, not the global default (§4.6 asymmetry, bank_name only)
        service.createProject("HOSPITALITY", null);
        assertEquals("HOSPITALITY", brandService.attr("HOSPITALITY", BrandService.BANK_NAME));

        service.upsertBrandAttribute("HOSPITALITY", BrandService.BANK_NAME, "Grand Hotel Group");
        assertEquals("Grand Hotel Group", brandService.attr("HOSPITALITY", BrandService.BANK_NAME));

        // Update same key → value replaced, not duplicated
        service.upsertBrandAttribute("HOSPITALITY", BrandService.BANK_NAME, "Grand Hotels");
        assertEquals("Grand Hotels", brandService.attr("HOSPITALITY", BrandService.BANK_NAME));
        long rows = service.listBrandAttributes().stream()
                .filter(a -> "HOSPITALITY".equals(a.projectCode())
                        && BrandService.BANK_NAME.equals(a.attrKey()))
                .count();
        assertEquals(1, rows);
    }

    @Test
    void brandDeleteFallsBackToGlobalDefault() {
        service.createProject("HOSPITALITY", null);
        BrandAttributeView created =
                service.upsertBrandAttribute("HOSPITALITY", BrandService.HOTLINE, "1-800-HOTEL");
        assertEquals("1-800-HOTEL", brandService.attr("HOSPITALITY", BrandService.HOTLINE));

        service.deleteBrandAttribute(created.id());
        assertEquals("the customer service number printed on the back of the card",
                brandService.attr("HOSPITALITY", BrandService.HOTLINE));
    }

    @Test
    void brandValidationRejectsBlanksAndUnknownProject() {
        assertThrows(ConfigRegistryException.class,
                () -> service.upsertBrandAttribute("METRO", " ", "x"));
        assertThrows(ConfigRegistryException.class,
                () -> service.upsertBrandAttribute("METRO", "bank_name", " "));
        assertThrows(ConfigRegistryException.class,
                () -> service.upsertBrandAttribute("NO_SUCH_PROJECT", "bank_name", "x"));
    }
}
