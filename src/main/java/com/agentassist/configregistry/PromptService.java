package com.agentassist.configregistry;

import com.agentassist.configregistry.entity.AaPromptTemplate;
import com.agentassist.configregistry.entity.AaProject;
import com.agentassist.configregistry.repository.AaProjectRepository;
import com.agentassist.configregistry.repository.AaPromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and renders prompt templates from the registry.
 *
 * <p>Resolution: highest PUBLISHED version for (key, project); if none, highest
 * PUBLISHED version for (key, NULL project). Resolved CONTENT is cached for
 * 60s; rendering is per-call.</p>
 *
 * <p>Substitution walks the TEMPLATE's {@code {snake_case}} placeholders in a
 * single pass — variable values are inserted but never re-scanned, so a value
 * that happens to contain something brace-shaped can neither be substituted
 * again nor be misread as a missing placeholder. Extra variables are ignored
 * (checklist callers compute all brand vars regardless of which the template
 * uses); a placeholder with no variable fails loudly listing every missing
 * name.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private static final long CACHE_TTL_MS = 60_000;

    /**
     * Our placeholders are strictly snake_case: {text}, {latest_message}.
     * Prompt prose containing braces with spaces or capitals — JSON examples,
     * "{credit cards count}" — deliberately does NOT match.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][a-z0-9_]*)}");

    private final AaPromptTemplateRepository templateRepository;
    private final AaProjectRepository projectRepository;

    private final TtlCache<String> templateCache = new TtlCache<>(CACHE_TTL_MS);

    /**
     * Render the template for (key, project) with the given variables.
     * Project override wins over the default when one exists.
     */
    public String render(String templateKey, String projectCode, Map<String, String> vars) {
        String content = resolveContent(templateKey, projectCode);
        return substitute(templateKey, content, vars);
    }

    /** Render the default (NULL-project) template. */
    public String renderDefault(String templateKey, Map<String, String> vars) {
        return render(templateKey, null, vars);
    }

    /** Drop all cached template content — for the portal after publishing. */
    public void evictAll() {
        templateCache.clear();
    }

    /** Normalization every registry lookup shares: null for blank, else trim + uppercase. */
    static String normalizeProject(String projectCode) {
        if (projectCode == null || projectCode.isBlank()) {
            return null;
        }
        return projectCode.trim().toUpperCase();
    }

    private String resolveContent(String templateKey, String projectCode) {
        String normalized = normalizeProject(projectCode);
        String cacheKey = templateKey + "|" + (normalized == null ? "" : normalized);
        try {
            return templateCache.get(cacheKey, k -> loadContent(templateKey, normalized));
        } catch (ConfigRegistryException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("[ConfigRegistry] Failed to load template '{}' (project: {}): {}",
                    templateKey, normalized, e.getMessage());
            throw new ConfigRegistryException(
                    "Config registry unavailable while loading template '" + templateKey + "'", e);
        }
    }

    private String loadContent(String templateKey, String normalizedProject) {
        if (normalizedProject != null) {
            Optional<AaProject> project = projectRepository.findByCode(normalizedProject);
            if (project.isPresent()) {
                Optional<AaPromptTemplate> override =
                        templateRepository.findFirstByTemplateKeyAndProjectIdAndStatusOrderByVersionDesc(
                                templateKey, project.get().getId(), AaPromptTemplate.STATUS_PUBLISHED);
                if (override.isPresent()) {
                    return override.get().getContent();
                }
            }
        }
        return templateRepository
                .findFirstByTemplateKeyAndProjectIdIsNullAndStatusOrderByVersionDesc(
                        templateKey, AaPromptTemplate.STATUS_PUBLISHED)
                .map(AaPromptTemplate::getContent)
                .orElseThrow(() -> {
                    log.error("[ConfigRegistry] No PUBLISHED template for key '{}'"
                            + " (project checked: {})", templateKey, normalizedProject);
                    return new ConfigRegistryException(
                            "No PUBLISHED template for key '" + templateKey + "'"
                            + (normalizedProject != null
                                    ? " (no override for project " + normalizedProject + ", no default)"
                                    : ""));
                });
    }

    private String substitute(String templateKey, String content, Map<String, String> vars) {
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();
        Set<String> missing = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = vars != null ? vars.get(name) : null;
            if (value == null) {
                missing.add(name);
                matcher.appendReplacement(sb, "");
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(sb);
        if (!missing.isEmpty()) {
            log.error("[ConfigRegistry] Template '{}' rendered without required variables: {}",
                    templateKey, missing);
            throw new ConfigRegistryException(
                    "Template '" + templateKey + "' is missing variables: " + missing);
        }
        return sb.toString();
    }
}
