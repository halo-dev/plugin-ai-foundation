package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Category;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Verifies category lookup and mutation contracts using the native extension client boundary.
 *
 * <p>Examples cover lookup projections, unique slugs, omitted and cleared fields, unsupported input and concurrent
 * metadata versions. These cases exercise the business provider directly; registry authorization is verified by the
 * shared tool service tests.
 */
class HaloCategoryToolsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloCategoryTools tools = new HaloCategoryTools(client, access);

    @BeforeEach
    void setUp() {
        when(access.requireResource(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(client.countBy(eq(Category.class), any())).thenReturn(Mono.just(0L));
        when(client.create(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.getMetadata().setVersion(1L);
            return Mono.just(category);
        });
        when(client.update(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.getMetadata().setVersion(category.getMetadata().getVersion() + 1);
            return Mono.just(category);
        });
    }

    @Test
    void createsARootCategoryWithExplicitDefaultsAndBoundedEditableFields() {
        var result = map(tools.createCategory(Map.of(
                        "displayName",
                        "新闻",
                        "slug",
                        "news",
                        "description",
                        "News category",
                        "cover",
                        "/upload/news.png",
                        "priority",
                        7,
                        "hideFromList",
                        true,
                        "preventParentPostCascadeQuery",
                        true))
                .block());
        assertThat(result)
                .containsEntry("displayName", "新闻")
                .containsEntry("slug", "news")
                .containsEntry("parent", "")
                .containsEntry("priority", 7)
                .containsEntry("hiddenFromList", true)
                .containsEntry("metadataVersion", 1L)
                .containsEntry("saved", true);
        assertThat((String) result.get("name")).startsWith("ai-category-");
        var captor = ArgumentCaptor.forClass(Category.class);
        verify(client).create(captor.capture());
        assertThat(captor.getValue().getSpec().isPreventParentPostCascadeQuery())
                .isTrue();
        assertThat(captor.getValue().getSpec().getChildren()).isNull();
        assertThat(captor.getValue().getMetadata().getAnnotations()).isNull();
    }

    @Test
    void createsAChildOnlyAfterCheckingTheWholeExistingParentChain() {
        var parent = category("parent", 2);
        parent.getSpec().setParent("root");
        when(client.fetch(Category.class, "parent")).thenReturn(Mono.just(parent));
        when(client.fetch(Category.class, "root")).thenReturn(Mono.just(category("root", 1)));
        assertThat(map(tools.createCategory(Map.of("displayName", "Child", "slug", "child", "parent", "parent"))
                        .block()))
                .containsEntry("parent", "parent");
        verify(client).fetch(Category.class, "root");
    }

    @Test
    void deniedAncestorPermissionStopsBeforeReadingThatAncestorOrWritingTheChild() {
        var parent = category("parent", 2);
        parent.getSpec().setParent("root");
        when(client.fetch(Category.class, "parent")).thenReturn(Mono.just(parent));
        when(access.requireResource("content.halo.run", "categories", "get", "root"))
                .thenReturn(Mono.error(new AccessDeniedException("ancestor denied")));
        StepVerifier.create(tools.createCategory(Map.of("displayName", "Child", "slug", "child", "parent", "parent")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client).fetch(Category.class, "parent");
        verify(client, never()).fetch(Category.class, "root");
        verify(client, never()).countBy(any(), any());
        verify(client, never()).create(any());
    }

    @Test
    void editsCopiesAndPreservesAllOmittedFieldsAndMetadata() {
        var category = category("existing", 7);
        category.getSpec().setDescription("keep description");
        category.getSpec().setCover("/keep.png");
        category.getSpec().setTemplate("keep-template");
        category.getSpec().setHideFromList(true);
        category.getMetadata().setAnnotations(Map.of("user.annotation", "keep"));
        when(client.fetch(Category.class, "existing")).thenReturn(Mono.just(category));
        assertThat(map(tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7, "displayName", "Renamed"))
                        .block()))
                .containsEntry("metadataVersion", 8L);
        var captor = ArgumentCaptor.forClass(Category.class);
        verify(client).update(captor.capture());
        assertThat(captor.getValue().getSpec().getDescription()).isEqualTo("keep description");
        assertThat(captor.getValue().getSpec().getCover()).isEqualTo("/keep.png");
        assertThat(captor.getValue().getSpec().getTemplate()).isEqualTo("keep-template");
        assertThat(captor.getValue().getMetadata().getAnnotations()).containsEntry("user.annotation", "keep");
        assertThat(category.getSpec().getDisplayName()).isEqualTo("existing");
        assertThat(category.getMetadata().getVersion()).isEqualTo(7L);
    }

    @Test
    void refusesSelfParentCyclesAndAlreadyCyclicAncestorChains() {
        when(client.fetch(Category.class, "existing")).thenReturn(Mono.just(category("existing", 7)));
        StepVerifier.create(
                        tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7, "parent", "existing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        var child = category("child", 3);
        child.getSpec().setParent("existing");
        when(client.fetch(Category.class, "child")).thenReturn(Mono.just(child));
        StepVerifier.create(tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7, "parent", "child")))
                .expectError(IllegalArgumentException.class)
                .verify();
        child.getSpec().setParent("child");
        StepVerifier.create(tools.createCategory(Map.of("displayName", "New", "slug", "new", "parent", "child")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void rejectsMovingAHiddenRootUnderAParentUnlessVisibilityIsExplicitlyChanged() {
        var current = category("existing", 7);
        current.getSpec().setHideFromList(true);
        when(client.fetch(Category.class, "existing")).thenReturn(Mono.just(current));
        when(client.fetch(Category.class, "parent")).thenReturn(Mono.just(category("parent", 1)));
        StepVerifier.create(tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7, "parent", "parent")))
                .expectError(IllegalArgumentException.class)
                .verify();
        assertThat(map(tools.updateCategory(Map.of(
                                "name", "existing", "expectedVersion", 7, "parent", "parent", "hideFromList", false))
                        .block()))
                .containsEntry("parent", "parent")
                .containsEntry("hiddenFromList", false);
    }

    @Test
    void refusesDuplicateCategorySlugsBeforeAnyWrite() {
        when(client.countBy(eq(Category.class), any())).thenReturn(Mono.just(1L));
        StepVerifier.create(tools.createCategory(Map.of("displayName", "News", "slug", "news")))
                .expectError(DuplicateKeyException.class)
                .verify();
        verify(client, never()).create(any());
    }

    @Test
    void supportsExplicitOptionalFieldClearingAndMovingACategoryToRoot() {
        var category = category("existing", 7);
        category.getSpec().setParent("old-parent");
        when(client.fetch(Category.class, "existing")).thenReturn(Mono.just(category));
        assertThat(map(tools.updateCategory(Map.of(
                                "name", "existing", "expectedVersion", 7, "parent", "", "description", "", "cover", ""))
                        .block()))
                .containsEntry("parent", "")
                .containsEntry("description", "")
                .containsEntry("cover", "");
    }

    @Test
    void rejectsDeletedCategoriesAndMissingOrDeletedParents() {
        var deleting = category("existing", 7);
        deleting.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.fetch(Category.class, "existing")).thenReturn(Mono.just(deleting));
        StepVerifier.create(tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7, "priority", 1)))
                .expectError(IllegalArgumentException.class)
                .verify();
        when(client.fetch(Category.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(tools.createCategory(Map.of("displayName", "Child", "slug", "child", "parent", "missing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(tools.createCategory(Map.of("displayName", "Child", "slug", "child", "parent", "existing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "metadata", "children", "template", "url", "callerName"})
    void rejectsUnsupportedFieldsBeforeReadingOrWriting(String field) {
        var input = new HashMap<String, Object>(Map.of("displayName", "Name", "slug", "slug"));
        input.put(field, "unapproved");
        StepVerifier.create(tools.createCategory(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
    }

    @Test
    void validatesBooleansIntegerBoundsTextUrlsAndRequiredFieldsAuthoritatively() {
        var invalid = List.<Map<String, Object>>of(
                Map.of("displayName", "", "slug", "slug"),
                Map.of("displayName", "Name", "slug", "bad slug"),
                Map.of("displayName", "Name", "slug", "slug", "priority", 0.5),
                Map.of("displayName", "Name", "slug", "slug", "priority", Long.MAX_VALUE),
                Map.of("displayName", "Name", "slug", "slug", "hideFromList", "true"),
                Map.of("displayName", "Name", "slug", "slug", "preventParentPostCascadeQuery", 1),
                Map.of("displayName", "Name", "slug", "slug", "parent", "../parent"),
                Map.of("displayName", "Name", "slug", "slug", "description", "x".repeat(5_001)),
                Map.of("displayName", "Name", "slug", "slug", "cover", "javascript:alert(1)"));
        for (var input : invalid) {
            StepVerifier.create(tools.createCategory(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.updateCategory(Map.of("name", "existing", "expectedVersion", 7)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void searchesCategoriesOnlyUsingRegisteredIndexesAndReturnsBoundedMetadata() {
        var category = category("news", "新闻", "news");
        category.getSpec().setDescription("d".repeat(1_100));
        category.getSpec().setHideFromList(true);
        category.getSpec().setParent("parent-category");
        category.getSpec().setPriority(7);
        category.getMetadata().setVersion(12L);
        category.getStatus().setPostCount(12);
        category.getStatus().setVisiblePostCount(3);
        category.getMetadata().setAnnotations(Map.of("private", "do-not-export"));
        when(client.listBy(eq(Category.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(2, 3, 10, List.of(category))));

        var output = result(tools.listCategories(Map.of("query", " news ", "page", 2, "size", 3)));

        assertThat(output)
                .containsEntry("page", 2)
                .containsEntry("size", 3)
                .containsEntry("total", 10L)
                .containsEntry("hasNext", true);
        var item = items(output).getFirst();
        assertThat(item)
                .containsEntry("name", "news")
                .containsEntry("displayName", "新闻")
                .containsEntry("slug", "news")
                .containsEntry("url", "/categories/news")
                .containsEntry("hiddenFromList", true)
                .containsEntry("visiblePostCount", 3)
                .containsEntry("metadataVersion", 12L)
                .containsEntry("parent", "parent-category")
                .containsEntry("priority", 7)
                .doesNotContainKeys("postCount", "annotations", "template");
        assertThat((String) item.get("description")).hasSize(1_000);
        assertThat(output.toString()).doesNotContain("do-not-export");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Category.class), options.capture(), page.capture());
        assertThat(options.getValue().toCondition().toString())
                .contains("metadata.deletionTimestamp", "metadata.name", "spec.slug", "news")
                .doesNotContain("spec.displayName");
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
        assertThat(page.getValue().getSort().getOrderFor("metadata.name").isAscending())
                .isTrue();
    }

    @Test
    void rejectsInvalidPaginationAndQueriesBeforeTouchingTheExtensionClient() {
        var values = List.<Map<String, Object>>of(
                Map.of("page", 0),
                Map.of("page", 10_001),
                Map.of("page", 1.5),
                Map.of("size", 0),
                Map.of("size", 101),
                Map.of("size", "10"),
                Map.of("size", Double.NaN),
                Map.of("size", Long.MAX_VALUE),
                Map.of("query", 1),
                Map.of("query", "x".repeat(201)));
        for (var input : values) {
            StepVerifier.create(tools.listCategories(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        var withNull = new HashMap<String, Object>();
        withNull.put("size", null);
        StepVerifier.create(tools.listCategories(withNull))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private static Category category(String name, long version) {
        var category = new Category();
        category.setMetadata(metadata(name, version));
        var spec = new Category.CategorySpec();
        spec.setDisplayName(name);
        spec.setSlug(name);
        spec.setPriority(0);
        category.setSpec(spec);
        return category;
    }

    private static Category category(String name, String displayName, String slug) {
        var category = new Category();
        category.setMetadata(metadata(name, 1));
        var spec = new Category.CategorySpec();
        spec.setDisplayName(displayName);
        spec.setSlug(slug);
        category.setSpec(spec);
        var status = new Category.CategoryStatus();
        status.setPermalink("/categories/" + slug);
        category.setStatus(status);
        return category;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> result(Mono<Object> value) {
        return map(value.block());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }
}
