package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.security.AfterSecurityWebFilter;

/**
 * Verifies native request coverage and the preflight that protects publication from stale editor saves.
 *
 * <p>Handlers are explicit mutation probes, so a conflict must occur before any native content write can run.
 */
class HaloContentMutationWebFilterTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
    private final MockEnvironment environment = new MockEnvironment();
    private final HaloContentMutationWebFilter filter =
            new HaloContentMutationWebFilter(client, coordinator, environment);

    @Test
    void usesHalosExportedAfterSecurityExtensionPoint() {
        assertThat(filter).isInstanceOf(AfterSecurityWebFilter.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/apis/api.console.halo.run/v1alpha1/posts/article/content",
                "/apis/api.console.halo.run/v1alpha1/posts/article/publish",
                "/apis/api.console.halo.run/v1alpha1/posts/article/revert-content",
                "/apis/api.console.halo.run/v1alpha1/singlepages/article/content",
                "/apis/api.console.halo.run/v1alpha1/singlepages/article/publish",
                "/apis/uc.api.content.halo.run/v1alpha1/posts/article/draft",
                "/apis/uc.api.content.halo.run/v1alpha1/posts/article/publish",
                "/apis/content.halo.run/v1alpha1/posts/article",
                "/apis/content.halo.run/v1alpha1/singlepages/article"
            })
    void holdsNativeMutationHandlersUntilTheCorrespondingContentLeaseIsReleased(String path) throws Exception {
        var type = path.contains("singlepages") ? SinglePage.class : Post.class;
        var hold = coordinator.withLock(type, "article", Mono::<Void>never).subscribe();
        var entered = new AtomicInteger();
        var complete = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();
        var exchange = exchange(path);
        if (path.startsWith("/apis/content.halo.run/")) {
            var resource = type == Post.class ? "posts" : "singlepages";
            currentContent(resource, 7);
            exchange = resourceExchange(resource, resourceBody("article", 7, "base", "head", "release"));
        }
        var request = filter.filter(exchange, ignored -> Mono.fromRunnable(entered::incrementAndGet))
                .doFinally(signal -> complete.countDown())
                .subscribe(ignored -> {}, error::set);
        assertThat(entered).hasValue(0);
        hold.dispose();
        assertThat(complete.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(error).hasNullValue();
        assertThat(entered).hasValue(1);
        assertThat(coordinator.activeKeyCount()).isZero();
        request.dispose();
    }

    @Test
    void doesNotAcquireALeaseForReadsCollectionsOrOtherPlugins() {
        var hold =
                coordinator.withLock(Post.class, "article", Mono::<Void>never).subscribe();
        var entered = new AtomicInteger();
        try {
            for (var request : java.util.List.of(
                    MockServerHttpRequest.get("/apis/api.console.halo.run/v1alpha1/posts/article/content"),
                    MockServerHttpRequest.post("/apis/api.console.halo.run/v1alpha1/posts"),
                    MockServerHttpRequest.put("/apis/console.api.aifoundation.halo.run/v1alpha1/tools/article"))) {
                StepVerifier.create(filter.filter(
                                MockServerWebExchange.from(request),
                                ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                        .verifyComplete();
            }
            assertThat(entered).hasValue(3);
        } finally {
            hold.dispose();
        }
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/vnd.halo+json"})
    void validatesAFreshEditorRequestAndReplaysItsExactBytesToTheNativeHandler(String mediaType) {
        var post = post("article", 7);
        post.getSpec().setPublishTime(Instant.parse("2026-10-15T00:00:00Z"));
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        var body = editorBody("article", 7, "base", "head", "release");
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/apis/api.console.halo.run/v1alpha1/posts/article")
                        .contentType(MediaType.parseMediaType(mediaType))
                        .body(body));
        StepVerifier.create(filter.filter(
                        exchange,
                        current -> DataBufferUtils.join(current.getRequest().getBody())
                                .doOnNext(buffer -> {
                                    try {
                                        assertThat(buffer.toString(StandardCharsets.UTF_8))
                                                .isEqualTo(body);
                                    } finally {
                                        DataBufferUtils.release(buffer);
                                    }
                                })
                                .then()))
                .verifyComplete();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void rejectsStaleFullEditorSavesBeforeTheirContentMutationAndReleasesTheLease() {
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post("article", 8)));
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        editorExchange(editorBody("article", 7, "base", "head", "release")),
                        ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(entered).hasValue(0);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void rejectsBodyNameMismatchBeforeTheHandlerCanWriteAnotherResource() {
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        editorExchange(editorBody("another", 7, "base", "head", "release")),
                        ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .expectErrorSatisfies(error -> status(error, HttpStatus.BAD_REQUEST))
                .verify();
        assertThat(entered).hasValue(0);
        verifyNoInteractions(client);
    }

    @Test
    void rejectsChangedSnapshotReferencesEvenWhenTheResourceVersionMatches() {
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post("article", 7)));
        StepVerifier.create(filter.filter(
                        editorExchange(editorBody("article", 7, "stale-base", "head", "release")),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @ParameterizedTest
    @CsvSource({
        "posts,baseSnapshot", "posts,headSnapshot", "posts,releaseSnapshot",
        "singlepages,baseSnapshot", "singlepages,headSnapshot", "singlepages,releaseSnapshot"
    })
    void rejectsFreshVersionWithStaleResourceSnapshotReferences(String resource, String reference) {
        currentContent(resource, 7);
        var body = resourceBody(
                "article",
                7,
                reference.equals("baseSnapshot") ? "stale-base" : "base",
                reference.equals("headSnapshot") ? "stale-head" : "head",
                reference.equals("releaseSnapshot") ? "stale-release" : "release");
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        resourceExchange(resource, body), ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(entered).hasValue(0);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"posts,false", "singlepages,false", "posts,true", "singlepages,true"})
    void rejectsAnOldPublicationRevisionEvenWhenTheVersionAndSnapshotsAreCurrent(String resource, boolean editor) {
        currentContent(resource, 7)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var body = publicationRevisionBody(resource, "old-state", editor);
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        publicationRevisionExchange(resource, body, editor),
                        ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(entered).hasValue(0);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"posts,false", "singlepages,false", "posts,true", "singlepages,true"})
    void cannotOmitTheRevisionOfAResourceChangedByAPublicationTool(String resource, boolean editor) {
        currentContent(resource, 7)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var body = publicationRevisionBody(resource, null, editor);
        StepVerifier.create(filter.filter(
                        publicationRevisionExchange(resource, body, editor),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @ParameterizedTest
    @CsvSource({"posts,false", "singlepages,false", "posts,true", "singlepages,true"})
    void aFreshPublicationRevisionStillPermitsNormalNativeSavesAfterAFilterRestart(String resource, boolean editor) {
        currentContent(resource, 7)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var restarted = new HaloContentMutationWebFilter(client, new HaloContentMutationCoordinator(), environment);
        var body = publicationRevisionBody(resource, "new-state", editor);
        var entered = new AtomicInteger();
        StepVerifier.create(restarted.filter(
                        publicationRevisionExchange(resource, body, editor),
                        ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        assertThat(entered).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"old-state", ""})
    void anOldUserCenterSaveCannotEraseTheCurrentPublicationRevision(String revision) {
        currentContent("posts", 7)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var body = publicationRevisionBody("posts", revision.isEmpty() ? null : revision, false);
        StepVerifier.create(filter.filter(
                        userCenterExchange(body),
                        ignored -> Mono.error(new AssertionError("User Center must not overwrite the revision"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void freshUserCenterSavesStillDelegateProtectedSpecFieldsToTheNativeHandler() {
        currentContent("posts", 7)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var body = publicationRevisionBody("posts", "new-state", false).replace("\"head\"", "\"old-head\"");
        var entered = new AtomicInteger();
        StepVerifier.create(
                        filter.filter(userCenterExchange(body), ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        assertThat(entered).hasValue(1);
    }

    @Test
    void userCenterSavesRequireTheCurrentResourceVersionAsWellAsItsPublicationRevision() {
        currentContent("posts", 8)
                .getMetadata()
                .setAnnotations(Map.of(HaloPublicationRevision.ANNOTATION, "new-state"));
        var body = publicationRevisionBody("posts", "new-state", false);
        StepVerifier.create(filter.filter(
                        userCenterExchange(body),
                        ignored -> Mono.error(new AssertionError("Stale version must not reach the handler"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @ParameterizedTest
    @CsvSource({
        "posts,application/json", "posts,application/vnd.halo+json",
        "singlepages,application/json", "singlepages,application/vnd.halo+json"
    })
    void validatesFreshResourceSavesAndReplaysTheirExactBytes(String resource, String mediaType) {
        currentContent(resource, 7);
        var body = resourceBody("article", 7, "base", "head", "release");
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/apis/content.halo.run/v1alpha1/" + resource + "/article")
                        .contentType(MediaType.parseMediaType(mediaType))
                        .body(body));
        StepVerifier.create(filter.filter(
                        exchange,
                        current -> DataBufferUtils.join(current.getRequest().getBody())
                                .doOnNext(buffer -> {
                                    try {
                                        assertThat(buffer.toString(StandardCharsets.UTF_8))
                                                .isEqualTo(body);
                                    } finally {
                                        DataBufferUtils.release(buffer);
                                    }
                                })
                                .then()))
                .verifyComplete();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"posts", "singlepages"})
    void rejectsResourceSavesWithAStaleVersion(String resource) {
        currentContent(resource, 8);
        StepVerifier.create(filter.filter(
                        resourceExchange(resource, resourceBody("article", 7, "base", "head", "release")),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"posts", "singlepages"})
    void rejectsResourceBodyNameMismatch(String resource) {
        StepVerifier.create(filter.filter(
                        resourceExchange(resource, resourceBody("another", 7, "base", "head", "release")),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.BAD_REQUEST))
                .verify();
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @CsvSource({"posts,head,true", "singlepages,head,true"})
    void permitsNativeResourcePublicationOfTheCurrentHead(String resource, String release, String publish) {
        currentContent(resource, 7);
        var body = resourceBody("article", 7, "base", "head", release)
                .replace("\"title\":", "\"publish\":" + publish + ",\"title\":");
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        resourceExchange(resource, body), ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        assertThat(entered).hasValue(1);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
        "posts,head,false", "posts,head,\"true\"", "posts,another,true",
        "singlepages,head,false", "singlepages,head,\"true\"", "singlepages,another,true"
    })
    void rejectsResourceReleaseChangesOtherThanPublishingTheCurrentHead(
            String resource, String release, String publish) {
        currentContent(resource, 7);
        var body = resourceBody("article", 7, "base", "head", release)
                .replace("\"title\":", "\"publish\":" + publish + ",\"title\":");
        StepVerifier.create(filter.filter(
                        resourceExchange(resource, body),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void doesNotPermitTheResourcePublicationTransitionInAFullEditorBody() {
        currentContent("posts", 7);
        var body = editorBody("article", 7, "base", "head", "head")
                .replace("\"baseSnapshot\":", "\"publish\":true,\"baseSnapshot\":");
        StepVerifier.create(filter.filter(
                        editorExchange(body), ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @Test
    void acceptsOmittedOrNullDraftReferencesWhenTheCurrentReferencesAreNull() {
        var post = post("article", 7);
        post.getSpec().setHeadSnapshot(null);
        post.getSpec().setReleaseSnapshot(null);
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        var body = "{\"metadata\":{\"name\":\"article\",\"version\":7},"
                + "\"spec\":{\"baseSnapshot\":\"base\",\"releaseSnapshot\":null}}";
        StepVerifier.create(filter.filter(resourceExchange("posts", body), ignored -> Mono.empty()))
                .verifyComplete();
    }

    @Test
    void leavesJsonPatchAndSubresourceBodiesToTheNativeHandlers() {
        var entered = new AtomicInteger();
        var patch =
                MockServerWebExchange.from(MockServerHttpRequest.patch("/apis/content.halo.run/v1alpha1/posts/article")
                        .contentType(MediaType.valueOf("application/json-patch+json"))
                        .body("[{\"op\":\"replace\",\"path\":\"/spec/title\",\"value\":\"Title\"}]"));
        var status = exchange("/apis/content.halo.run/v1alpha1/posts/article/status");
        StepVerifier.create(filter.filter(patch, ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        StepVerifier.create(filter.filter(status, ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        assertThat(entered).hasValue(2);
        verifyNoInteractions(client);
    }

    @Test
    void cachesEditorRequestsWithinHalosConfiguredCodecLimit() {
        environment.setProperty("spring.http.codecs.max-in-memory-size", "16B");
        StepVerifier.create(filter.filter(
                        editorExchange(editorBody("article", 7, "base", "head", "release")),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.PAYLOAD_TOO_LARGE))
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void failsClosedWhenASnapshotHasNoRecognizedContentOwner() {
        var snapshot = new Snapshot();
        snapshot.setMetadata(metadata("snapshot", 1));
        snapshot.setSpec(new Snapshot.SnapShotSpec());
        when(client.fetch(Snapshot.class, "snapshot")).thenReturn(Mono.just(snapshot));
        StepVerifier.create(filter.filter(
                        exchange("/apis/content.halo.run/v1alpha1/snapshots/snapshot"),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    @Test
    void rechecksASnapshotsOwnerAfterWaitingForItsLease() {
        var original = snapshot("snapshot", "article");
        var changed = snapshot("snapshot", "another");
        when(client.fetch(Snapshot.class, "snapshot")).thenReturn(Mono.just(original), Mono.just(changed));
        StepVerifier.create(filter.filter(
                        exchange("/apis/content.halo.run/v1alpha1/snapshots/snapshot"),
                        ignored -> Mono.error(new AssertionError("Native handler must not run"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"base", "release", "last-release"})
    void rejectsRawWritesToSnapshotsThatContributeToPublishedContent(String name) {
        var post = post("article", 7);
        post.getSpec().setPublish(true);
        post.getMetadata().setAnnotations(Map.of(Post.LAST_RELEASED_SNAPSHOT_ANNO, "last-release"));
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        when(client.fetch(Snapshot.class, name)).thenReturn(Mono.just(snapshot(name, "article")));
        StepVerifier.create(filter.filter(
                        exchange("/apis/content.halo.run/v1alpha1/snapshots/" + name),
                        ignored -> Mono.error(new AssertionError("Published snapshot must not be mutated"))))
                .expectErrorSatisfies(error -> status(error, HttpStatus.CONFLICT))
                .verify();
    }

    @Test
    void permitsRawWritesToASeparateDraftWhileThePublishedSnapshotsStayProtected() {
        var post = post("article", 7);
        post.getSpec().setPublish(true);
        when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(snapshot("head", "article")));
        var entered = new AtomicInteger();
        StepVerifier.create(filter.filter(
                        exchange("/apis/content.halo.run/v1alpha1/snapshots/head"),
                        ignored -> Mono.fromRunnable(entered::incrementAndGet)))
                .verifyComplete();
        assertThat(entered).hasValue(1);
    }

    @Test
    void cancellingANativeHandlerReleasesItsLeaseForTheNextRequest() {
        var exchange = exchange("/apis/api.console.halo.run/v1alpha1/posts/article/content");
        var pending = filter.filter(exchange, ignored -> Mono.never()).subscribe();
        assertThat(coordinator.activeKeyCount()).isEqualTo(1);
        pending.dispose();
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.put(path));
    }

    private static MockServerWebExchange editorExchange(String body) {
        return MockServerWebExchange.from(MockServerHttpRequest.put("/apis/api.console.halo.run/v1alpha1/posts/article")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
    }

    private static MockServerWebExchange resourceExchange(String resource, String body) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.put("/apis/content.halo.run/v1alpha1/" + resource + "/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    private Extension currentContent(String resource, long version) {
        if (resource.equals("posts")) {
            var post = post("article", version);
            when(client.fetch(Post.class, "article")).thenReturn(Mono.just(post));
            return post;
        }
        var page = new SinglePage();
        page.setMetadata(metadata("article", version));
        var spec = new SinglePage.SinglePageSpec();
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        page.setSpec(spec);
        when(client.fetch(SinglePage.class, "article")).thenReturn(Mono.just(page));
        return page;
    }

    private static MockServerWebExchange publicationRevisionExchange(String resource, String body, boolean editor) {
        var group = editor ? "api.console.halo.run" : "content.halo.run";
        return MockServerWebExchange.from(
                MockServerHttpRequest.put("/apis/" + group + "/v1alpha1/" + resource + "/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    private static MockServerWebExchange userCenterExchange(String body) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.put("/apis/uc.api.content.halo.run/v1alpha1/posts/article")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body));
    }

    private static String publicationRevisionBody(String resource, String revision, boolean editor) {
        var body = resourceBody("article", 7, "base", "head", "release");
        if (revision != null) {
            body = body.replace(
                    "\"metadata\":{",
                    "\"metadata\":{\"annotations\":{\"" + HaloPublicationRevision.ANNOTATION + "\":\"" + revision
                            + "\",\"user-note\":\"Keep me\"},");
        }
        return editor
                ? "{\"" + (resource.equals("posts") ? "post" : "page") + "\":" + body
                        + ",\"content\":{\"raw\":\"Body\",\"content\":\"Body\",\"rawType\":\"HTML\"}}"
                : body;
    }

    private static String resourceBody(String name, long version, String base, String head, String release) {
        return "{\"metadata\":{\"name\":\"" + name + "\",\"version\":" + version
                + "},\"spec\":{\"title\":\"Updated title\",\"baseSnapshot\":\"" + base
                + "\",\"headSnapshot\":\"" + head + "\",\"releaseSnapshot\":\"" + release + "\"}}";
    }

    private static String editorBody(String name, long version, String base, String head, String release) {
        return "{\"post\":{\"metadata\":{\"name\":\"" + name + "\",\"version\":" + version
                + "},\"spec\":{\"baseSnapshot\":\"" + base + "\",\"headSnapshot\":\"" + head
                + "\",\"releaseSnapshot\":\"" + release
                + "\"}},\"content\":{\"raw\":\"保存\",\"content\":\"保存\",\"rawType\":\"HTML\"}}";
    }

    private static Post post(String name, long version) {
        var post = new Post();
        post.setMetadata(metadata(name, version));
        var spec = new Post.PostSpec();
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        post.setSpec(spec);
        return post;
    }

    private static Snapshot snapshot(String name, String owner) {
        var snapshot = new Snapshot();
        snapshot.setMetadata(metadata(name, 1));
        snapshot.setSpec(new Snapshot.SnapShotSpec());
        var ref = new Ref();
        ref.setGroup("content.halo.run");
        ref.setKind("Post");
        ref.setVersion("v1alpha1");
        ref.setName(owner);
        snapshot.getSpec().setSubjectRef(ref);
        return snapshot;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        return metadata;
    }

    private static void status(Throwable error, HttpStatus expected) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(expected);
    }
}
