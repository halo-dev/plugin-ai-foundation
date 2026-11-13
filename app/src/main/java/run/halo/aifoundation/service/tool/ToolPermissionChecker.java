package run.halo.aifoundation.service.tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.app.core.extension.Role;
import run.halo.app.core.user.service.RoleService;

/**
 * Evaluates shared tool permission declarations against the caller's effective Halo RBAC rules.
 *
 * <p>The trusted reactive security context supplies the identity. Role dependencies are resolved through
 * {@link RoleService}; missing identity, unavailable role data, and evaluation errors deny access. Discovery checks
 * whether a named operation could be allowed, while execution evaluates the resource names extracted from validated
 * arguments.
 *
 * <p>For example, a declaration targeting {@code items[].name} requires every submitted resource name to match an
 * effective grant. Resource ownership and other domain restrictions remain the responsibility of the business service
 * after this generic resource-rule check.
 */
@Component
@RequiredArgsConstructor
public class ToolPermissionChecker {
    private final RoleService roles;

    /**
     * Checks whether a tool can be offered before concrete model arguments are available.
     *
     * @param permissions declarations that must all pass
     * @return whether the current caller has a matching grant for every declaration
     */
    public Mono<Boolean> canResolve(List<ToolPermission> permissions) {
        return check(permissions, Map.of(), true);
    }

    /**
     * Checks all declarations against the actual resource names in a tool call.
     *
     * @param permissions declarations that must all pass
     * @param input validated tool arguments, or {@code null} for no arguments
     * @return whether every required operation is permitted
     */
    public Mono<Boolean> canExecute(List<ToolPermission> permissions, Map<String, Object> input) {
        return check(permissions, input == null ? Map.of() : input, false);
    }

    /**
     * Rejects execution unless every declared permission passes for the current caller.
     *
     * @param permissions declarations that must all pass
     * @param input validated tool arguments
     * @return completion when permitted, or {@link AccessDeniedException} on denial
     */
    public Mono<Void> requirePermissions(List<ToolPermission> permissions, Map<String, Object> input) {
        return canExecute(permissions, input)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(denied())
                .then();
    }

