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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.content.ContentWrapper;
import run.halo.app.content.PatchUtils;
import run.halo.app.content.PostContentService;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;

/**
 * Verifies article and page query contracts through their respective business providers.
 *
 * <p>Reading a released article or reconstructing a page snapshot must preserve visibility, ownership and configured
 * limits.
 */
class HaloContentQueryTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final PostContentService contentService = mock(PostContentService.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloContentToolSettings settings = mock(HaloContentToolSettings.class);
    private final HaloDraftContentService drafts = mock(HaloDraftContentService.class);
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
    private final HaloPostTools posts =
            new HaloPostTools(client, contentService, access, settings, drafts, coordinator);
    private final HaloPageTools pages = new HaloPageTools(client, access, settings, drafts, coordinator);

    @BeforeEach
    void setUp() {
        when(access.postScope(any(), any(), any(), any())).thenReturn(Mono.just(""));
        when(access.requirePermissions(any(), any())).thenReturn(Mono.empty());
        when(settings.maxContentCharacters(anyString())).thenReturn(Mono.just(100_000));
    }

    @Test
    void listsOnlyPublicPublishedPostsAndUsesBoundedIndexedFilters() {
        var published = post("published", true, Post.VisibleEnum.PUBLIC);
        var draft = post("draft", false, Post.VisibleEnum.PUBLIC);
        var privatePost = post("private", true, Post.VisibleEnum.PRIVATE);
        var recycled = post("recycled", true, Post.VisibleEnum.PUBLIC);
        recycled.getSpec().setDeleted(true);
        when(client.listBy(eq(Post.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(2, 3, 12, List.of(published, draft, privatePost, recycled))));
        StepVerifier.create(posts.listPosts(
                        Map.of("page", 2, "size", 3, "query", "Halo", "category", "news", "tag", "cms")))
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("page")).isEqualTo(2);
                    assertThat(values.get("size")).isEqualTo(3);
                    assertThat(values.get("hasNext")).isEqualTo(true);
                    var items = (List<?>) values.get("items");
                    assertThat(items).hasSize(1);
                    assertThat(((Map<?, ?>) items.getFirst()).get("name")).isEqualTo("published");
                    assertThat(((Map<?, ?>) items.getFirst()).get("metadataVersion"))
                            .isEqualTo(7L);
                })
                .verifyComplete();
        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Post.class), options.capture(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
        assertThat(options.getValue().getFieldSelector().query().toString())
                .contains(
                        "spec.visible",
                        "PUBLIC",
                        "spec.deleted",
                        "spec.categories",
                        "news",
                        "spec.tags",
                        "cms",
                        "spec.title",
                        "Halo",
                        "metadata.deletionTimestamp");
        assertThat(options.getValue().getLabelSelector().toString()).contains(Post.PUBLISHED_LABEL, Post.DELETED_LABEL);
        assertThat(options.getValue().getFieldSelector().query().toString()).doesNotContain("spec.owner");
        verifyNoInteractions(contentService, settings);
    }

    @Test
    void userCenterScopeRestrictsTheIndexedQueryAndOutputToTheAuthenticatedOwner() {
        when(access.postScope(eq(HaloPostTools.LIST_POSTS_GLOBAL), eq(HaloPostTools.LIST_POSTS_OWN), any(), any()))
                .thenReturn(Mono.just("author"));
        var mine = post("mine", false, Post.VisibleEnum.PRIVATE);
        mine.getSpec().setOwner("author");
        var others = post("others", false, Post.VisibleEnum.PRIVATE);
        others.getSpec().setOwner("someone-else");
        when(client.listBy(eq(Post.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(mine, others))));
        var input = Map.<String, Object>of("includeUnpublished", true, "owner", "someone-else");
        StepVerifier.create(posts.listPosts(input))
                .assertNext(value -> {
                    var items = (List<?>) ((Map<?, ?>) value).get("items");
                    assertThat(items).hasSize(1);
                    assertThat(((Map<?, ?>) items.getFirst()).get("name")).isEqualTo("mine");
                })
                .verifyComplete();
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(Post.class), options.capture(), any());
        assertThat(options.getValue().getFieldSelector().query().toString())
                .contains("spec.owner", "author")
                .doesNotContain("someone-else");
        verify(access).postScope(HaloPostTools.LIST_POSTS_GLOBAL, HaloPostTools.LIST_POSTS_OWN, input, null);
    }

    @Test
    void scopeFailureCannotFallBackToListingAllPosts() {
        when(access.postScope(eq(HaloPostTools.LIST_POSTS_GLOBAL), eq(HaloPostTools.LIST_POSTS_OWN), any(), any()))
                .thenReturn(Mono.error(new SecurityException("Denied owner scope")));
        StepVerifier.create(posts.listPosts(Map.of()))
                .expectErrorMessage("Denied owner scope")
                .verify();
        verifyNoInteractions(client, contentService, settings);
    }

    @Test
    void pageListsDefaultToPublicAndCanExplicitlyIncludeUnpublishedManagementContent() {
        var published = page("published", true, Post.VisibleEnum.PUBLIC);
        var draft = page("draft", false, Post.VisibleEnum.PRIVATE);
        var recycled = page("recycled", false, Post.VisibleEnum.PUBLIC);
        recycled.getMetadata().setLabels(Map.of(Post.DELETED_LABEL, "true"));
        when(client.listBy(eq(SinglePage.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 3, List.of(published, draft, recycled))));
        StepVerifier.create(pages.listPages(Map.of()))
                .assertNext(result ->
                        assertThat((List<?>) ((Map<?, ?>) result).get("items")).hasSize(1))
                .verifyComplete();
        StepVerifier.create(pages.listPages(Map.of("includeUnpublished", true)))
                .assertNext(result ->
                        assertThat((List<?>) ((Map<?, ?>) result).get("items")).hasSize(2))
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "query", "category", "tag", "includeUnpublished"})
    void rejectsMalformedListInputsBeforeQuerying(String field) {
        StepVerifier.create(posts.listPosts(Map.of(
                        field,
                        switch (field) {
                            case "page" -> 10_001;
                            case "size" -> 101;
                            case "query" -> "a".repeat(201);
                            case "category", "tag" -> 7;
                            default -> "true";
                        })))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client, contentService, settings);
    }

    @Test
    void getsTheSelectedPublishedPostSnapshotThroughThePublicContentService() {
        var post = post("article", true, Post.VisibleEnum.PUBLIC);
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        when(contentService.getSpecifiedContent("article", "article-release"))
                .thenReturn(Mono.just(content("article-release", "# Published", "<h1>Published</h1>")));
        StepVerifier.create(posts.getPost(Map.of("name", "article")))
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("name")).isEqualTo("article");
                    assertThat(values.get("metadataVersion")).isEqualTo(7L);
                    assertThat(values.get("snapshotName")).isEqualTo("article-release");
                    assertThat(values.get("version")).isEqualTo("release");
                    assertThat(values.get("raw")).isEqualTo("# Published");
                    assertThat(values.get("content")).isEqualTo("<h1>Published</h1>");
                    assertThat(values.get("url")).isEqualTo("https://site.example/article");
                    assertThat(values.get("truncated")).isEqualTo(false);
                })
                .verifyComplete();
        verify(contentService).getSpecifiedContent("article", "article-release");
        verify(contentService, never()).getHeadContent(anyString());
        verify(settings).maxContentCharacters("get_post");
    }

    @Test
    void rechecksTheOwnerOfTheActualFetchedPostBeforeReadingItsContent() {
        when(access.postScope(any(), any(), any(), any())).thenReturn(Mono.just("author"));
        var changedOwner = post("post", false, Post.VisibleEnum.PRIVATE);
        changedOwner.getSpec().setOwner("new-owner");
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(changedOwner));
        StepVerifier.create(posts.getPost(Map.of("name", "post", "version", "head")))
                .expectErrorMessage("Requested content is unavailable")
                .verify();
        verifyNoInteractions(contentService, settings);
    }

    @Test
    void explicitlyReadsTheLatestPrivateDraftWhenHeadIsSelected() {
        var draft = post("draft", false, Post.VisibleEnum.PRIVATE);
        when(client.fetch(Post.class, "draft")).thenReturn(Mono.just(draft));
        when(contentService.getSpecifiedContent("draft", "draft-head"))
                .thenReturn(Mono.just(content("draft-head", "Draft source", "<p>Draft source</p>")));
        StepVerifier.create(posts.getPost(Map.of("name", "draft", "version", "head")))
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("version")).isEqualTo("head");
                    assertThat(values.get("snapshotName")).isEqualTo("draft-head");
                    assertThat(values.get("raw")).isEqualTo("Draft source");
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unpublished", "private", "recycled", "deletedLabel", "deleting"})
    void defaultDetailReadsRejectNonPublicOrDeletedContent(String state) {
        var post = post("post", true, Post.VisibleEnum.PUBLIC);
        var page = page("page", true, Post.VisibleEnum.PUBLIC);
        switch (state) {
            case "unpublished" -> {
                post.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
                page.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
            }
            case "private" -> {
                post.getSpec().setVisible(Post.VisibleEnum.PRIVATE);
                page.getSpec().setVisible(Post.VisibleEnum.PRIVATE);
            }
            case "recycled" -> {
                post.getSpec().setDeleted(true);
                page.getSpec().setDeleted(true);
            }
            case "deletedLabel" -> {
                post.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "true", Post.DELETED_LABEL, "true"));
                page.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "true", Post.DELETED_LABEL, "true"));
            }
            default -> {
                post.getMetadata().setDeletionTimestamp(Instant.now());
                page.getMetadata().setDeletionTimestamp(Instant.now());
            }
        }
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        when(client.fetch(SinglePage.class, "page")).thenReturn(Mono.just(page));
        StepVerifier.create(posts.getPost(Map.of("name", "post")))
                .expectErrorMessage("Requested content is unavailable")
                .verify();
        StepVerifier.create(pages.getPage(Map.of("name", "page")))
                .expectErrorMessage("Requested content is unavailable")
                .verify();
        if (!"unpublished".equals(state) && !"private".equals(state)) {
            StepVerifier.create(posts.getPost(Map.of("name", "post", "version", "head")))
                    .expectErrorMessage("Requested content is unavailable")
                    .verify();
            StepVerifier.create(pages.getPage(Map.of("name", "page", "version", "head")))
                    .expectErrorMessage("Requested content is unavailable")
                    .verify();
        }
        verifyNoInteractions(contentService, settings);
    }

    @Test
    void reconstructsPageDeltaSnapshotsInsteadOfReturningRawPatches() {
        var page = page("about", true, Post.VisibleEnum.PUBLIC);
        var base = snapshot(page, "about-base", "Old source", "<p>Old</p>", true);
        var released = snapshot(
                page,
                "about-release",
                PatchUtils.diffToJsonPatch("Old source", "New source"),
                PatchUtils.diffToJsonPatch("<p>Old</p>", "<p>New</p>"),
                false);
        when(client.fetch(SinglePage.class, "about")).thenReturn(Mono.just(page));
        when(client.fetch(Snapshot.class, "about-base")).thenReturn(Mono.just(base));
        when(client.fetch(Snapshot.class, "about-release")).thenReturn(Mono.just(released));
        StepVerifier.create(pages.getPage(Map.of("name", "about")))
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("raw")).isEqualTo("New source");
                    assertThat(values.get("content")).isEqualTo("<p>New</p>");
                    assertThat(values.get("snapshotName")).isEqualTo("about-release");
                    assertThat(values.get("metadataVersion")).isEqualTo(7L);
                })
                .verifyComplete();
        verify(settings).maxContentCharacters("get_page");
        verify(client, never()).fetch(Snapshot.class, "about-head");
        verifyNoInteractions(contentService);
    }

    @Test
    void pageHeadCanBeItsBaseSnapshotButMustBelongToThatPage() {
        var page = page("draft", false, Post.VisibleEnum.PRIVATE);
        page.getSpec().setHeadSnapshot("draft-base");
        var base = snapshot(page, "draft-base", "Draft", "<p>Draft</p>", true);
        when(client.fetch(SinglePage.class, "draft")).thenReturn(Mono.just(page));
        when(client.fetch(Snapshot.class, "draft-base")).thenReturn(Mono.just(base));
        StepVerifier.create(pages.getPage(Map.of("name", "draft", "version", "head")))
                .assertNext(
                        result -> assertThat(((Map<?, ?>) result).get("raw")).isEqualTo("Draft"))
                .verifyComplete();
        base.getSpec().setSubjectRef(Ref.of(page("other", false, Post.VisibleEnum.PRIVATE)));
        StepVerifier.create(pages.getPage(Map.of("name", "draft", "version", "head")))
                .expectErrorMessage("Requested content is unavailable")
                .verify();
    }

    @Test
    void eachDetailExecutionReadsItsOwnConfiguredLimitAndProtectsUnicodeBoundaries() {
        var post = post("article", true, Post.VisibleEnum.PUBLIC);
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        when(contentService.getSpecifiedContent("article", "article-release"))
                .thenReturn(Mono.just(content("article-release", "ab😀tail", "12😀tail")));
        var reads = new AtomicInteger();
        when(settings.maxContentCharacters("get_post"))
                .thenAnswer(ignored -> Mono.just(reads.incrementAndGet() == 1 ? 3 : 8));
        var read = posts.getPost(Map.of("name", "article"));
        StepVerifier.create(read)
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("raw")).isEqualTo("ab");
                    assertThat(values.get("content")).isEqualTo("12");
                    assertThat(values.get("rawTruncated")).isEqualTo(true);
                    assertThat(values.get("contentTruncated")).isEqualTo(true);
                    assertThat(values.get("truncated")).isEqualTo(true);
                })
                .verifyComplete();
        StepVerifier.create(read)
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("raw")).isEqualTo("ab😀tail");
                    assertThat(values.get("content")).isEqualTo("12😀tail");
                    assertThat(values.get("truncated")).isEqualTo(false);
                })
                .verifyComplete();
        assertThat(reads).hasValue(2);
    }

    @Test
    void aConfigurationFailurePreventsContentSnapshotReads() {
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post("article", true, Post.VisibleEnum.PUBLIC)));
        when(settings.maxContentCharacters("get_post"))
                .thenReturn(Mono.error(new IllegalStateException("Broken settings")));
        StepVerifier.create(posts.getPost(Map.of("name", "article")))
                .expectErrorMessage("Broken settings")
                .verify();
        verifyNoInteractions(contentService);
    }

    @Test
    void missingContentAndUnexpectedSnapshotVersionsAreRejected() {
        when(client.fetch(Post.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(posts.getPost(Map.of("name", "missing")))
                .expectErrorMessage("Requested content is unavailable")
                .verify();
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post("article", true, Post.VisibleEnum.PUBLIC)));
        when(contentService.getSpecifiedContent("article", "article-release"))
                .thenReturn(Mono.just(content("unexpected", "text", "text")));
        StepVerifier.create(posts.getPost(Map.of("name", "article")))
                .expectErrorMessage("Content snapshot changed; read the content again")
                .verify();
    }

    private static Post post(String name, boolean published, Post.VisibleEnum visible) {
        var post = new Post();
        post.setMetadata(metadata(name, published));
        var spec = new Post.PostSpec();
        spec.setTitle("Title " + name);
        spec.setSlug(name);
        spec.setDeleted(false);
        spec.setPublish(published);
        spec.setVisible(visible);
        spec.setBaseSnapshot(name + "-base");
        spec.setReleaseSnapshot(name + "-release");
        spec.setHeadSnapshot(name + "-head");
        spec.setCategories(List.of("news"));
        spec.setTags(List.of("cms"));
        post.setSpec(spec);
        var status = new Post.PostStatus();
        status.setPermalink("https://site.example/" + name);
        status.setExcerpt("Excerpt " + name);
        post.setStatus(status);
        return post;
    }

    private static SinglePage page(String name, boolean published, Post.VisibleEnum visible) {
        var page = new SinglePage();
        page.setMetadata(metadata(name, published));
        var spec = new SinglePage.SinglePageSpec();
        spec.setTitle("Title " + name);
        spec.setSlug(name);
        spec.setDeleted(false);
        spec.setPublish(published);
        spec.setVisible(visible);
        spec.setBaseSnapshot(name + "-base");
        spec.setReleaseSnapshot(name + "-release");
        spec.setHeadSnapshot(name + "-head");
        page.setSpec(spec);
        var status = new SinglePage.SinglePageStatus();
        status.setPermalink("https://site.example/" + name);
        page.setStatus(status);
        return page;
    }

    private static Metadata metadata(String name, boolean published) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(7L);
        metadata.setLabels(Map.of(Post.PUBLISHED_LABEL, Boolean.toString(published), Post.DELETED_LABEL, "false"));
        return metadata;
    }

    private static ContentWrapper content(String name, String raw, String rendered) {
        return ContentWrapper.builder()
                .snapshotName(name)
                .raw(raw)
                .content(rendered)
                .rawType("markdown")
                .build();
    }

    private static Snapshot snapshot(SinglePage page, String name, String raw, String rendered, boolean base) {
        var snapshot = new Snapshot();
        var metadata = metadata(name, false);
        metadata.setAnnotations(Map.of(Snapshot.KEEP_RAW_ANNO, Boolean.toString(base)));
        snapshot.setMetadata(metadata);
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(page));
        spec.setRawPatch(raw);
        spec.setContentPatch(rendered);
        spec.setRawType("markdown");
        snapshot.setSpec(spec);
        return snapshot;
    }
}
