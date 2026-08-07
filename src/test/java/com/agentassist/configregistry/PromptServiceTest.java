package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.entity.AaPromptTemplate;
import com.agentassist.configregistry.repository.AaProjectRepository;
import com.agentassist.configregistry.repository.AaPromptTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptServiceTest extends ConfigRegistryTestBase {

    private static final String KEY = "t.test";

    @Autowired
    private AaPromptTemplateRepository templateRepository;
    @Autowired
    private AaProjectRepository projectRepository;

    private Long metroId;

    @BeforeEach
    void seedTestTemplates() {
        metroId = projectRepository.findByCode("METRO").orElseThrow().getId();
        save(KEY, null, 1, "D v1", AaPromptTemplate.STATUS_PUBLISHED);
        save(KEY, null, 2, "D v2 hello {who}", AaPromptTemplate.STATUS_PUBLISHED);
        save(KEY, null, 3, "D v3 draft", AaPromptTemplate.STATUS_DRAFT);
        save(KEY, metroId, 1, "P v1 hello {who}", AaPromptTemplate.STATUS_PUBLISHED);
    }

    private void save(String key, Long projectId, int version, String content, String status) {
        templateRepository.save(AaPromptTemplate.builder()
                .templateKey(key).projectId(projectId).version(version)
                .content(content).status(status).createdBy("test").build());
    }

    @Test
    void highestPublishedVersionWins_draftIgnored() {
        assertEquals("D v2 hello x", promptService.renderDefault(KEY, Map.of("who", "x")));
    }

    @Test
    void projectOverrideWinsOverDefault() {
        assertEquals("P v1 hello x", promptService.render(KEY, "METRO", Map.of("who", "x")));
    }

    @Test
    void projectCodeIsNormalized() {
        assertEquals("P v1 hello x", promptService.render(KEY, "  metro ", Map.of("who", "x")));
    }

    @Test
    void unknownProjectFallsBackToDefault() {
        assertEquals("D v2 hello x", promptService.render(KEY, "HOSPITALITY", Map.of("who", "x")));
    }

    @Test
    void knownProjectWithoutOverrideFallsBackToDefault() {
        assertEquals("D v2 hello x", promptService.render(KEY, "ALLIANZ", Map.of("who", "x")));
    }

    @Test
    void missingVariableThrowsListingNames() {
        ConfigRegistryException e = assertThrows(ConfigRegistryException.class,
                () -> promptService.renderDefault(KEY, Map.of()));
        assertTrue(e.getMessage().contains("who"), e.getMessage());
        assertTrue(e.getMessage().contains(KEY), e.getMessage());
    }

    @Test
    void extraVariablesAreIgnored() {
        assertEquals("D v2 hello x",
                promptService.renderDefault(KEY, Map.of("who", "x", "unused", "y")));
    }

    @Test
    void variableValueContainingPlaceholderSyntaxIsNotReprocessed() {
        // A customer message may literally contain "{who}" — it must be
        // inserted verbatim, not substituted again or flagged missing.
        assertEquals("D v2 hello {who}",
                promptService.renderDefault(KEY, Map.of("who", "{who}")));
    }

    @Test
    void unknownKeyThrows() {
        ConfigRegistryException e = assertThrows(ConfigRegistryException.class,
                () -> promptService.renderDefault("t.nope", Map.of()));
        assertTrue(e.getMessage().contains("t.nope"), e.getMessage());
    }

    @Test
    void resolvedContentIsCachedUntilEvicted() {
        assertEquals("D v2 hello x", promptService.renderDefault(KEY, Map.of("who", "x")));

        // Publish a newer version behind the cache's back
        save(KEY, null, 4, "D v4 hello {who}", AaPromptTemplate.STATUS_PUBLISHED);

        // Within the TTL the old content is still served
        assertEquals("D v2 hello x", promptService.renderDefault(KEY, Map.of("who", "x")));

        promptService.evictAll();
        assertEquals("D v4 hello x", promptService.renderDefault(KEY, Map.of("who", "x")));
    }

    @Test
    void seededRegistryHasEveryExpectedKeyPublished() {
        for (String key : TemplateKeys.ALL) {
            assertTrue(templateRepository
                            .findFirstByTemplateKeyAndProjectIdIsNullAndStatusOrderByVersionDesc(
                                    key, AaPromptTemplate.STATUS_PUBLISHED)
                            .isPresent(),
                    "missing PUBLISHED default row for " + key);
        }
    }

    @Test
    void metroProjectExistsFromSeed() {
        assertTrue(projectRepository.findByCode("METRO").isPresent());
        assertEquals(4, projectRepository.count());
    }
}
