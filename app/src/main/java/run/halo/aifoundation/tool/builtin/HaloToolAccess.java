package run.halo.aifoundation.tool.builtin;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.tool.ToolPermissionChecker;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Applies resource-specific authorization after a tool's declared operation permissions.
 *
 * <p>Generic rule matching is delegated to {@link ToolPermissionChecker}. This adapter additionally handles Halo's
 * distinction between managing all articles and managing the current author's own articles. An own-article permission
 * never grants access to another author's resource.
 *
 * <pre>{@code
 * access.requireResource("storage.halo.run", "attachments", "get", attachmentName)
 *     .then(loadAttachment(attachmentName));
 * }</pre>
 */
@Component
public class HaloToolAccess {
    private final ToolPermissionChecker permissions;
    private final ReactiveExtensionClient client;

    /**
     * Creates the adapter used by built-in tools.
     *
     * @param permissions shared permission evaluator
     * @param client Halo resource client used to verify article ownership
     */
    @Autowired
    public HaloToolAccess(ToolPermissionChecker permissions, ReactiveExtensionClient client) {
        this.permissions = permissions;
        this.client = client;
    }

    HaloToolAccess(RoleService roles, ReactiveExtensionClient client) {
        this(new ToolPermissionChecker(roles), client);
    }

    HaloToolAccess(RoleService roles) {
        this(roles, null);
    }

    /**
     * Requires every declared permission, including names resolved from the tool input.
     *
     * @param required permissions to evaluate together
     * @param input validated tool arguments
     * @return completion when allowed, or an access-denied error
     */
    public Mono<Void> requirePermissions(List<ToolPermission> required, Map<String, Object> input) {
        return permissions.requirePermissions(required, input);
    }

    /**
     * Resolves the authenticated human caller from the reactive security context.
     *
     * @return the username, or an access-denied error for an anonymous caller
     */
    public Mono<String> actorName() {
        return permissions.actorName();
    }

    /**
     * Requires access to a concrete resource, including any name restrictions in Halo rules.
     *
     * @param group API group, or an empty string for the core group
     * @param resource resource name, optionally including a subresource
     * @param verb operation such as {@code get}, {@code create} or {@code update}
     * @param name target {@code metadata.name}, or {@code null} for a collection operation
     * @return completion when allowed, or an access-denied error
     */
    public Mono<Void> requireResource(String group, String resource, String verb, String name) {
        return permissions.requireResource(group, resource, verb, name);
    }

    /**
     * Resolves the ownership scope for an article operation.
     *
     * <p>For example, a list operation receiving an author name must filter its query by that owner. An empty string
     * means the caller has the global permission. When {@code input.name} is present, the named article must also be
     * active and owned by the current author.
     *
     * @param global permission to operate on all articles
     * @param own permission to operate on the caller's articles, or {@code null} if unsupported
     * @param input tool arguments, optionally containing the target {@code name}
     * @param actual already fetched article, or {@code null} to fetch it when needed
     * @return an empty string for unrestricted scope, or the required owner name
     */
    public Mono<String> postScope(ToolPermission global, ToolPermission own, Map<String, Object> input, Post actual) {
        return permissions.canExecute(List.of(global), input).flatMap(allowed -> {
            if (allowed) {
                return Mono.just("");
            }
            if (own == null) {
                return denied();
            }
            return requirePermissions(List.of(own), input)
                    .then(actorName())
                    .flatMap(actor -> ownerScope(input, actual, actor));
        });
    }

    private Mono<String> ownerScope(Map<String, Object> input, Post actual, String actor) {
        var target = input.get("name") instanceof String name ? name : null;
        if (target == null) {
            return Mono.just(actor);
        }
        if (actual == null && client == null) {
            return denied();
        }
        var post = actual == null ? client.fetch(Post.class, target) : Mono.just(actual);
        return post.filter(value -> ownedPost(value, target, actor))
                .map(ignored -> actor)
                .switchIfEmpty(denied());
    }

    private static boolean ownedPost(Post post, String target, String actor) {
        var metadata = post.getMetadata();
        if (metadata == null || !Objects.equals(target, metadata.getName())) {
            return false;
        }
        if (metadata.getDeletionTimestamp() != null) {
            return false;
        }
        var spec = post.getSpec();
        if (spec == null || Boolean.TRUE.equals(spec.getDeleted())) {
            return false;
        }
        return actor.equals(spec.getOwner());
    }

    /**
     * Verifies that a fetched article matches the input and is within the caller's ownership scope.
     *
     * @param global permission to operate on any article
     * @param own permission restricted to the current author, or {@code null}
     * @param input arguments containing the requested article {@code name}
     * @param actual fetched article to authorize
     * @return completion when allowed, or an access-denied error
     */
    public Mono<Void> requirePost(ToolPermission global, ToolPermission own, Map<String, Object> input, Post actual) {
        if (actual == null
                || actual.getMetadata() == null
                || !Objects.equals(input.get("name"), actual.getMetadata().getName())) {
            return denied();
        }
        return postScope(global, own, input, actual).then();
    }

    private static <T> Mono<T> denied() {
        return Mono.error(
                new AccessDeniedException("The current caller lacks Halo permission for this operation or resource"));
    }
}
