package com.agentassist.service.translation;

import com.agentassist.ai.AiProvider;
import com.agentassist.ai.AiProviderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The in-process detector may skip the model only when it says English. It is
 * confidently wrong on short English lines built from words another language
 * shares - "how to do e sim setup" came back Portuguese at 100% - and on a
 * first message that verdict would have pinned the whole conversation to
 * Portuguese. Every non-English verdict therefore goes to the model, exactly
 * as every message did before the local detector existed.
 */
class LanguageDetectionGateTest {

    private static LocalLanguageDetector local;

    private AiProvider model;
    private LanguageService service;

    @BeforeAll
    static void loadProfiles() throws Exception {
        local = new LocalLanguageDetector(0.85, 0.30, 6); // production defaults
    }

    @BeforeEach
    void setUp() {
        model = mock(AiProvider.class);
        AiProviderFactory factory = mock(AiProviderFactory.class);
        when(factory.active()).thenReturn(model);
        service = new LanguageService(factory, local);
        ReflectionTestUtils.setField(service, "detectionMode", "local");
    }

    @Test
    void englishSkipsTheModel() {
        assertEquals("en", service.detectLanguage("my data is not working since yesterday"));
        verify(model, never()).detectLanguage(anyString());
    }

    @Test
    void englishMisreadAsAnotherLanguageIsCorrectedByTheModel() {
        String text = "how to do e sim setup";
        // the failure this guards against: the local detector is sure this is Portuguese
        assertEquals("pt", local.detect(text).code());

        when(model.detectLanguage(anyString())).thenReturn("en");
        assertEquals("en", service.detectLanguage(text));
        verify(model).detectLanguage(text);
    }

    @Test
    void nonEnglishAlwaysGoesToTheModel() {
        String text = "saya mau cek sisa kuota paket saya sekarang";
        when(model.detectLanguage(anyString())).thenReturn("id");
        assertEquals("id", service.detectLanguage(text));
        verify(model).detectLanguage(text);
    }
}
