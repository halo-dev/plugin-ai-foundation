package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Objects;

/**
 * Serializable preset containing the catalog identities of selected leaf tools.
 *
 * <p>A preset may reference tools currently disabled or awaiting configuration. Consumers should display their state
 * and only select available members. For example, a read-only article preset can contain
 * {@code article-tools/list_posts} and {@code article-tools/get_post}.
 *
 * @param id stable {@code pluginId/groupName/presetName} identity
 * @param displayName administrator-facing title
 * @param description optional selection explanation
 * @param toolIds stable leaf tool identities, including unavailable members
 */
public record ToolPresetDescriptor(String id, String displayName, String description, List<String> toolIds) {
    /**
     * Validates the identity and snapshots the selected IDs.
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public ToolPresetDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
    }
}
