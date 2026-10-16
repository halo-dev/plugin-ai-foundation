package run.halo.aifoundation.tool;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Declares the Halo resource operations a shared tool requires from its current caller.
 *
 * <p>Permissions use API groups, resource names, and verbs from Halo RBAC policy rules. UI permission labels such as
 * {@code system:posts:manage} are not resource permissions. Register declarations through
 * {@link ToolRegistration#getRequiredPermissions()}; every top-level entry must pass. Combine alternatives with
 * {@link #anyOf(ToolPermission...)}.
 *
 * <pre>{@code
 * ToolPermission readArticles = ToolPermission.resource("content.halo.run", "posts", "list");
 * ToolPermission editArticle = ToolPermission.named("content.halo.run", "posts", "update", "name");
 * ToolPermission editBatch = ToolPermission.named(
 *     "content.halo.run", "posts", "update", "items[].name");
 * ToolPermission eitherReadPath = ToolPermission.anyOf(
 *     readArticles,
 *     ToolPermission.resource("uc.api.content.halo.run", "posts", "list"));
 * }</pre>
 */
public sealed interface ToolPermission {

    /**
     * Requires a collection-level resource operation, such as listing posts.
     *
     * @param apiGroup concrete Halo API group; an empty string denotes the core API group
     * @param resource concrete resource or subresource name, such as {@code posts}
     * @param verb concrete operation, such as {@code list} or {@code create}
     * @return collection-level permission declaration
     */
    static Resource resource(String apiGroup, String resource, String verb) {
        return new Resource(apiGroup, resource, verb, null);
    }

    /**
     * Requires an operation on the resource whose name is supplied by this input argument. Resolution checks whether a
     * matching grant exists; execution checks the actual name. Array paths such as {@code items[].name} require
     * permission for every supplied resource.
     *
     * @param apiGroup concrete Halo API group
     * @param resource concrete resource or subresource name
     * @param verb concrete operation, such as {@code get} or {@code update}
     * @param nameArgument dot-separated input path containing a resource name or names
     * @return name-aware permission declaration
     * @throws IllegalArgumentException if the path or another declaration component is invalid
     */
    static Resource named(String apiGroup, String resource, String verb, String nameArgument) {
        if (nameArgument == null) {
            throw new IllegalArgumentException("Resource name argument is required");
        }
        return new Resource(apiGroup, resource, verb, nameArgument);
    }

    /**
     * Requires every nested permission.
     *
     * @param permissions nonempty permission expressions without {@code null} entries
     * @return a conjunction of the supplied expressions
     */
    static AllOf allOf(ToolPermission... permissions) {
        return new AllOf(permissionList(permissions));
    }

    /**
     * Requires at least one nested permission.
     *
     * @param permissions nonempty permission expressions without {@code null} entries
     * @return a disjunction of the supplied expressions
     */
    static AnyOf anyOf(ToolPermission... permissions) {
        return new AnyOf(permissionList(permissions));
    }

    /**
     * A Halo API group, resource and verb. The empty API group denotes Halo core resources. A null resourceNameArgument
     * requests a collection-level operation. Array paths such as {@code items[].name} check every resource name before
     * the tool executes.
     *
     * @param apiGroup concrete Halo API group, or an empty string for the core group
     * @param resource concrete resource or subresource name
     * @param verb concrete operation name
     * @param resourceNameArgument input path for target names, or {@code null} for a collection
     */
    record Resource(String apiGroup, String resource, String verb, String resourceNameArgument)
            implements ToolPermission {
        /**
         * Rejects wildcard or whitespace-containing declarations before registration.
         *
         * @throws IllegalArgumentException if any declaration component is invalid
         */
        public Resource {
            validate(apiGroup, "API group", true);
            validate(resource, "Resource", false);
            validate(verb, "Verb", false);
            if (resourceNameArgument != null) {
                validate(resourceNameArgument, "Resource name argument", false);
            }
        }
    }

    /**
     * A nonempty conjunction of permissions.
     *
     * @param permissions expressions that must all pass
     */
    record AllOf(List<ToolPermission> permissions) implements ToolPermission {
        /** Validates and snapshots the nested permission declarations. */
        public AllOf {
            permissions = nonemptyPermissions(permissions);
        }
    }

    /**
     * A nonempty disjunction of permissions.
     *
     * @param permissions expressions of which at least one must pass
     */
    record AnyOf(List<ToolPermission> permissions) implements ToolPermission {
        /** Validates and snapshots the alternative permission declarations. */
        public AnyOf {
            permissions = nonemptyPermissions(permissions);
        }
    }

    private static List<ToolPermission> permissionList(ToolPermission[] permissions) {
        if (permissions == null) {
            throw new IllegalArgumentException("Permissions are required");
        }
        return nonemptyPermissions(Arrays.asList(permissions));
    }

    private static List<ToolPermission> nonemptyPermissions(List<ToolPermission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("At least one non-null permission is required");
        }
        if (permissions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("At least one non-null permission is required");
        }
        return List.copyOf(permissions);
    }

    private static void validate(String value, String label, boolean emptyAllowed) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must be a concrete value without whitespace");
        }
        if (!emptyAllowed && value.isEmpty()) {
            throw new IllegalArgumentException(label + " must be a concrete value without whitespace");
        }
        if (value.chars().anyMatch(Character::isWhitespace) || value.contains("*")) {
            throw new IllegalArgumentException(label + " must be a concrete value without whitespace");
        }
    }
}
