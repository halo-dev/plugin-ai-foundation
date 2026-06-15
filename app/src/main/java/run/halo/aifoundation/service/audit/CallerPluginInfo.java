package run.halo.aifoundation.service.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
