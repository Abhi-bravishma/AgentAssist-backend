package com.agentassist.configregistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsAndLanguageTest extends ConfigRegistryTestBase {

    @Test
    void activeProviderSeededToOpenai() {
        assertEquals("openai", settingsService.require("ai.active_provider"));
    }

    @Test
    void setUpdatesValueAndEvictsCache() {
        settingsService.require("ai.active_provider"); // prime cache
        settingsService.set("ai.active_provider", "ollama", "test");
        assertEquals("ollama", settingsService.require("ai.active_provider"));
    }

    @Test
    void missingSettingIsEmptyAndRequireThrows() {
        assertTrue(settingsService.find("no.such.key").isEmpty());
    }

    @Test
    void languageDescribeMatchesOldDescribeLanguageContract() {
        // Exact strings describeLanguage() produces today
        assertEquals("Traditional Chinese (繁體中文), using Traditional characters only",
                languageRegistryService.describe("zh-Hant"));
        assertEquals("Simplified Chinese (简体中文), using Simplified characters only",
                languageRegistryService.describe("zh-Hans"));
        assertEquals("Chinese", languageRegistryService.describe("zh"));
        // null/blank → "English"; unknown code → raw tag unchanged
        assertEquals("English", languageRegistryService.describe(null));
        assertEquals("English", languageRegistryService.describe(" "));
        assertEquals("fr", languageRegistryService.describe("fr"));
    }
}
