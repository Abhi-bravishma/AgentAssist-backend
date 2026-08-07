package com.agentassist.configregistry;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * H2 + Liquibase test slice for the config registry. The REAL changelog runs
 * (schema + seed), so every test exercises the exact rows production gets —
 * including CLOB round-tripping of the prompt files.
 *
 * <p>ddl-auto is forced to none: Liquibase owns the aa_* tables and Hibernate
 * must not race it. Service caches are cleared before each test because the
 * singletons outlive the per-test transaction rollback.</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.enabled=true"
})
@ImportAutoConfiguration(LiquibaseAutoConfiguration.class)
@Import({PromptService.class, BrandService.class, IntentRegistryService.class,
        LanguageRegistryService.class, SettingsService.class})
public abstract class ConfigRegistryTestBase {

    @Autowired
    protected PromptService promptService;
    @Autowired
    protected BrandService brandService;
    @Autowired
    protected IntentRegistryService intentRegistryService;
    @Autowired
    protected LanguageRegistryService languageRegistryService;
    @Autowired
    protected SettingsService settingsService;

    @BeforeEach
    void clearRegistryCaches() {
        promptService.evictAll();
        brandService.evictAll();
        languageRegistryService.evictAll();
        settingsService.evictAll();
    }
}
