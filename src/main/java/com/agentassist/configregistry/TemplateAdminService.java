package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.entity.AaPromptTemplate;
import com.agentassist.configregistry.repository.AaProjectRepository;
import com.agentassist.configregistry.repository.AaPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Portal operations on prompt templates: list variants, fetch the published
 * content of a SPECIFIC variant (no default-fallback — the editor edits
 * exactly what it shows), and publish a new version. Publishing never edits a
 * row in place: it inserts version+1 PUBLISHED and evicts the render cache, so
 * every previous version remains as history and rollback is republishing it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateAdminService {

    public record TemplateSummary(String templateKey, String projectCode,
                                  Integer publishedVersion, Integer latestVersion,
                                  String latestStatus) {
    }

    public record TemplateContent(String templateKey, String projectCode,
                                  Integer version, String content) {
    }

    private final AaPromptTemplateRepository templateRepository;
    private final AaProjectRepository projectRepository;
    private final PromptService promptService;

    /** Every (key, variant) pair with its published + latest version. */
    public List<TemplateSummary> list() {
        Map<Long, String> projectCodes = new LinkedHashMap<>();
        projectRepository.findAll().forEach(p -> projectCodes.put(p.getId(), p.getCode()));

        record VariantKey(String key, Long projectId) {
        }
        Map<VariantKey, List<AaPromptTemplate>> grouped = new LinkedHashMap<>();
        templateRepository.findAll().stream()
                .sorted(Comparator.comparing(AaPromptTemplate::getTemplateKey)
                        .thenComparing(t -> t.getVersion() == null ? 0 : t.getVersion()))
                .forEach(t -> grouped
                        .computeIfAbsent(new VariantKey(t.getTemplateKey(), t.getProjectId()),
                                k -> new ArrayList<>())
                        .add(t));

        List<TemplateSummary> summaries = new ArrayList<>();
        grouped.forEach((variant, rows) -> {
            Integer published = rows.stream()
                    .filter(r -> AaPromptTemplate.STATUS_PUBLISHED.equals(r.getStatus()))
                    .map(AaPromptTemplate::getVersion)
                    .max(Comparator.naturalOrder()).orElse(null);
            AaPromptTemplate latest = rows.get(rows.size() - 1);
            summaries.add(new TemplateSummary(
                    variant.key(),
                    variant.projectId() != null ? projectCodes.get(variant.projectId()) : null,
                    published,
                    latest.getVersion(),
                    latest.getStatus()));
        });
        return summaries;
    }

    /** Published content of EXACTLY this variant (no fallback to the default). */
    public Optional<TemplateContent> get(String templateKey, String projectCode) {
        Long projectId = resolveProjectId(projectCode);
        Optional<AaPromptTemplate> row = projectId != null
                ? templateRepository.findFirstByTemplateKeyAndProjectIdAndStatusOrderByVersionDesc(
                        templateKey, projectId, AaPromptTemplate.STATUS_PUBLISHED)
                : templateRepository.findFirstByTemplateKeyAndProjectIdIsNullAndStatusOrderByVersionDesc(
                        templateKey, AaPromptTemplate.STATUS_PUBLISHED);
        return row.map(t -> new TemplateContent(templateKey,
                projectCode == null || projectCode.isBlank() ? null : projectCode.trim().toUpperCase(),
                t.getVersion(), t.getContent()));
    }

    /** Insert the next version as PUBLISHED and make it live (cache evicted). */
    @Transactional
    public TemplateContent publish(String templateKey, String projectCode, String content, String author) {
        if (templateKey == null || templateKey.isBlank()) {
            throw new ConfigRegistryException("templateKey is required");
        }
        if (content == null || content.isBlank()) {
            throw new ConfigRegistryException("content must not be empty - an empty prompt is never valid");
        }
        Long projectId = resolveProjectId(projectCode);

        int nextVersion = (projectId != null
                ? templateRepository.findFirstByTemplateKeyAndProjectIdOrderByVersionDesc(templateKey, projectId)
                : templateRepository.findFirstByTemplateKeyAndProjectIdIsNullOrderByVersionDesc(templateKey))
                .map(t -> t.getVersion() + 1)
                .orElse(1);

        AaPromptTemplate row = AaPromptTemplate.builder()
                .templateKey(templateKey.trim())
                .projectId(projectId)
                .version(nextVersion)
                .content(content)
                .status(AaPromptTemplate.STATUS_PUBLISHED)
                .createdBy(author)
                .build();
        templateRepository.save(row);
        promptService.evictAll();

        log.info("[ConfigRegistry] Published {} v{} (project: {}) by {}",
                templateKey, nextVersion, projectCode != null ? projectCode : "default", author);
        return new TemplateContent(row.getTemplateKey(),
                projectCode == null || projectCode.isBlank() ? null : projectCode.trim().toUpperCase(),
                nextVersion, content);
    }

    /** Null for blank/default; the project's id otherwise — unknown codes are an error here. */
    private Long resolveProjectId(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            return null;
        }
        return projectRepository.findByCode(projectCode.trim().toUpperCase())
                .map(AaProject::getId)
                .orElseThrow(() -> new ConfigRegistryException(
                        "Unknown project '" + projectCode + "' - create the project before adding an override"));
    }
}
