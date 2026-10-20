package run.halo.aifoundation.tool;

import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Value;

/**
 * Registers an executable tool and its presentation, configuration, and permission requirements.
 *
 * <p>A provider returns registrations from {@link AiToolProvider#tools()}. The definition name is local to that
 * provider's plugin; consumers select the assigned catalog ID instead. Display metadata describes the tool to
 * administrators, while {@link ToolDefinition#getDescription()} guides the model. All entries in
 * {@link #requiredPermissions} must be satisfied.
 *
 * <pre>{@code
 * ToolRegistration registration = ToolRegistration.builder()
 *     .displayName("List articles")
 *     .category("content")
 *     .tags(List.of("articles", "read-only"))
 *     .definition(listArticlesDefinition)
 *     .requiredPermissions(List.of(
 *         ToolPermission.resource("content.halo.run", "posts", "list")))
 *     .permissionDeniedBehavior(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE)
 *     .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class ToolRegistration {
    /** Administrator-facing title, falling back to the definition name when blank. */
    String displayName;
    /** Administrator-facing explanation, falling back to the model description when blank. */
    String displayDescription;
    /**
     * Shared catalog category: {@code content}, {@code web}, {@code media}, {@code site}, {@code utility}, or
     * {@code other}. Unknown categories are displayed under {@code other}.
     */
    String category;
    /** Searchable presentation labels; these do not affect permission checks or model input. */
    List<String> tags;
    /** Whether required provider settings are ready. Defaults to {@code true}. */
    @Builder.Default
    boolean configured = true;
    /** Optional provider-owned settings form and persistence callbacks. */
    ToolConfiguration configuration;
    /** Model description, argument schemas, callbacks, and server-side executor. */
    ToolDefinition definition;
    /** Optional presentation group shared with related registrations from the same plugin. */
    ToolGroup group;

    /**
     * Halo resource permissions required by this tool. Every entry must be satisfied. An empty list declares a public
     * tool. Permission evaluation always uses the current caller.
     */
    @Builder.Default
    List<ToolPermission> requiredPermissions = List.of();

    /**
     * When to reject callers without the declared permissions. Defaults to filtering at resolution; execution checks
     * the current caller regardless of the chosen policy.
     */
    @Builder.Default
    ToolPermissionDeniedBehavior permissionDeniedBehavior = ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE;

    /**
     * Creates a registration with immutable snapshots of tags and permission declarations.
     *
     * @param displayName administrator-facing title
     * @param displayDescription administrator-facing explanation
     * @param category shared catalog category
     * @param tags searchable labels, or {@code null} for none
     * @param configured whether required settings are ready
     * @param configuration optional settings form and persistence callbacks
     * @param definition executable model-facing definition
     * @param group optional related-tool group
     * @param requiredPermissions permission expressions that must all pass
     * @param permissionDeniedBehavior resolution or execution rejection policy
     */
    public ToolRegistration(
            String displayName,
            String displayDescription,
            String category,
            List<String> tags,
            boolean configured,
            ToolConfiguration configuration,
            ToolDefinition definition,
            ToolGroup group,
            List<ToolPermission> requiredPermissions,
            ToolPermissionDeniedBehavior permissionDeniedBehavior) {
        this.displayName = displayName;
        this.displayDescription = displayDescription;
        this.category = category;
        this.tags = tags == null ? List.of() : List.copyOf(tags);
        this.configured = configured;
        this.configuration = configuration;
        this.definition = definition;
        this.group = group;
        this.requiredPermissions = List.copyOf(Objects.requireNonNull(requiredPermissions, "requiredPermissions"));
        this.permissionDeniedBehavior = Objects.requireNonNull(permissionDeniedBehavior, "permissionDeniedBehavior");
    }
}
