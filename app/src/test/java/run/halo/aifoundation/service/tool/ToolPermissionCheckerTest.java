package run.halo.aifoundation.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.tool.ToolPermission.allOf;
import static run.halo.aifoundation.tool.ToolPermission.anyOf;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolPermission;
import run.halo.app.core.extension.Role;
import run.halo.app.core.user.service.RoleService;

class ToolPermissionCheckerTest {
    private final RoleService roles = mock(RoleService.class);
    private final ToolPermissionChecker checker = new ToolPermissionChecker(roles);
    private final Authentication caller = user("alice", "custom");

    @Test
    void emptyDeclarationIsPublicAndNullDeclarationFailsClosed() {
        assertThat(checker.canResolve(List.of()).block()).isTrue();
        assertThat(checker.canExecute(List.of(), Map.of()).block()).isTrue();
        assertThat(checker.canResolve(null).block()).isFalse();
        assertThat(checker.canExecute(null, Map.of()).block()).isFalse();
        verifyNoInteractions(roles);
    }

    @Test
    void noAuthenticationOrRoleNameAloneGrantsNothing() {
        var permissions = List.<ToolPermission>of(resource("content.halo.run", "posts", "list"));
        assertThat(checker.canResolve(permissions).block()).isFalse();
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.empty());
        assertThat(as(user("admin", "super-role"), checker.canExecute(permissions, Map.of()))
                        .block())
                .isFalse();
        assertThat(as(UsernamePasswordAuthenticationToken.unauthenticated("admin", ""), checker.canResolve(permissions))
                        .block())
                .isFalse();
        StepVerifier.create(checker.actorName())
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void usesInheritedRulesRatherThanUiPermissionClaims() {
        grant(rule("content.halo.run", "posts", null, "get", "list"));
        assertThat(as(caller, checker.canExecute(List.of(resource("content.halo.run", "posts", "list")), Map.of()))
                        .block())
                .isTrue();
        assertThat(as(caller, checker.canExecute(List.of(resource("content.halo.run", "posts", "update")), Map.of()))
                        .block())
                .isFalse();
        assertThat(as(caller, checker.actorName()).block()).isEqualTo("alice");
    }

