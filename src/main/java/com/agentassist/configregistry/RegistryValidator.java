package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaPromptTemplate;
import com.agentassist.configregistry.repository.AaPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Startup check: every key in {@link TemplateKeys#ALL} must have a PUBLISHED
 * default row. Logs a table of what it found. On failure it logs ERROR and
 * KEEPS the app running (some deployments seed after first boot) — but the
 * result feeds {@code ConfigRegistryHealthIndicator}, so an incomplete
 * registry turns the health endpoint DOWN and can gate the deploy instead of
 * failing on the first customer message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistryValidator implements ApplicationRunner {

    private final AaPromptTemplateRepository templateRepository;
    private final RegistryStatus status;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> missing = new ArrayList<>();
            StringBuilder table = new StringBuilder("Config registry template check:\n");
            for (String key : TemplateKeys.ALL) {
                Optional<AaPromptTemplate> template = templateRepository
                        .findFirstByTemplateKeyAndProjectIdIsNullAndStatusOrderByVersionDesc(
                                key, AaPromptTemplate.STATUS_PUBLISHED);
                if (template.isPresent()) {
                    table.append(String.format("  %-45s v%d PUBLISHED%n", key, template.get().getVersion()));
                } else {
                    table.append(String.format("  %-45s *** MISSING ***%n", key));
                    missing.add(key);
                }
            }
            status.update(missing);
            if (missing.isEmpty()) {
                log.info("[ConfigRegistry] All {} template keys present.\n{}",
                        TemplateKeys.ALL.size(), table);
            } else {
                log.error("[ConfigRegistry] Registry INCOMPLETE — {} of {} template keys missing: {}."
                        + " Requests needing them will FAIL until seeded.\n{}",
                        missing.size(), TemplateKeys.ALL.size(), missing, table);
            }
        } catch (Exception e) {
            status.markUnreachable(e);
            log.error("[ConfigRegistry] Validation could not run (registry unreachable?): {}",
                    e.getMessage(), e);
        }
    }
}
