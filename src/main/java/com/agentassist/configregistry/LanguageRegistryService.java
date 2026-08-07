package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaLanguage;
import com.agentassist.configregistry.repository.AaLanguageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Language display names for prompts. Mirrors the old
 * {@code describeLanguage()} contract: null/blank → "English", known code →
 * table display name, unknown code → the raw tag unchanged. No caller is
 * switched to this until phase 1c — until then it is inert plumbing.
 */
@Service
@RequiredArgsConstructor
public class LanguageRegistryService {

    private static final long CACHE_TTL_MS = 60_000;

    private final AaLanguageRepository languageRepository;

    private final TtlCache<String> cache = new TtlCache<>(CACHE_TTL_MS);

    public String describe(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "English";
        }
        return cache.get(languageCode, code -> languageRepository.findByCode(code)
                .map(AaLanguage::getDisplayName)
                .orElse(code));
    }

    public void evictAll() {
        cache.clear();
    }
}
