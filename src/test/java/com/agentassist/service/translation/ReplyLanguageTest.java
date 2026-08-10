package com.agentassist.service.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * §4.9 — replies stick to the conversation's base language. A customer who
 * types one English message mid-conversation must still get the reply in
 * their language; the base language only yields when none was recorded.
 */
class ReplyLanguageTest {

    @Test
    void baseLanguageWinsOverCurrentMessage() {
        assertEquals("id", LanguageService.replyLanguage("id", "en"));
        assertEquals("zh-Hant", LanguageService.replyLanguage("zh-Hant", "en"));
        assertEquals("id", LanguageService.replyLanguage("id", "fr"));
    }

    @Test
    void fallsBackToCurrentWhenNoBaseRecorded() {
        assertEquals("en", LanguageService.replyLanguage(null, "en"));
        assertEquals("th", LanguageService.replyLanguage(null, "th"));
        assertEquals("th", LanguageService.replyLanguage("", "th"));
        assertEquals("th", LanguageService.replyLanguage("  ", "th"));
    }

    @Test
    void sameLanguagePassesThroughUnchanged() {
        assertEquals("id", LanguageService.replyLanguage("id", "id"));
        assertEquals("en", LanguageService.replyLanguage("en", "en"));
    }

    // ==================== §4.10: "und" never wins ====================

    @Test
    void undeterminedBaseYieldsToCurrentLanguage() {
        // A stored "und" must not pin the conversation to failed detection.
        assertEquals("id", LanguageService.replyLanguage("und", "id"));
        assertEquals("en", LanguageService.replyLanguage("UND", "en"));
    }

    @Test
    void usableRejectsBlankAndUndetermined() {
        assertEquals(false, LanguageService.isUsable(null));
        assertEquals(false, LanguageService.isUsable(""));
        assertEquals(false, LanguageService.isUsable("  "));
        assertEquals(false, LanguageService.isUsable("und"));
        assertEquals(false, LanguageService.isUsable("UND"));
        assertEquals(true, LanguageService.isUsable("en"));
        assertEquals(true, LanguageService.isUsable("zh-Hant"));
    }
}
