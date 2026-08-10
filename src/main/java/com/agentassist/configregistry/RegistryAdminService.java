package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaBrandAttribute;
import com.agentassist.configregistry.entity.AaIntent;
import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.entity.AaProjectIntent;
import com.agentassist.configregistry.repository.AaBrandAttributeRepository;
import com.agentassist.configregistry.repository.AaIntentRepository;
import com.agentassist.configregistry.repository.AaProjectIntentRepository;
import com.agentassist.configregistry.repository.AaProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Portal CRUD for the non-template registry data: projects, intents, the
 * project→intent whitelist and brand attributes (Part 5).
 *
 * <p>Projects and intents are never hard-deleted — history (templates,
 * whitelist rows) hangs off them — they are deactivated instead. Whitelist
 * semantics stay exactly what the pipeline enforces: a project with NO rows
 * allows every intent; a restricted project gets one row PER ACTIVE INTENT so
 * the stored matrix is explicit (and deny-all is representable). Brand
 * mutations evict the BrandService cache so they are live immediately.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistryAdminService {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");
    /** Sentinels the classifier owns — never stored as aa_intent rows. */
    private static final Set<String> RESERVED_INTENT_CODES = Set.of("NONE", "GENERAL");

    private final AaProjectRepository projectRepository;
    private final AaIntentRepository intentRepository;
    private final AaProjectIntentRepository projectIntentRepository;
    private final AaBrandAttributeRepository brandAttributeRepository;
    private final BrandService brandService;

    public record ProjectView(String code, String displayName, boolean active,
                              boolean restricted, List<String> enabledIntents) {
    }

    public record IntentView(String code, String displayName, boolean active,
                             String description, String filteredMessageTemplate) {
    }

    public record BrandAttributeView(Long id, String projectCode, String attrKey, String attrValue) {
    }

    // ==================== projects ====================

    public List<ProjectView> listProjects() {
        return projectRepository.findAll().stream()
                .sorted(Comparator.comparing(AaProject::getId))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public ProjectView createProject(String code, String displayName) {
        String normalized = normalizeCode(code, "project code");
        projectRepository.findByCode(normalized).ifPresent(p -> {
            throw new ConfigRegistryException("Project '" + normalized + "' already exists");
        });
        AaProject project = projectRepository.save(AaProject.builder()
                .code(normalized)
                .displayName(displayName == null || displayName.isBlank() ? normalized : displayName.trim())
                .active(true)
                .build());
        log.info("[RegistryAdmin] Project '{}' created", normalized);
        return toView(project);
    }

    @Transactional
    public ProjectView updateProject(String code, String displayName, Boolean active) {
        AaProject project = requireProject(code);
        if (displayName != null && !displayName.isBlank()) {
            project.setDisplayName(displayName.trim());
        }
        if (active != null) {
            project.setActive(active);
        }
        project.setUpdatedAt(Instant.now());
        projectRepository.save(project);
        log.info("[RegistryAdmin] Project '{}' updated (active: {})", project.getCode(), project.getActive());
        return toView(project);
    }

    /**
     * Replace the project's intent whitelist.
     *
     * <p>{@code restricted=false} deletes every row — the pipeline then allows
     * ALL intents (the "no rows" semantics). {@code restricted=true} writes one
     * row per active intent, enabled only for the requested codes, so even
     * "restricted to nothing" is stored explicitly rather than collapsing back
     * to allow-all.</p>
     */
    @Transactional
    public ProjectView setProjectIntents(String code, boolean restricted, List<String> enabledIntentCodes) {
        AaProject project = requireProject(code);
        List<AaProjectIntent> existing = projectIntentRepository.findByProjectId(project.getId());
        projectIntentRepository.deleteAll(existing);

        if (restricted) {
            Set<String> enabled = enabledIntentCodes == null ? Set.of()
                    : enabledIntentCodes.stream()
                            .map(c -> normalizeCode(c, "intent code"))
                            .collect(Collectors.toSet());
            List<AaIntent> activeIntents = intentRepository.findByActiveTrueOrderByIdAsc();
            Set<String> known = activeIntents.stream().map(AaIntent::getCode).collect(Collectors.toSet());
            for (String requested : enabled) {
                if (!known.contains(requested)) {
                    throw new ConfigRegistryException("Unknown or inactive intent '" + requested + "'");
                }
            }
            for (AaIntent intent : activeIntents) {
                projectIntentRepository.save(AaProjectIntent.builder()
                        .projectId(project.getId())
                        .intentId(intent.getId())
                        .enabled(enabled.contains(intent.getCode()))
                        .build());
            }
        }
        log.info("[RegistryAdmin] Project '{}' whitelist replaced (restricted: {}, enabled: {})",
                project.getCode(), restricted, enabledIntentCodes);
        return toView(project);
    }

    // ==================== intents ====================

    public List<IntentView> listIntents() {
        return intentRepository.findAll().stream()
                .sorted(Comparator.comparing(AaIntent::getId))
                .map(RegistryAdminService::toView)
                .toList();
    }

    /**
     * Create an intent. This is the 2b/2c payoff surface: the description
     * becomes the classifier rule, the filtered message covers project gating,
     * and no Java change is needed anywhere.
     */
    @Transactional
    public IntentView createIntent(String code, String displayName, String description,
                                   String filteredMessageTemplate) {
        String normalized = normalizeCode(code, "intent code");
        if (RESERVED_INTENT_CODES.contains(normalized)) {
            throw new ConfigRegistryException(
                    "'" + normalized + "' is a reserved sentinel and cannot be an intent");
        }
        intentRepository.findByCode(normalized).ifPresent(i -> {
            throw new ConfigRegistryException("Intent '" + normalized + "' already exists");
        });
        if (description == null || description.isBlank()) {
            throw new ConfigRegistryException("description is required - without a classification "
                    + "rule the classifier never returns this intent");
        }
        AaIntent intent = intentRepository.save(AaIntent.builder()
                .code(normalized)
                .displayName(displayName == null || displayName.isBlank() ? normalized : displayName.trim())
                .description(description)
                .filteredMessageTemplate(blankToNull(filteredMessageTemplate))
                .active(true)
                .build());
        log.info("[RegistryAdmin] Intent '{}' created - classifier picks it up on the next call", normalized);
        return toView(intent);
    }

    @Transactional
    public IntentView updateIntent(String code, String displayName, String description,
                                   String filteredMessageTemplate, Boolean active) {
        AaIntent intent = intentRepository.findByCode(normalizeCode(code, "intent code"))
                .orElseThrow(() -> new ConfigRegistryException("Unknown intent '" + code + "'"));
        if (displayName != null && !displayName.isBlank()) {
            intent.setDisplayName(displayName.trim());
        }
        if (description != null) {
            if (description.isBlank()) {
                throw new ConfigRegistryException("description must not be blank - the classifier "
                        + "would silently stop returning this intent");
            }
            intent.setDescription(description);
        }
        if (filteredMessageTemplate != null) {
            intent.setFilteredMessageTemplate(blankToNull(filteredMessageTemplate));
        }
        if (active != null) {
            intent.setActive(active);
        }
        intent.setUpdatedAt(Instant.now());
        intentRepository.save(intent);
        log.info("[RegistryAdmin] Intent '{}' updated (active: {})", intent.getCode(), intent.getActive());
        return toView(intent);
    }

    // ==================== brand attributes ====================

    public List<BrandAttributeView> listBrandAttributes() {
        Map<Long, String> projectCodes = new LinkedHashMap<>();
        projectRepository.findAll().forEach(p -> projectCodes.put(p.getId(), p.getCode()));
        List<BrandAttributeView> views = new ArrayList<>();
        brandAttributeRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((AaBrandAttribute a) -> a.getProjectId() == null ? -1L : a.getProjectId())
                        .thenComparing(AaBrandAttribute::getAttrKey))
                .forEach(a -> views.add(new BrandAttributeView(
                        a.getId(),
                        a.getProjectId() != null ? projectCodes.get(a.getProjectId()) : null,
                        a.getAttrKey(),
                        a.getAttrValue())));
        return views;
    }

    /** Insert or update the (project, key) value. Live immediately (cache evicted). */
    @Transactional
    public BrandAttributeView upsertBrandAttribute(String projectCode, String attrKey, String attrValue) {
        if (attrKey == null || attrKey.isBlank()) {
            throw new ConfigRegistryException("attrKey is required");
        }
        if (attrValue == null || attrValue.isBlank()) {
            throw new ConfigRegistryException("attrValue is required - an empty brand value would "
                    + "render an empty string into live prompts");
        }
        String key = attrKey.trim();
        Long projectId = null;
        String normalizedProject = null;
        if (projectCode != null && !projectCode.isBlank()) {
            AaProject project = requireProject(projectCode);
            projectId = project.getId();
            normalizedProject = project.getCode();
        }

        AaBrandAttribute row = (projectId != null
                ? brandAttributeRepository.findFirstByProjectIdAndAttrKey(projectId, key)
                : brandAttributeRepository.findFirstByProjectIdIsNullAndAttrKey(key))
                .orElseGet(AaBrandAttribute::new);
        row.setProjectId(projectId);
        row.setAttrKey(key);
        row.setAttrValue(attrValue);
        brandAttributeRepository.save(row);
        brandService.evictAll();
        log.info("[RegistryAdmin] Brand attribute '{}' set for {} - live now",
                key, normalizedProject != null ? normalizedProject : "GLOBAL DEFAULT");
        return new BrandAttributeView(row.getId(), normalizedProject, key, attrValue);
    }

    @Transactional
    public void deleteBrandAttribute(Long id) {
        AaBrandAttribute row = brandAttributeRepository.findById(id)
                .orElseThrow(() -> new ConfigRegistryException("No brand attribute with id " + id));
        brandAttributeRepository.delete(row);
        brandService.evictAll();
        log.info("[RegistryAdmin] Brand attribute '{}' (project id {}) deleted",
                row.getAttrKey(), row.getProjectId());
    }

    // ==================== helpers ====================

    private ProjectView toView(AaProject project) {
        List<AaProjectIntent> rows = projectIntentRepository.findByProjectId(project.getId());
        Map<Long, String> intentCodes = new LinkedHashMap<>();
        intentRepository.findAll().forEach(i -> intentCodes.put(i.getId(), i.getCode()));
        List<String> enabled = rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                .map(r -> intentCodes.get(r.getIntentId()))
                .filter(c -> c != null)
                .sorted()
                .toList();
        return new ProjectView(project.getCode(), project.getDisplayName(),
                Boolean.TRUE.equals(project.getActive()), !rows.isEmpty(), enabled);
    }

    private static IntentView toView(AaIntent intent) {
        return new IntentView(intent.getCode(), intent.getDisplayName(),
                Boolean.TRUE.equals(intent.getActive()),
                intent.getDescription(), intent.getFilteredMessageTemplate());
    }

    private AaProject requireProject(String code) {
        return projectRepository.findByCode(normalizeCode(code, "project code"))
                .orElseThrow(() -> new ConfigRegistryException("Unknown project '" + code + "'"));
    }

    private static String normalizeCode(String code, String what) {
        if (code == null || code.isBlank()) {
            throw new ConfigRegistryException(what + " is required");
        }
        String normalized = code.trim().toUpperCase();
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new ConfigRegistryException(
                    "Invalid " + what + " '" + code + "' - use letters, digits and underscores, "
                            + "starting with a letter (e.g. ROOM_BOOKING)");
        }
        return normalized;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