    /**
     * Checks an operation against an already known resource identity.
     *
     * @param group Halo API group
     * @param resource resource or subresource name
     * @param verb required operation
     * @param name target resource name, or {@code null} for a collection
     * @return whether the caller may perform the operation
     */
    public Mono<Boolean> canAccessResource(String group, String resource, String verb, String name) {
        return principal()
                .map(principal -> allowed(principal, group, resource, verb, name, false))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /**
     * Rejects an operation unless the caller can access its known resource identity.
     *
     * @param group Halo API group
     * @param resource resource or subresource name
     * @param verb required operation
     * @param name target resource name, or {@code null} for a collection
     * @return completion when permitted, or {@link AccessDeniedException} on denial
     */
    public Mono<Void> requireResource(String group, String resource, String verb, String name) {
        return canAccessResource(group, resource, verb, name)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(denied())
                .then();
    }

    /**
     * Resolves the authenticated, non-anonymous caller for ownership-aware operations.
     *
     * @return current username, or {@link AccessDeniedException} when no signed-in caller exists
     */
    public Mono<String> actorName() {
        return authentication()
                .filter(ToolPermissionChecker::signedIn)
                .map(Authentication::getName)
                .switchIfEmpty(denied());
    }

    private Mono<Boolean> check(List<ToolPermission> permissions, Map<String, Object> input, boolean discovery) {
        if (permissions == null) {
            return Mono.just(false);
        }
        if (permissions.isEmpty()) {
            return Mono.just(true);
        }
        return principal()
                .map(principal ->
                        permissions.stream().allMatch(permission -> evaluate(permission, principal, input, discovery)))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    private static boolean evaluate(
            ToolPermission permission, Principal principal, Map<String, Object> input, boolean discovery) {
        if (permission instanceof ToolPermission.AllOf all) {
            return all.permissions().stream().allMatch(child -> evaluate(child, principal, input, discovery));
        }
        if (permission instanceof ToolPermission.AnyOf any) {
            return any.permissions().stream().anyMatch(child -> evaluate(child, principal, input, discovery));
        }
        if (permission instanceof ToolPermission.Resource resource) {
            return evaluateResource(resource, principal, input, discovery);
        }
        return false;
    }

    private static boolean evaluateResource(
            ToolPermission.Resource resource, Principal principal, Map<String, Object> input, boolean discovery) {
        if (resource.resourceNameArgument() == null) {
            return allowed(principal, resource.apiGroup(), resource.resource(), resource.verb(), null, false);
        }
        if (discovery) {
            return allowed(principal, resource.apiGroup(), resource.resource(), resource.verb(), null, true);
        }
        var names = resourceNames(input, resource.resourceNameArgument());
        if (names.isEmpty()) {
            return false;
        }
        return names.stream()
                .allMatch(name ->
                        allowed(principal, resource.apiGroup(), resource.resource(), resource.verb(), name, false));
    }

    private static List<String> resourceNames(Map<String, Object> input, String path) {
        List<Object> values = List.of(input);
        for (var segment : path.split("\\.", -1)) {
            var batch = segment.endsWith("[]");
            var field = batch ? segment.substring(0, segment.length() - 2) : segment;
            var next = new ArrayList<Object>();
            for (var value : values) {
                if (!(value instanceof Map<?, ?> fields) || !fields.containsKey(field)) {
                    return List.of();
                }
                var found = fields.get(field);
                if (!batch) {
                    if (found == null) {
                        return List.of();
                    }
                    next.add(found);
                    continue;
                }
                if (!(found instanceof List<?> entries)) {
                    return List.of();
                }
                next.addAll(entries);
            }
            values = next;
        }
        var names = new ArrayList<String>();
        for (var value : values) {
            if (!(value instanceof String name) || name.isBlank()) {
                return List.of();
            }
            names.add(name);
        }
        return names;
    }

    private static boolean allowed(
            Principal principal, String group, String resource, String verb, String name, boolean discovery) {
        if (!principal.signedIn() && !Set.of("get", "list", "watch").contains(verb)) {
            return false;
        }
        return principal.rules().stream().anyMatch(rule -> matchesRule(rule, group, resource, verb, name, discovery));
    }

    private static boolean matchesRule(
            Role.PolicyRule rule, String group, String resource, String verb, String name, boolean discovery) {
        if (rule == null) {
            return false;
        }
        if (!contains(rule.getApiGroups(), group)) {
            return false;
        }
        if (!contains(rule.getResources(), resource)) {
            return false;
        }
        if (!contains(rule.getVerbs(), verb)) {
            return false;
        }
        return namesMatch(rule.getResourceNames(), name, discovery);
    }

    private Mono<Principal> principal() {
        return authentication().flatMap(authentication -> {
            Set<String> names = authentication instanceof AnonymousAuthenticationToken
                    ? Set.of("anonymous")
                    : authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .filter(authority -> authority.startsWith("ROLE_"))
                            .map(authority -> authority.substring("ROLE_".length()))
                            .collect(Collectors.toSet());
            if (names.isEmpty()) {
                return Mono.just(new Principal(signedIn(authentication), List.of()));
            }
            return roles.listDependenciesFlux(names)
                    .filter(role -> role.getRules() != null)
                    .flatMapIterable(Role::getRules)
                    .collectList()
                    .map(rules -> new Principal(signedIn(authentication), rules));
        });
    }

    private static Mono<Authentication> authentication() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(context -> context.getAuthentication())
                .filter(ToolPermissionChecker::validAuthentication);
    }

    private static boolean validAuthentication(Authentication authentication) {
        if (!authentication.isAuthenticated()) {
            return false;
        }
        var name = authentication.getName();
        return name != null && !name.isBlank();
    }

    private static boolean signedIn(Authentication authentication) {
        return authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean contains(String[] values, String requested) {
        return values != null
                && Arrays.stream(values).anyMatch(value -> "*".equals(value) || Objects.equals(value, requested));
    }

    private static boolean namesMatch(String[] patterns, String name, boolean discovery) {
        if (patterns == null || patterns.length == 0) {
            return true;
        }
        if (discovery) {
            return patterns[0] != null && !patterns[0].isBlank();
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        if (!name.equals(name.strip()) || name.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        if (patterns[0] == null) {
            return false;
        }
        // Halo 2.26 evaluates the first resourceNames pattern, with wildcards per slash segment.
        var patternParts = patterns[0].split("/", -1);
        var nameParts = name.split("/", -1);
        if (patternParts.length != nameParts.length) {
            return false;
        }
        for (int index = 0; index < patternParts.length; index++) {
            if (!matchesNameSegment(patternParts[index], nameParts[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesNameSegment(String pattern, String name) {
        if ("*".equals(pattern) || pattern.equals(name)) {
            return true;
        }
        if (pattern.startsWith("*") && name.endsWith(pattern.substring(1))) {
            return true;
        }
        return pattern.endsWith("*") && name.startsWith(pattern.substring(0, pattern.length() - 1));
    }

    private static <T> Mono<T> denied() {
        return Mono.error(new AccessDeniedException("The current caller lacks the required Halo permission"));
    }

    /**
     * Effective authorization data for a single trusted reactive caller.
     *
     * @param signedIn whether the caller is authenticated and non-anonymous
     * @param rules policy rules collected from effective role dependencies
     */
    private record Principal(boolean signedIn, List<Role.PolicyRule> rules) {}
}
