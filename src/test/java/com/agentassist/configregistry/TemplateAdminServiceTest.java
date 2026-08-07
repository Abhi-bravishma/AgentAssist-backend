package com.agentassist.configregistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TemplateAdminService.class)
class TemplateAdminServiceTest extends ConfigRegistryTestBase {

    @Autowired
    private TemplateAdminService templateAdminService;

    @Test
    void listShowsEveryVariantIncludingScbOverride() {
        var summaries = templateAdminService.list();
        // 22 default keys + the SCB fee-waiver override
        assertEquals(TemplateKeys.ALL.size() + 1, summaries.size());
        assertTrue(summaries.stream().anyMatch(s ->
                "checklist.fee_waiver".equals(s.templateKey()) && "SCB".equals(s.projectCode())));
        assertTrue(summaries.stream().allMatch(s -> s.publishedVersion() != null));
    }

    @Test
    void getReturnsExactVariantWithoutFallback() {
        var scb = templateAdminService.get("checklist.fee_waiver", "SCB").orElseThrow();
        var def = templateAdminService.get("checklist.fee_waiver", null).orElseThrow();
        assertTrue(scb.content().contains("STANDARD CHARTERED"));
        assertTrue(def.content().contains("{bank_name_upper}"));
        // A variant that does not exist is empty, NOT the default
        assertTrue(templateAdminService.get("checklist.billing", "SCB").isEmpty());
    }

    @Test
    void publishCreatesNextVersionAndGoesLiveImmediately() {
        String original = promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of());

        var published = templateAdminService.publish(
                TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, null, "New reply text.", "test");
        assertEquals(2, published.version());

        // Cache evicted by publish -> render serves the new version at once
        assertEquals("New reply text.",
                promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of()));

        // Rollback = republish the old content as v3
        var rolledBack = templateAdminService.publish(
                TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, null, original, "test");
        assertEquals(3, rolledBack.version());
        assertEquals(original,
                promptService.renderDefault(TemplateKeys.SYSTEM_NO_KNOWLEDGE_REPLY, Map.of()));
    }

    @Test
    void publishRejectsEmptyContentAndUnknownProject() {
        assertThrows(ConfigRegistryException.class,
                () -> templateAdminService.publish("t.x", null, "  ", "test"));
        assertThrows(ConfigRegistryException.class,
                () -> templateAdminService.publish("t.x", "NOPE", "content", "test"));
    }

    @Test
    void publishNewKeyStartsAtVersionOne() {
        var published = templateAdminService.publish("checklist.room_booking", null, "New checklist.", "test");
        assertEquals(1, published.version());
    }
}
