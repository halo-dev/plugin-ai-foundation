package run.halo.aifoundation.tool;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Defines a convenient selection of related tools within a {@link ToolGroup}.
 *
 * <p>Use local definition names such as {@code list_posts} and {@code get_post}, not catalog IDs. Every referenced
 * registration must belong to the same group. Presets only assist selection; disabled, unconfigured, or unauthorized
 * tools do not gain access by belonging to a preset.
 *
 * <pre>{@code
 * ToolPreset readOnly = ToolPreset.builder()
 *     .name("read-only")
 *     .displayName("Read-only queries")
 *     .toolNames(List.of("list_posts", "get_post"))
 *     .build();
 * }</pre>
 */
@Value
public class ToolPreset {
    /** Stable name unique within the containing group. */
    String name;
    /** Administrator-facing title, falling back to {@link #name} when blank. */
    String displayName;
    /** Optional explanation of when this selection is useful. */
    String description;
    /** Nonempty selection of distinct provider-local tool names in the containing group. */
    List<String> toolNames;

    /**
     * Creates a preset with an immutable snapshot of its local tool names.
     *
     * @param name stable preset name
     * @param displayName administrator-facing title
     * @param description optional selection explanation
     * @param toolNames member tool names, validated when registered
     */
    @Builder
    public ToolPreset(String name, String displayName, String description, List<String> toolNames) {
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
    }
}
