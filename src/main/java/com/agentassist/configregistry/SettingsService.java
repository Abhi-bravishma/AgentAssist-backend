package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaSetting;
import com.agentassist.configregistry.repository.AaSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Global runtime settings (aa_setting, NULL-project rows). The 60s cache means
 * a portal change — e.g. flipping {@code ai.active_provider} — reaches every
 * node within a minute even without an explicit evict.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final long CACHE_TTL_MS = 60_000;

    private final AaSettingRepository settingRepository;

    private final TtlCache<Optional<String>> cache = new TtlCache<>(CACHE_TTL_MS);

    public Optional<String> find(String key) {
        return cache.get(key, k -> settingRepository.findBySettingKeyAndProjectIdIsNull(k)
                .map(AaSetting::getSettingValue));
    }

    public String require(String key) {
        return find(key).orElseThrow(() -> {
            log.error("[ConfigRegistry] Required setting '{}' has no row", key);
            return new ConfigRegistryException("Required setting '" + key + "' has no row");
        });
    }

    @Transactional
    public void set(String key, String value, String updatedBy) {
        AaSetting setting = settingRepository.findBySettingKeyAndProjectIdIsNull(key)
                .orElseGet(() -> AaSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);
        settingRepository.save(setting);
        cache.clear();
        log.info("[ConfigRegistry] Setting '{}' updated by {}", key, updatedBy);
    }

    public void evictAll() {
        cache.clear();
    }
}
