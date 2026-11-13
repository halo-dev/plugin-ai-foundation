package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolPermissionDeniedBehavior;
import run.halo.app.core.extension.Menu;
import run.halo.app.core.extension.MenuItem;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class HaloMenuToolsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloMenuTools tools = new HaloMenuTools(client, access);

    @BeforeEach
    void allowOperationPermissions() {
        when(access.requireResource(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(access.requirePost(any(), any(), any(), any(Post.class))).thenReturn(Mono.empty());
        when(access.postScope(any(), any(), any(), any())).thenReturn(Mono.just(""));
        when(access.requirePermissions(any(), any())).thenReturn(Mono.empty());
    }

    @Test
    void advertisesSixToolsInOneGroupWithReadAndMaintenancePresets() {
        var registrations = tools.tools().collectList().block();
        assertThat(registrations)
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactly(
                        "list_menus",
                        "get_menu",
                        "create_menu",
                        "create_menu_item",
                        "update_menu_item",
                        "reorder_menu_items");
        var group = registrations.getFirst().getGroup();
        assertThat(group.getName()).isEqualTo("menus");
        assertThat(group.getPresets()).extracting(preset -> preset.getName()).containsExactly("read", "maintain");
        assertThat(group.getPresets().getFirst().getToolNames()).containsExactly("list_menus", "get_menu");
        assertThat(group.getPresets().getLast().getToolNames()).hasSize(6);
        for (var registration : registrations) {
            assertThat(registration.getGroup()).isSameAs(group);
            assertThat(registration.getCategory()).isEqualTo("site");
            assertThat(registration.getDefinition().getDescription()).contains("Halo permissions", "untrusted");
            assertThat(registration
                            .getDefinition()
                            .getApprovalPolicy()
                            .requiresApproval(ToolExecutionContext.builder()
                                    .input(Map.of())
                                    .build()))
                    .isEqualTo(!registration.getDefinition().getName().startsWith("list_")
                            && !registration.getDefinition().getName().startsWith("get_"));
        }
        assertThat(registrations.getLast().getDefinition().getDescription()).contains("not atomic", "earlier items");
        assertThat(registrations)
                .allSatisfy(tool -> assertThat(tool.getPermissionDeniedBehavior())
                        .isEqualTo(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE));
        assertThat(registrations.get(0).getRequiredPermissions()).containsExactly(resource("", "menus", "list"));
        assertThat(registrations.get(1).getRequiredPermissions())
                .containsExactly(named("", "menus", "get", "name"), resource("", "menuitems", "list"));
        assertThat(registrations.get(2).getRequiredPermissions()).containsExactly(resource("", "menus", "create"));
        assertThat(registrations.get(3).getRequiredPermissions())
                .containsExactly(
                        named("", "menus", "get", "menuName"),
                        resource("", "menuitems", "list"),
                        resource("", "menuitems", "create"));
        assertThat(registrations.get(4).getRequiredPermissions())
                .containsExactly(
                        named("", "menus", "get", "menuName"),
                        resource("", "menuitems", "list"),
                        named("", "menuitems", "get", "name"),
                        named("", "menuitems", "update", "name"));
        assertThat(registrations.get(5).getRequiredPermissions())
                .containsExactly(
                        named("", "menus", "get", "menuName"),
                        resource("", "menuitems", "list"),
                        named("", "menuitems", "update", "items[].name"));
    }

    @Test
    void deniedParentMenuReadCannotFetchItsItems() {
        when(access.requireResource("", "menus", "get", "menu"))
                .thenReturn(Mono.error(new AccessDeniedException("menu denied")));
        StepVerifier.create(tools.getMenu(Map.of("name", "menu")))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void aReferencedPrivatePageRequiresItsOwnReadPermissionBeforeCreatingNavigation() {
        fixture();
        when(access.requirePermissions(eq(List.of(HaloPageTools.PAGE_HEAD)), any()))
                .thenReturn(Mono.error(new AccessDeniedException("page denied")));
        StepVerifier.create(tools.createMenuItem(
                        Map.of("menuName", "menu", "targetRef", Map.of("kind", "SinglePage", "name", "private-page"))))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(access).requirePermissions(List.of(HaloPageTools.PAGE_HEAD), Map.of("name", "private-page"));
        verify(client, never()).fetch(eq(SinglePage.class), any());
        verify(client, never()).create(any());
    }

    @Test
    void reorderPreflightsEveryChangedItemPermissionBeforeTheFirstWrite() {
        fixture(item("first", null, 0, 7), item("second", null, 1, 8));
        when(access.requireResource("", "menuitems", "update", "first"))
                .thenReturn(Mono.error(new AccessDeniedException("first denied")));
        StepVerifier.create(tools.reorderMenuItems(
                        Map.of("menuName", "menu", "items", List.of(order("second", 8), order("first", 7)))))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(access).requireResource("", "menuitems", "update", "second");
        verify(access).requireResource("", "menuitems", "update", "first");
        verify(client, never()).update(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void listSearchesDisplayNameWithThePublicPredicateApiInsteadOfAnUnregisteredIndex() {
        when(client.list(eq(Menu.class), any(), any(), eq(2), eq(3)))
                .thenReturn(Mono.just(new ListResult<>(2, 3, 10, List.of(menu("primary", "主导航")))));
        var result = result(tools.listMenus(Map.of("page", 2, "size", 3, "query", " 主导航 ")));
        assertThat(result)
                .containsEntry("page", 2)
                .containsEntry("size", 3)
                .containsEntry("total", 10L)
                .containsEntry("hasNext", true);
        assertThat(items(result))
                .hasSize(1)
                .first()
                .satisfies(item -> assertThat(item)
                        .containsEntry("name", "primary")
                        .containsEntry("displayName", "主导航")
                        .containsEntry("metadataVersion", 7L));
        var filter = ArgumentCaptor.forClass(Predicate.class);
        verify(client).list(eq(Menu.class), filter.capture(), any(), eq(2), eq(3));
        Predicate<Menu> predicate = filter.getValue();
        assertThat(predicate.test(menu("different", "主导航"))).isTrue();
        assertThat(predicate.test(menu("主导航", "Other"))).isTrue();
        assertThat(predicate.test(menu("other", "Other"))).isFalse();
        var deleted = menu("deleted", "主导航");
        deleted.getMetadata().setDeletionTimestamp(Instant.now());
        assertThat(predicate.test(deleted)).isFalse();
        verify(client, never()).listBy(eq(Menu.class), any(), any());
    }

    @Test
    void getReturnsTheCompleteCanonicalTreeInSiblingOrderAndNoRawMetadata() {
        var second = item("second", null, 20, 4);
        var first = item("first", null, 10, 3);
        var child = item("child", "first", 0, 5);
        child.getMetadata().setAnnotations(Map.of("secret", "private-token"));
        child.getSpec().setHref("https://user:secret@example.com/private");
        var status = new MenuItem.MenuItemStatus();
        status.setDisplayName("Resolved page");
        status.setHref("/page");
        child.setStatus(status);
        fixture(first, second, child);
        var result = result(tools.getMenu(Map.of("name", "menu")));
        assertThat(result).containsEntry("itemCount", 3).containsEntry("name", "menu");
        assertThat(items(result)).extracting(value -> value.get("name")).containsExactly("first", "second");
        var children = children(items(result).getFirst());
        assertThat(children)
                .hasSize(1)
                .first()
                .satisfies(value -> assertThat(value)
                        .containsEntry("name", "child")
                        .containsEntry("parentName", "first")
                        .containsEntry("metadataVersion", 5L)
                        .containsEntry("resolvedDisplayName", "Resolved page")
                        .containsEntry("resolvedHref", "/page")
                        .containsEntry("href", ""));
        assertThat(result.toString()).doesNotContain("private-token", "user:secret");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listAll(eq(MenuItem.class), options.capture(), any());
        assertThat(options.getValue().toCondition().toString())
                .contains("spec.menuName", "menu", "metadata.deletionTimestamp");
    }

    @Test
    void getFailsExplicitlyForOversizeOrBrokenTreesRatherThanOmittingChildren() {
        fixture(item("orphan", "missing", 0, 1));
        StepVerifier.create(tools.getMenu(Map.of("name", "menu")))
                .expectErrorMessage("Menu parent does not belong to this menu")
                .verify();
        fixture(item("a", "b", 0, 1), item("b", "a", 0, 1));
        StepVerifier.create(tools.getMenu(Map.of("name", "menu")))
                .expectErrorMessage("Menu hierarchy would contain a cycle")
                .verify();
        fixture(IntStream.range(0, 501)
                .mapToObj(i -> item("item-" + i, null, i, 1))
                .toArray(MenuItem[]::new));
        StepVerifier.create(tools.getMenu(Map.of("name", "menu")))
                .expectErrorMessage("Menu has more than 500 items; use Halo Console")
                .verify();
    }

    @Test
    void createsAnEmptyMenuWithoutTouchingExistingMenusOrLegacyItemOwnership() {
        var sent = new AtomicReference<Menu>();
        when(client.create(any(Menu.class))).thenAnswer(invocation -> {
            Menu menu = invocation.getArgument(0);
            sent.set(menu);
            return Mono.just(menu);
        });
        assertThat(result(tools.createMenu(Map.of("displayName", "New navigation"))))
                .containsEntry("displayName", "New navigation");
        assertThat(sent.get().getMetadata().getName()).startsWith("ai-menu-");
        assertThat(sent.get().getSpec().getMenuItems()).isNull();
        verify(client, never()).fetch(eq(Menu.class), any());
        verify(client, never()).update(any());
    }

    @Test
    void createsReferencedPageItemsUsingCanonicalOwnershipAndAppendsToTheRequestedParent() {
        fixture(item("parent", null, 0, 1), item("sibling", "parent", 8, 2));
        var page = new SinglePage();
        page.setMetadata(metadata("about", 3));
        page.setSpec(new SinglePage.SinglePageSpec());
        when(client.fetch(SinglePage.class, "about")).thenReturn(Mono.just(page));
        var sent = new AtomicReference<MenuItem>();
        when(client.create(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem item = invocation.getArgument(0);
            sent.set(item);
            return Mono.just(item);
        });
        var result = result(tools.createMenuItem(Map.of(
                "menuName",
                "menu",
                "parentName",
                "parent",
                "targetRef",
                Map.of("kind", "SinglePage", "name", "about"))));
        assertThat(result)
                .containsEntry("menuName", "menu")
                .containsEntry("parentName", "parent")
                .containsEntry("priority", 9);
        assertThat(sent.get().getSpec().getMenuName()).isEqualTo("menu");
        assertThat(sent.get().getSpec().getParent()).isEqualTo("parent");
        assertThat(sent.get().getSpec().getChildren()).isNull();
        assertThat(sent.get().getSpec().getTargetRef().getKind()).isEqualTo("SinglePage");
        assertThat(sent.get().getSpec().getTargetRef().getGroup()).isEqualTo("content.halo.run");
        assertThat(sent.get().getSpec().getTargetRef().getVersion()).isEqualTo("v1alpha1");
        verify(client).fetch(SinglePage.class, "about");
    }

    @Test
    void refusesMissingReferencesOrParentsFromAnotherMenuBeforeCreating() {
        fixture();
        StepVerifier.create(tools.createMenuItem(Map.of(
                        "menuName", "menu", "parentName", "other-menu-parent", "displayName", "Link", "href", "/link")))
                .expectErrorMessage("Parent item does not belong to this menu")
                .verify();
        when(client.fetch(SinglePage.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(tools.createMenuItem(
                        Map.of("menuName", "menu", "targetRef", Map.of("kind", "SinglePage", "name", "missing"))))
                .expectErrorMessage("Referenced content is unavailable")
                .verify();
        verify(client, never()).create(any());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "//evil.example/path",
                "https://user:password@example.com/",
                "file:///etc/passwd",
                "relative-path"
            })
    void rejectsExecutableCredentialedAndAmbiguousCustomNavigationUrls(String href) {
        fixture();
        StepVerifier.create(tools.createMenuItem(Map.of("menuName", "menu", "displayName", "Bad", "href", href)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
    }

    @Test
    void updatePreservesOmittedValuesMetadataAndVersionOnADeepCopy() {
        var current = item("existing", null, 8, 7);
        current.getMetadata().setAnnotations(Map.of("preserve", "private-token"));
        current.getSpec().setTarget(MenuItem.Target.BLANK);
        fixture(current);
        var sent = new AtomicReference<MenuItem>();
        when(client.update(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem update = invocation.getArgument(0);
            sent.set(update);
            return Mono.just(update);
        });
        var result = result(tools.updateMenuItem(
                Map.of("menuName", "menu", "name", "existing", "expectedVersion", 7, "displayName", "Updated")));
        assertThat(result).containsEntry("displayName", "Updated").containsEntry("metadataVersion", 7L);
        assertThat(sent.get()).isNotSameAs(current);
        assertThat(sent.get().getMetadata().getVersion()).isEqualTo(7L);
        assertThat(sent.get().getMetadata().getAnnotations()).containsEntry("preserve", "private-token");
        assertThat(sent.get().getSpec().getHref()).isEqualTo("/existing");
        assertThat(sent.get().getSpec().getTarget()).isEqualTo(MenuItem.Target.BLANK);
        assertThat(sent.get().getSpec().getPriority()).isEqualTo(8);
        assertThat(current.getSpec().getDisplayName()).isEqualTo("existing");
        assertThat(result.toString()).doesNotContain("private-token");
    }

    @Test
    void movingAnItemChecksOwnershipAndCyclesWithoutWriting() {
        fixture(item("parent", null, 0, 7), item("child", "parent", 0, 3));
        for (var parent : List.of("parent", "child")) {
            StepVerifier.create(tools.updateMenuItem(
                            Map.of("menuName", "menu", "name", "parent", "expectedVersion", 7, "parentName", parent)))
                    .expectErrorMessage("Menu hierarchy would contain a cycle")
                    .verify();
        }
        StepVerifier.create(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "parent", "expectedVersion", 7, "parentName", "foreign")))
                .expectErrorMessage("Parent item does not belong to this menu")
                .verify();
        StepVerifier.create(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "foreign", "expectedVersion", 7, "displayName", "X")))
                .expectErrorMessage("Menu item does not belong to this menu")
                .verify();
        verify(client, never()).update(any());
    }

    @Test
    void movingToRootsUsesEmptyParentAndAppendsWithoutChangingOtherItems() {
        var child = item("child", "parent", 0, 3);
        fixture(item("parent", null, 8, 7), child);
        when(client.update(any(MenuItem.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        assertThat(result(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "child", "expectedVersion", 3, "parentName", ""))))
                .containsEntry("parentName", "")
                .containsEntry("priority", 9);
        assertThat(child.getSpec().getParent()).isEqualTo("parent");
        verify(client, times(1)).update(any(MenuItem.class));
    }

    @Test
    void staleOrConcurrentUpdateConflictsDoNotRetryOldIntent() {
        fixture(item("existing", null, 0, 8));
        StepVerifier.create(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "existing", "expectedVersion", 7, "displayName", "Updated")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any());
        fixture(item("existing", null, 0, 7));
        when(client.update(any(MenuItem.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));
        StepVerifier.create(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "existing", "expectedVersion", 7, "displayName", "Updated")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any(MenuItem.class));
    }

    @Test
    void reorderRequiresAnExactSiblingPermutationAndAllVersionsBeforeFirstWrite() {
        fixture(item("first", null, 0, 7), item("second", null, 1, 8), item("child", "first", 0, 3));
        for (var order : List.of(
                List.of(order("first", 7)),
                List.of(order("first", 7), order("child", 3)),
                List.of(order("first", 7), order("first", 7)),
                List.of(order("second", 7), order("first", 7)))) {
            StepVerifier.create(tools.reorderMenuItems(Map.of("menuName", "menu", "items", order)))
                    .expectError(RuntimeException.class)
                    .verify();
        }
        verify(client, never()).update(any());
    }

    @Test
    void reorderUsesSequentialCasAndPreservesEveryOtherFieldAndParent() {
        var first = item("first", "parent", 0, 7);
        var second = item("second", "parent", 1, 8);
        fixture(item("parent", null, 0, 1), first, second, item("other-root", null, 1, 3));
        var saved = new ArrayList<MenuItem>();
        when(client.update(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem update = invocation.getArgument(0);
            saved.add(update);
            return Mono.just(update);
        });
        var result = result(tools.reorderMenuItems(Map.of(
                "menuName", "menu", "parentName", "parent", "items", List.of(order("second", 8), order("first", 7)))));
        assertThat(result)
                .containsEntry("orderedNames", List.of("second", "first"))
                .containsEntry("updatedCount", 2)
                .containsEntry("readAgain", true);
        assertThat(saved).extracting(item -> item.getMetadata().getName()).containsExactly("second", "first");
        assertThat(saved).extracting(item -> item.getSpec().getPriority()).containsExactly(0, 1);
        assertThat(saved)
                .allSatisfy(item -> assertThat(item.getSpec().getParent()).isEqualTo("parent"));
        assertThat(saved.getFirst().getMetadata().getVersion()).isEqualTo(8L);
        assertThat(saved.getLast().getMetadata().getVersion()).isEqualTo(7L);
        assertThat(saved.getFirst().getSpec().getHref()).isEqualTo("/second");
        assertThat(first.getSpec().getPriority()).isEqualTo(0);
        assertThat(second.getSpec().getPriority()).isEqualTo(1);
    }

    @Test
    void aMidReorderConflictExplainsPartialCompletionAndDoesNotRetryOrContinue() {
        fixture(item("first", null, 0, 7), item("second", null, 1, 8), item("third", null, 2, 9));
        when(client.update(any(MenuItem.class))).thenAnswer(invocation -> {
            MenuItem update = invocation.getArgument(0);
            return "first".equals(update.getMetadata().getName())
                    ? Mono.error(new OptimisticLockingFailureException("conflict"))
                    : Mono.just(update);
        });
        StepVerifier.create(tools.reorderMenuItems(Map.of(
                        "menuName",
                        "menu",
                        "items",
                        List.of(order("third", 9), order("first", 7), order("second", 8)))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(OptimisticLockingFailureException.class);
                    assertThat(error.getMessage())
                            .contains("partially applied", "1 items updated", "get_menu", "do not retry");
                })
                .verify();
        verify(client, times(2)).update(any(MenuItem.class));
    }

    @Test
    void unmodifiedOrderingDoesNotWriteAnything() {
        fixture(item("first", null, 0, 7), item("second", null, 1, 8));
        assertThat(result(tools.reorderMenuItems(
                        Map.of("menuName", "menu", "items", List.of(order("first", 7), order("second", 8))))))
                .containsEntry("updatedCount", 0);
        verify(client, never()).update(any());
    }

    @Test
    void rejectsUnsupportedFieldsAndMalformedVersionsBeforeReadingResources() {
        StepVerifier.create(tools.createMenu(Map.of("displayName", "Menu", "owner", "admin")))
                .expectError(IllegalArgumentException.class)
                .verify();
        for (var input : List.<Map<String, Object>>of(
                Map.of("page", 0), Map.of("size", 101), Map.of("page", 1.5), Map.of("query", 1))) {
            StepVerifier.create(tools.listMenus(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.updateMenuItem(
                        Map.of("menuName", "menu", "name", "item", "expectedVersion", 1.1, "displayName", "X")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(tools.reorderMenuItems(Map.of("menuName", "menu", "items", List.of(order("item", 0)))))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private void fixture(MenuItem... items) {
        when(client.fetch(Menu.class, "menu")).thenReturn(Mono.just(menu("menu", "Navigation")));
        when(client.listAll(eq(MenuItem.class), any(), any())).thenReturn(Flux.fromArray(items));
    }

    private static Map<String, Object> order(String name, long version) {
        return Map.of("name", name, "expectedVersion", version);
    }

    private static Menu menu(String name, String displayName) {
        var menu = new Menu();
        menu.setMetadata(metadata(name, 7));
        menu.setSpec(new Menu.Spec());
        menu.getSpec().setDisplayName(displayName);
        return menu;
    }

    private static MenuItem item(String name, String parent, int priority, long version) {
        var item = new MenuItem();
        item.setMetadata(metadata(name, version));
        var spec = new MenuItem.MenuItemSpec();
        spec.setMenuName("menu");
        spec.setParent(parent);
        spec.setDisplayName(name);
        spec.setHref("/" + name);
        spec.setPriority(priority);
        item.setSpec(spec);
        return item;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        metadata.setCreationTimestamp(Instant.EPOCH);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> result(Mono<Object> result) {
        return map(result.block());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(Map<String, Object> item) {
        return (List<Map<String, Object>>) item.get("children");
    }
}
