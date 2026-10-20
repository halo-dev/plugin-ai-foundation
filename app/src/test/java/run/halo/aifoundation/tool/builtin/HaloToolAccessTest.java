package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.tool.ToolPermission.allOf;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class HaloToolAccessTest {
    private final RoleService roles = mock(RoleService.class);
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloToolAccess access = new HaloToolAccess(roles, client);
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated(
            "alice", "", List.of(new SimpleGrantedAuthority("ROLE_editor")));
    private final ToolPermission global = allOf(
            named("content.halo.run", "posts", "get", "name"),
            named("api.console.halo.run", "posts/head-content", "get", "name"));
    private final ToolPermission own = allOf(
            named("uc.api.content.halo.run", "posts", "get", "name"),
            named("uc.api.content.halo.run", "posts/draft", "get", "name"));

    @Test
    void globalPermissionDoesNotImposeAnOwnerFilter() {
        grant(rule("content.halo.run", "posts", "get"), rule("api.console.halo.run", "posts/head-content", "get"));
        assertThat(as(access.postScope(global, own, Map.of("name", "post"), post("post", "bob")))
                        .block())
                .isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void ownershipIsCheckedOnTheActualObjectEvenAfterASeparateLookupWasAllowed() {
        grant(rule("uc.api.content.halo.run", "posts", "get"), rule("uc.api.content.halo.run", "posts/draft", "get"));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(post("post", "alice")));
        assertThat(as(access.postScope(global, own, Map.of("name", "post"), null))
                        .block())
                .isEqualTo("alice");
        StepVerifier.create(as(access.requirePost(global, own, Map.of("name", "post"), post("post", "bob"))))
                .expectError(AccessDeniedException.class)
                .verify();
        StepVerifier.create(as(access.requirePost(global, own, Map.of("name", "post"), post("other", "alice"))))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void owningAResourceWithoutTheDeclaredPermissionGrantsNoAccess() {
        grant();
        StepVerifier.create(as(access.requirePost(global, own, Map.of("name", "post"), post("post", "alice"))))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void userCenterListsReturnAnOwnerFilterAndCannotUseAPageFallback() {
        grant(rule("uc.api.content.halo.run", "posts", "list"));
        var globalList = resource("content.halo.run", "posts", "list");
        var ownList = resource("uc.api.content.halo.run", "posts", "list");
        assertThat(as(access.postScope(globalList, ownList, Map.of(), null)).block())
                .isEqualTo("alice");
        StepVerifier.create(
                        as(access.postScope(resource("content.halo.run", "singlepages", "list"), null, Map.of(), null)))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void absentOrDeletedOwnedResourcesFailClosed() {
        grant(rule("uc.api.content.halo.run", "posts", "get"), rule("uc.api.content.halo.run", "posts/draft", "get"));
        when(client.fetch(Post.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(as(access.postScope(global, own, Map.of("name", "missing"), null)))
                .expectError(AccessDeniedException.class)
                .verify();
        var deleted = post("post", "alice");
        deleted.getSpec().setDeleted(true);
        StepVerifier.create(as(access.requirePost(global, own, Map.of("name", "post"), deleted)))
                .expectError(AccessDeniedException.class)
                .verify();
    }

    @Test
    void anonymousCanNeverUseOwnershipAsALoggedInActor() {
        grant(rule("uc.api.content.halo.run", "posts", "get"), rule("uc.api.content.halo.run", "posts/draft", "get"));
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_anonymous")));
        StepVerifier.create(access.requirePost(global, own, Map.of("name", "post"), post("post", "anonymous"))
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymous)))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private void grant(Role.PolicyRule... rules) {
        var role = new Role();
        role.setRules(List.of(rules));
        when(roles.listDependenciesFlux(anySet())).thenReturn(Flux.just(role));
    }

    private static Role.PolicyRule rule(String group, String resource, String verb) {
        return new Role.PolicyRule(new String[] {group}, new String[] {resource}, null, null, new String[] {verb});
    }

    private static Post post(String name, String owner) {
        var post = new Post();
        var metadata = new Metadata();
        metadata.setName(name);
        post.setMetadata(metadata);
        post.setSpec(new Post.PostSpec());
        post.getSpec().setOwner(owner);
        return post;
    }

    private <T> Mono<T> as(Mono<T> operation) {
        return operation.contextWrite(ReactiveSecurityContextHolder.withAuthentication(actor));
    }
}
