package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Category;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.core.extension.content.Tag;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;

/**
 * Verifies draft creation and editing through independent article and page providers.
 *
 * <p>Both providers must preserve caller identity, resource ownership, source formats and optimistic concurrency
 * checks.
 */
class HaloContentDraftTest {
    private static final String ACTOR = "current-author";
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloDraftContentService drafts = mock(HaloDraftContentService.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloContentToolSettings settings = mock(HaloContentToolSettings.class);
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
    private final HaloPostTools posts = new HaloPostTools(
            client, mock(run.halo.app.content.PostContentService.class), access, settings, drafts, coordinator);
    private final HaloPageTools pages = new HaloPageTools(client, access, settings, drafts, coordinator);

    @BeforeEach
    void allowCurrentCaller() {
        when(access.requirePost(any(), any(), anyMap(), any())).thenReturn(Mono.empty());
        when(access.actorName()).thenReturn(Mono.just(ACTOR));
        when(access.requireResource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(drafts.createPost(any(), any(), anyString())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(drafts.createPage(any(), any(), anyString())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(drafts.updatePost(any(), any(), anyString())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(drafts.updatePage(any(), any(), anyString())).thenAnswer(call -> Mono.just(call.getArgument(0)));
    }

    @Test
    void createsPostDraftWithAuthenticatedOwnerAndCanonicalBody() {
        StepVerifier.create(posts.createPost(createInput()))
                .assertNext(value ->
                        assertThat(map(value)).containsEntry("draftSaved", true).containsEntry("published", false))
                .verifyComplete();
        var post = ArgumentCaptor.forClass(Post.class);
        var body = ArgumentCaptor.forClass(Map.class);
        verify(drafts).createPost(post.capture(), body.capture(), eq(ACTOR));
        assertThat(post.getValue().getMetadata().getName()).startsWith("ai-post-");
        assertThat(post.getValue().getSpec().getOwner()).isEqualTo(ACTOR);
        assertThat(post.getValue().getSpec().getPublish()).isFalse();
        assertThat(post.getValue().getSpec().getReleaseSnapshot()).isNull();
        assertThat(post.getValue().getSpec().getVisible()).isEqualTo(Post.VisibleEnum.PUBLIC);
        assertThat(body.getValue())
                .containsEntry("rawType", "MARKDOWN")
                .containsEntry("raw", "# Hello\n")
                .containsEntry("content", "<h1>Hello</h1>\n")
                .doesNotContainKey("version");
    }

    @Test
    void createsSinglePageWithTheCurrentActorAndNativeDefaults() {
        pages.createSinglePage(createInput()).block();
        var page = ArgumentCaptor.forClass(SinglePage.class);
        verify(drafts).createPage(page.capture(), anyMap(), eq(ACTOR));
        assertThat(page.getValue().getSpec().getOwner()).isEqualTo(ACTOR);
        assertThat(page.getValue().getSpec().getPublish()).isFalse();
        assertThat(page.getValue().getSpec().getDeleted()).isFalse();
        assertThat(page.getValue().getSpec().getExcerpt().getAutoGenerate()).isTrue();
    }

    @Test
    void metadataOnlyEditDoesNotReadOrRecreateContentSnapshots() {
        var current = post(7);
        current.getSpec().setCategories(List.of("old-category"));
        current.getSpec().setTags(List.of("old-tag"));
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(current));
        posts.updatePost(Map.of(
                        "name",
                        "existing",
                        "expectedVersion",
                        7L,
                        "title",
                        "Revised",
                        "categories",
                        List.of(),
                        "tags",
                        List.of(),
                        "cover",
                        ""))
                .block();
        var saved = ArgumentCaptor.forClass(Post.class);
        verify(drafts).updatePost(saved.capture(), isNull(), eq(ACTOR));
        assertThat(saved.getValue()).isNotSameAs(current);
        assertThat(saved.getValue().getMetadata().getVersion()).isEqualTo(7L);
        assertThat(saved.getValue().getSpec().getTitle()).isEqualTo("Revised");
        assertThat(saved.getValue().getSpec().getOwner()).isEqualTo("original-owner");
        assertThat(saved.getValue().getSpec().getCategories()).isEmpty();
        assertThat(saved.getValue().getSpec().getTags()).isEmpty();
        assertThat(saved.getValue().getSpec().getCover()).isEmpty();
        assertThat(saved.getValue().getSpec().getBaseSnapshot()).isEqualTo("base");
        assertThat(saved.getValue().getSpec().getHeadSnapshot()).isEqualTo("head");
        assertThat(saved.getValue().getSpec().getReleaseSnapshot()).isEqualTo("release");
        assertThat(current.getSpec().getTitle()).isEqualTo("Original");
        assertThat(current.getSpec().getCategories()).containsExactly("old-category");
        verify(client, never()).fetch(eq(Snapshot.class), anyString());
        verify(access)
                .requirePost(
                        eq(HaloPostTools.UPDATE_POST_GLOBAL), eq(HaloPostTools.UPDATE_POST_OWN), anyMap(), eq(current));
    }

    @Test
    void rechecksTheActuallyFetchedPostBeforeReadingOrSavingContent() {
        var changedOwner = post(7);
        changedOwner.getSpec().setOwner("someone-else");
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(changedOwner));
        when(access.requirePost(
                        eq(HaloPostTools.UPDATE_POST_GLOBAL),
                        eq(HaloPostTools.UPDATE_POST_OWN),
                        anyMap(),
                        eq(changedOwner)))
                .thenReturn(Mono.error(new AccessDeniedException("ownership changed")));
        StepVerifier.create(posts.updatePost(
                        Map.of("name", "existing", "expectedVersion", 7, "raw", "private replacement")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, never()).fetch(eq(Snapshot.class), anyString());
        verifyNoInteractions(drafts);
    }

    @Test
    void pageMetadataEditPreservesItsSnapshotReferencesAndVersion() {
        var current = page(7);
        when(client.fetch(SinglePage.class, "existing")).thenReturn(Mono.just(current));
        pages.updateSinglePage(Map.of("name", "existing", "expectedVersion", 7L, "title", "Revised"))
                .block();
        var saved = ArgumentCaptor.forClass(SinglePage.class);
        verify(drafts).updatePage(saved.capture(), isNull(), eq(ACTOR));
        assertThat(saved.getValue().getMetadata().getVersion()).isEqualTo(7);
        assertThat(saved.getValue().getSpec().getReleaseSnapshot()).isEqualTo("release");
        assertThat(current.getSpec().getTitle()).isEqualTo("Original");
        verify(client, never()).fetch(eq(Snapshot.class), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "page"})
    void bodyUpdateInheritsMarkdownAndRequiresRenderedContent(String kind) {
        Extension owner;
        if (kind.equals("post")) {
            var current = post(7);
            current.getSpec().setBaseSnapshot("draft");
            current.getSpec().setHeadSnapshot("draft");
            when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(current));
            owner = current;
        } else {
            var current = page(7);
            current.getSpec().setBaseSnapshot("draft");
            current.getSpec().setHeadSnapshot("draft");
            when(client.fetch(SinglePage.class, "existing")).thenReturn(Mono.just(current));
            owner = current;
        }
        when(client.fetch(Snapshot.class, "draft")).thenReturn(Mono.just(snapshot(owner, "draft")));
        var input = Map.<String, Object>of(
                "name", "existing", "expectedVersion", 7L, "raw", "# Revised", "content", "<h1>Revised</h1>");
        (kind.equals("post") ? posts.updatePost(input) : pages.updateSinglePage(input)).block();
        var body = ArgumentCaptor.forClass(Map.class);
        if (kind.equals("post")) verify(drafts).updatePost(any(), body.capture(), eq(ACTOR));
        else verify(drafts).updatePage(any(), body.capture(), eq(ACTOR));
        assertThat(body.getValue())
                .containsEntry("rawType", "MARKDOWN")
                .containsEntry("raw", "# Revised")
                .containsEntry("content", "<h1>Revised</h1>");
        var rawOnly = Map.<String, Object>of("name", "existing", "expectedVersion", 7L, "raw", "# Revised");
        StepVerifier.create(kind.equals("post") ? posts.updatePost(rawOnly) : pages.updateSinglePage(rawOnly))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void rejectsForeignSnapshotsWhenInheritingTheSourceFormat() {
        var current = post(7);
        current.getSpec().setBaseSnapshot("draft");
        current.getSpec().setHeadSnapshot("draft");
        var foreign = post(7);
        foreign.getMetadata().setName("someone-else");
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(current));
        when(client.fetch(Snapshot.class, "draft")).thenReturn(Mono.just(snapshot(foreign, "draft")));
        StepVerifier.create(posts.updatePost(Map.of("name", "existing", "expectedVersion", 7L, "raw", "replacement")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(drafts);
    }

    @Test
    void rejectsStaleVersionsBeforeSnapshotOrDraftWritesAndDoesNotRetryAConflict() {
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(post(8)));
        var input = Map.<String, Object>of("name", "existing", "expectedVersion", 7L, "title", "Revised");
        StepVerifier.create(posts.updatePost(input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verifyNoInteractions(drafts);
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(post(7)));
        doReturn(Mono.error(new OptimisticLockingFailureException("conflict")))
                .when(drafts)
                .updatePost(any(), isNull(), anyString());
        StepVerifier.create(posts.updatePost(input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(drafts, times(1)).updatePost(any(), isNull(), eq(ACTOR));
    }

    @Test
    void rejectsRecycledContentAndMalformedVersionsBeforeSaving() {
        var deleted = post(7);
        deleted.getSpec().setDeleted(true);
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just(deleted));
        var page = page(7);
        page.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.fetch(SinglePage.class, "existing")).thenReturn(Mono.just(page));
        var input = Map.<String, Object>of("name", "existing", "expectedVersion", 7, "title", "Revised");
        StepVerifier.create(posts.updatePost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(pages.updateSinglePage(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(posts.updatePost(Map.of("name", "existing", "expectedVersion", 7.5, "title", "Revised")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(drafts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "publish", "headSnapshot", "releaseSnapshot", "url"})
    void rejectsModelSelectedIdentityAndPublicationFields(String field) {
        var input = new HashMap<>(createInput());
        input.put(field, "untrusted");
        StepVerifier.create(posts.createPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(pages.createSinglePage(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client, drafts);
    }

    @Test
    void validatesTaxonomyReferencesAndPreservesExplicitPresentationFields() {
        var category = new Category();
        category.setMetadata(metadata("category", 1));
        var tag = new Tag();
        tag.setMetadata(metadata("tag", 1));
        when(client.fetch(Category.class, "category")).thenReturn(Mono.just(category));
        when(client.fetch(Tag.class, "tag")).thenReturn(Mono.just(tag));
        var input = new HashMap<>(createInput());
        input.putAll(Map.of(
                "categories",
                List.of("category"),
                "tags",
                List.of("tag"),
                "cover",
                "/upload/cover.png",
                "excerpt",
                "Manual"));
        posts.createPost(input).block();
        var post = ArgumentCaptor.forClass(Post.class);
        verify(drafts).createPost(post.capture(), anyMap(), eq(ACTOR));
        assertThat(post.getValue().getSpec().getCategories()).containsExactly("category");
        assertThat(post.getValue().getSpec().getTags()).containsExactly("tag");
        assertThat(post.getValue().getSpec().getCover()).isEqualTo("/upload/cover.png");
        assertThat(post.getValue().getSpec().getExcerpt().getRaw()).isEqualTo("Manual");
        assertThat(post.getValue().getSpec().getExcerpt().getAutoGenerate()).isFalse();
        when(client.fetch(Category.class, "category")).thenReturn(Mono.empty());
        StepVerifier.create(posts.createPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void selectedCategoryRequiresItsOwnReadPermissionBeforeLookupOrSaving() {
        when(access.requireResource("content.halo.run", "categories", "get", "private-category"))
                .thenReturn(Mono.error(new AccessDeniedException("category access denied")));
        var input = new HashMap<>(createInput());
        input.put("categories", List.of("private-category"));
        StepVerifier.create(posts.createPost(input))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client, drafts);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "javascript:alert(1)",
                "file:///tmp/cover",
                "data:image/png;base64,a",
                "//evil.test/cover",
                "https://user:password@example.com/cover"
            })
    void rejectsUnsafeCoverUrls(String value) {
        var input = new HashMap<>(createInput());
        input.put("cover", value);
        StepVerifier.create(posts.createPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(drafts);
    }

    @ParameterizedTest
    @ValueSource(strings = {"raw", "content"})
    void defaultHtmlCopiesTheOnlyBodyFieldAndAllowsEmptySource(String key) {
        posts.createPost(Map.of("title", "Title", "slug", "title", key, "")).block();
        var body = ArgumentCaptor.forClass(Map.class);
        verify(drafts).createPost(any(), body.capture(), eq(ACTOR));
        assertThat(body.getValue())
                .containsEntry("rawType", "HTML")
                .containsEntry("raw", "")
                .containsEntry("content", "");
    }

    private static Map<String, Object> createInput() {
        return Map.of(
                "title",
                "Title",
                "slug",
                "title",
                "rawType",
                "markdown",
                "raw",
                "# Hello\n",
                "content",
                "<h1>Hello</h1>\n");
    }

    private static Post post(long version) {
        var post = new Post();
        post.setMetadata(metadata("existing", version));
        var spec = new Post.PostSpec();
        post.setSpec(spec);
        spec.setTitle("Original");
        spec.setSlug("original");
        spec.setOwner("original-owner");
        spec.setDeleted(false);
        spec.setPublish(true);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        return post;
    }

    private static SinglePage page(long version) {
        var page = new SinglePage();
        page.setMetadata(metadata("existing", version));
        var spec = new SinglePage.SinglePageSpec();
        page.setSpec(spec);
        spec.setTitle("Original");
        spec.setSlug("original");
        spec.setOwner("original-owner");
        spec.setDeleted(false);
        spec.setPublish(true);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        return page;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        return metadata;
    }

    private static Snapshot snapshot(Extension owner, String name) {
        var snapshot = new Snapshot();
        var meta = metadata(name, 1);
        meta.setAnnotations(Map.of(Snapshot.KEEP_RAW_ANNO, "true"));
        snapshot.setMetadata(meta);
        var spec = new Snapshot.SnapShotSpec();
        snapshot.setSpec(spec);
        spec.setSubjectRef(Ref.of(owner));
        spec.setRawType("MARKDOWN");
        spec.setRawPatch("# Original");
        spec.setContentPatch("<h1>Original</h1>");
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
