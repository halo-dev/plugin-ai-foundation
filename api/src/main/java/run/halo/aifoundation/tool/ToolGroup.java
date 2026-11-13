package run.halo.aifoundation.tool;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Groups related tool registrations from one provider plugin for catalog and selector display.
 *
 * <p>Assign the same group metadata to every member registration. A group does not create a model-facing tool or
 * combine executors; selected leaf tools retain their own schemas and permissions. Presets offer convenient selections
 * of those leaves.
 *
 * <pre>{@code
 * ToolGroup articles = ToolGroup.builder()
 *     .name("articles")
 *     .displayName("Articles")
 *     .description("Query and edit site articles")
 *     .presets(List.of(ToolPreset.builder()
 *         .name("read-only")
 *         .displayName("Read-only queries")
 *         .toolNames(List.of("list_posts", "get_post"))
 *         .build()))
 *     .build();
 * }</pre>
 */
@Value
public class ToolGroup {
    /** Stable provider-local group name, using the same naming rules as a local tool name. */
    String name;
    /** Administrator-facing title, falling back to {@link #name} when blank. */
    String displayName;
    /** Optional explanation of the operations collected in this group. */
    String description;
    /** Named selections containing tools from this group only. */
    List<ToolPreset> presets;

    /**
     * Creates a group and snapshots its preset list.
     *
     * @param name stable provider-local group name
     * @param displayName administrator-facing title
     * @param description optional group explanation
     * @param presets named selections, or {@code null} for none
     */
    @Builder
    public ToolGroup(String name, String displayName, String description, List<ToolPreset> presets) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.presets = presets == null ? List.of() : List.copyOf(presets);
    }
}