    @Test
    void namedGrantIsAvailableAtResolutionAndCheckedAgainstActualInputAtExecution() {
        grant(rule("storage.halo.run", "attachments", new String[] {"allowed"}, "get", "list"));
        var permissions = List.<ToolPermission>of(named("storage.halo.run", "attachments", "get", "attachmentName"));
        assertThat(as(caller, checker.canResolve(permissions)).block()).isTrue();
        assertThat(as(caller, checker.canExecute(permissions, Map.of("attachmentName", "allowed")))
                        .block())
                .isTrue();
        for (var input : List.<Map<String, Object>>of(
                Map.of("attachmentName", "other"), Map.of(), Map.of("attachmentName", 1))) {
            assertThat(as(caller, checker.canExecute(permissions, input)).block())
                    .isFalse();
        }
        assertThat(as(caller, checker.canResolve(List.of(resource("storage.halo.run", "attachments", "list"))))
                        .block())
                .isFalse();
        StepVerifier.create(as(caller, checker.requireResource("storage.halo.run", "attachments", "get", "other")))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void allRequiredPermissionsAndAnyAlternativesAreEvaluatedAgainstTheSameCaller() {
        var global = allOf(
                named("content.halo.run", "posts", "get", "name"),
                named("api.console.halo.run", "posts/head-content", "get", "name"));
        var own = allOf(
                named("uc.api.content.halo.run", "posts", "get", "name"),
                named("uc.api.content.halo.run", "posts/draft", "get", "name"));
        var permissions = List.<ToolPermission>of(anyOf(global, own));
        grant(rule("content.halo.run", "posts", null, "get"));
        assertThat(as(caller, checker.canExecute(permissions, Map.of("name", "post")))
                        .block())
                .isFalse();
        grant(
                rule("uc.api.content.halo.run", "posts", null, "get"),
                rule("uc.api.content.halo.run", "posts/draft", null, "get"));
        assertThat(as(caller, checker.canExecute(permissions, Map.of("name", "post")))
                        .block())
                .isTrue();
        assertThat(as(
                                caller,
                                checker.canExecute(
                                        List.of(own, resource("content.halo.run", "tags", "list")),
                                        Map.of("name", "post")))
                        .block())
                .isFalse();
    }

    @Test
    void batchResourceNamesRequireEveryTargetBeforeAnyExecution() {
        grant(rule("", "menuitems", new String[] {"allowed-*"}, "update"));
        var permissions = List.<ToolPermission>of(named("", "menuitems", "update", "items[].name"));
        assertThat(as(caller, checker.canResolve(permissions)).block()).isTrue();
        assertThat(as(
                                caller,
                                checker.canExecute(
                                        permissions,
                                        Map.of(
                                                "items",
                                                List.of(Map.of("name", "allowed-one"), Map.of("name", "allowed-two")))))
                        .block())
                .isTrue();
        for (var input : List.<Map<String, Object>>of(
                Map.of("items", List.of(Map.of("name", "allowed-one"), Map.of("name", "other"))),
                Map.of("items", List.of(Map.of("name", "allowed-one"), Map.of())),
                Map.of("items", List.of()),
                Map.of("items", "allowed-one"),
                Map.of())) {
            assertThat(as(caller, checker.canExecute(permissions, input)).block())
                    .isFalse();
        }
    }

    @ParameterizedTest
    @CsvSource({"draft-*, draft-one, other", "*-mine, post-mine, post-theirs", "exact, exact, different"})
    void matchesNativeResourceNameWildcards(String pattern, String allowed, String denied) {
        grant(rule("content.halo.run", "posts", new String[] {pattern}, "update"));
        assertThat(as(caller, checker.canAccessResource("content.halo.run", "posts", "update", allowed))
                        .block())
                .isTrue();
        assertThat(as(caller, checker.canAccessResource("content.halo.run", "posts", "update", denied))
                        .block())
                .isFalse();
    }

    @Test
    void doesNotBroadenHalo226FirstResourceNamePatternBehavior() {
        grant(rule("storage.halo.run", "attachments", new String[] {"first", "second"}, "get"));
        assertThat(as(caller, checker.canAccessResource("storage.halo.run", "attachments", "get", "first"))
                        .block())
                .isTrue();
        assertThat(as(caller, checker.canAccessResource("storage.halo.run", "attachments", "get", "second"))
                        .block())
                .isFalse();
    }

    @Test
    void namedGrantsDoNotAuthorizeExtraPathSegmentsOrNamesNormalizedByAnExecutor() {
        var permission = List.<ToolPermission>of(named("storage.halo.run", "attachments", "get", "name"));
        grant(rule("storage.halo.run", "attachments", new String[] {"allowed"}, "get"));
        for (var name : List.of("allowed/other", "allowed/", " allowed", "allowed ")) {
            assertThat(as(caller, checker.canExecute(permission, Map.of("name", name)))
                            .block())
                    .isFalse();
        }
        grant(rule("storage.halo.run", "attachments", new String[] {" allowed "}, "get"));
        assertThat(as(caller, checker.canExecute(permission, Map.of("name", " allowed ")))
                        .block())
                .isFalse();
    }

    @Test
    void standardWildcardsMatchNativeGroupsResourcesAndVerbs() {
        grant(rule("*", "*", null, "*"));
        for (var permission : List.of(
                resource("", "menus", "create"),
                named("content.halo.run", "posts", "update", "name"),
                named("api.console.halo.run", "posts/publish", "update", "name"))) {
            assertThat(as(caller, checker.canExecute(List.of(permission), Map.of("name", "post")))
                            .block())
                    .isTrue();
        }
    }

    @Test
    void anonymousUsesItsNativeRoleAndNeverWritesEvenWithOverbroadGrants() {
        when(roles.listDependenciesFlux(Set.of("anonymous"))).thenReturn(Flux.just(role(rule("*", "*", null, "*"))));
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_super-role")));
        assertThat(as(anonymous, checker.canResolve(List.of(resource("content.halo.run", "posts", "list"))))
                        .block())
                .isTrue();
        assertThat(as(anonymous, checker.canResolve(List.of(resource("content.halo.run", "posts", "create"))))
                        .block())
                .isFalse();
        StepVerifier.create(as(anonymous, checker.actorName()))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void missingAndFailedRoleLookupsFailClosedAndRevocationIsNotCached() {
        var permissions = List.<ToolPermission>of(resource("content.halo.run", "tags", "list"));
        grant(rule("content.halo.run", "tags", null, "list"));
        var operation = checker.canResolve(permissions);
        assertThat(as(caller, operation).block()).isTrue();
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.empty());
        assertThat(as(caller, operation).block()).isFalse();
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.error(new IllegalStateException("unavailable")));
        assertThat(as(caller, operation).block()).isFalse();
        StepVerifier.create(as(caller, checker.requirePermissions(permissions, Map.of())))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void ignoresNonResourceOrMalformedGrants() {
        grant(new Role.PolicyRule(null, null, null, new String[] {"*"}, new String[] {"*"}), new Role.PolicyRule());
        assertThat(as(caller, checker.canResolve(List.of(resource("content.halo.run", "posts", "list"))))
                        .block())
                .isFalse();
    }

    private void grant(Role.PolicyRule... rules) {
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.just(role(rules)));
    }

    private static Role role(Role.PolicyRule... rules) {
        var role = new Role();
        role.setRules(List.of(rules));
        return role;
    }

    private static Role.PolicyRule rule(String group, String resource, String[] names, String... verbs) {
        return new Role.PolicyRule(new String[] {group}, new String[] {resource}, names, null, verbs);
    }

    private static Authentication user(String name, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                name, "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static <T> Mono<T> as(Authentication authentication, Mono<T> operation) {
        return operation.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }
}
