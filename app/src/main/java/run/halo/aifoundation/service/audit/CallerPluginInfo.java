package run.halo.aifoundation.service.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import run.halo.app.core.extension.Plugin;

/**
 * Best-effort information about the Halo plugin that is calling AI Foundation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallerPluginInfo {

    /**
     * Whether AI Foundation detected the caller plugin automatically.
     */
    private boolean detected;

    /**
     * Detection mechanism used for this result.
     */
    private String detectionSource;

    /**
     * Halo plugin metadata.name.
     */
    private String pluginName;

    /**
     * Human-readable plugin name.
     */
    private String displayName;

    /**
     * Human-readable plugin description.
     */
    private String description;

    /**
     * Plugin version.
     */
    private String version;

    private String authorName;

    private String authorWebsite;

    private String homepage;

    private String repo;

    private String issues;

    /**
     * Describes a detected caller from its persisted Halo {@link Plugin} resource.
     */
    public static CallerPluginInfo fromPlugin(Plugin plugin, String detectionSource) {
        var spec = plugin.getSpec();
        var author = spec != null ? spec.getAuthor() : null;
        return CallerPluginInfo.builder()
            .detected(true)
            .detectionSource(detectionSource)
            .pluginName(plugin.getMetadata() != null ? plugin.getMetadata().getName() : null)
            .displayName(spec != null ? spec.getDisplayName() : null)
            .description(spec != null ? spec.getDescription() : null)
            .version(spec != null ? spec.getVersion() : null)
            .authorName(author != null ? author.getName() : null)
            .authorWebsite(author != null ? author.getWebsite() : null)
            .homepage(spec != null ? spec.getHomepage() : null)
            .repo(spec != null ? spec.getRepo() : null)
            .issues(spec != null ? spec.getIssues() : null)
            .build();
    }
}
