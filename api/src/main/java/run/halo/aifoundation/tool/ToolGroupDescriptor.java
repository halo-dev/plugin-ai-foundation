package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Objects;

/**
 * Serializable group metadata with a catalog identity assigned by the owning plugin.
 *
 * <p>For example, plugin {@code article-tools} and local group {@code articles} produce ID
 * {@code article-tools/articles}. Consumers can group tool descriptors by this ID and render the attached presets
 * without constructing provider-local names themselves.
 *
 * @param id stable {@code pluginId/groupName} identity
 * @param displayName administrator-facing title
 * @param description optional group explanation
 * @param presets named leaf selections with catalog IDs
 */
public record ToolGroupDescriptor(
        String id, String displayName, String description, List<ToolPresetDescriptor> presets) {
    /**
     * Validates the identity and snapshots the preset list.
     *
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public ToolGroupDescriptor {
        Objects.requireNonNull(id, "id must not be null");
        presets = presets == null ? List.of() : List.copyOf(presets);
    }
}
