package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaIntent;
import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.entity.AaProjectIntent;
import com.agentassist.configregistry.repository.AaIntentRepository;
import com.agentassist.configregistry.repository.AaProjectIntentRepository;
import com.agentassist.configregistry.repository.AaProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project → intent whitelist, replacing the {@code isOperationValidForProject}
 * switch with identical semantics:
 *
 * <ul>
 *   <li>NONE → always allowed (never stored)</li>
 *   <li>null/blank project → all intents allowed</li>
 *   <li>unknown project → all intents allowed</li>
 *   <li>project with NO rows → all intents allowed</li>
 *   <li>project with rows → only intents with an {@code enabled=true} row</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRegistryService {

    private static final String INTENT_NONE = "NONE";

    private final AaProjectRepository projectRepository;
    private final AaIntentRepository intentRepository;
    private final AaProjectIntentRepository projectIntentRepository;

    public boolean isIntentAllowed(String intentCode, String projectCode) {
        if (intentCode == null || INTENT_NONE.equals(intentCode)) {
            return true;
        }
        String normalized = PromptService.normalizeProject(projectCode);
        if (normalized == null) {
            return true;
        }
        Optional<AaProject> project = projectRepository.findByCode(normalized);
        if (project.isEmpty()) {
            return true; // unknown project → allow all (old default branch)
        }
        List<AaProjectIntent> rows = projectIntentRepository.findByProjectId(project.get().getId());
        if (rows.isEmpty()) {
            return true; // no rows → allow all
        }
        Optional<AaIntent> intent = intentRepository.findByCode(intentCode);
        if (intent.isEmpty()) {
            return false;
        }
        Long intentId = intent.get().getId();
        return rows.stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getEnabled()) && intentId.equals(r.getIntentId()));
    }

    /**
     * The "not available through this service" message for a filtered intent.
     * Matches the old {@code getFilteredIntentMessage} exactly: the RAW project
     * string is interpolated (no trim, no case change), and only a NULL project
     * falls back to "this service" — a blank string is used as-is.
     */
    public String filteredMessage(String intentCode, String projectCode) {
        AaIntent intent = intentRepository.findByCode(intentCode)
                .orElseThrow(() -> {
                    log.error("[ConfigRegistry] filteredMessage for unknown intent '{}'", intentCode);
                    return new ConfigRegistryException("Unknown intent '" + intentCode + "'");
                });
        String template = intent.getFilteredMessageTemplate();
        if (template == null || template.isBlank()) {
            log.error("[ConfigRegistry] Intent '{}' has no filtered_message_template", intentCode);
            throw new ConfigRegistryException(
                    "Intent '" + intentCode + "' has no filtered_message_template");
        }
        String projectDisplay = projectCode != null ? projectCode : "this service";
        return template.replace("{project}", projectDisplay);
    }

    /** Active intents this project may use — for the Part 2 classifier. */
    public List<String> allowedIntents(String projectCode) {
        List<AaIntent> active = intentRepository.findByActiveTrue();
        String normalized = PromptService.normalizeProject(projectCode);
        if (normalized == null) {
            return active.stream().map(AaIntent::getCode).toList();
        }
        Optional<AaProject> project = projectRepository.findByCode(normalized);
        if (project.isEmpty()) {
            return active.stream().map(AaIntent::getCode).toList();
        }
        List<AaProjectIntent> rows = projectIntentRepository.findByProjectId(project.get().getId());
        if (rows.isEmpty()) {
            return active.stream().map(AaIntent::getCode).toList();
        }
        Set<Long> enabledIds = rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(AaProjectIntent::getIntentId)
                .collect(Collectors.toSet());
        return active.stream()
                .filter(i -> enabledIds.contains(i.getId()))
                .map(AaIntent::getCode)
                .toList();
    }
}
