package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaBrandAttribute;
import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.repository.AaBrandAttributeRepository;
import com.agentassist.configregistry.repository.AaProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Brand attribute resolution: project row → global (NULL-project) row.
 *
 * <p>One deliberate asymmetry is preserved from the old {@code resolveBankName}
 * switch (plan §4.6): for {@code bank_name}, a non-blank project WITHOUT its
 * own row resolves to the project code itself — trimmed, ORIGINAL case — not
 * to the global default. The global "your bank" applies only when the project
 * is null/blank. hotline and loan_email always fall back to their global rows.
 * With the controller defaulting projectName to HOSPITALITY, the unknown
 * project is the normal case, not an edge case.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    public static final String BANK_NAME = "bank_name";
    public static final String HOTLINE = "hotline";
    public static final String LOAN_EMAIL = "loan_email";

    private static final long CACHE_TTL_MS = 60_000;

    private final AaBrandAttributeRepository attributeRepository;
    private final AaProjectRepository projectRepository;

    private final TtlCache<String> cache = new TtlCache<>(CACHE_TTL_MS);

    public String attr(String projectCode, String key) {
        // Original-case project in the cache key: the bank_name fallback echoes it.
        String cacheKey = (projectCode == null ? "" : projectCode.trim()) + "|" + key;
        return cache.get(cacheKey, k -> load(projectCode, key));
    }

    public void evictAll() {
        cache.clear();
    }

    private String load(String projectCode, String key) {
        if (projectCode == null || projectCode.isBlank()) {
            return globalOrThrow(key, projectCode);
        }
        Optional<AaProject> project =
                projectRepository.findByCode(PromptService.normalizeProject(projectCode));
        if (project.isPresent()) {
            Optional<AaBrandAttribute> row =
                    attributeRepository.findFirstByProjectIdAndAttrKey(project.get().getId(), key);
            if (row.isPresent()) {
                return row.get().getAttrValue();
            }
        }
        if (BANK_NAME.equals(key)) {
            // §4.6: unknown project (or known project without a bank_name row)
            // is addressed by its own name, exactly as the old switch did.
            return projectCode.trim();
        }
        return globalOrThrow(key, projectCode);
    }

    private String globalOrThrow(String key, String projectCode) {
        return attributeRepository.findFirstByProjectIdIsNullAndAttrKey(key)
                .map(AaBrandAttribute::getAttrValue)
                .orElseThrow(() -> {
                    log.error("[ConfigRegistry] No brand attribute '{}' for project '{}'"
                            + " and no global default row", key, projectCode);
                    return new ConfigRegistryException(
                            "No brand attribute '" + key + "' for project '" + projectCode
                            + "' and no global default");
                });
    }
}
