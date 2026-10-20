package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.app.content.ContentWrapper;
import run.halo.app.content.PatchUtils;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Verifies publication workflows through independent article and page providers.
 *
 * <p>Reviewed fingerprints, cancellation and reconciler confirmation remain required for both resource types.
 */
class HaloContentPublicationTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final ToolSettingsStore storedSettings = mock(ToolSettingsStore.class);
    private final HaloContentToolSettings settings = new HaloContentToolSettings(storedSettings);
    private final HaloDraftContentService drafts = mock(HaloDraftContentService.class);
    private final HaloContentMutationCoordinator coordinator = new HaloContentMutationCoordinator();
    private final HaloPostTools posts = new HaloPostTools(
            client, mock(run.halo.app.content.PostContentService.class), access, settings, drafts, coordinator);
    private final HaloPageTools pages = new HaloPageTools(client, access, settings, drafts, coordinator);
    private final AtomicReference<Extension> state = new AtomicReference<>();
    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private boolean reconcile = true;

    @BeforeEach
    void setUp() {
        when(access.requirePost(any(), any(), any(), any(Post.class))).thenReturn(Mono.empty());
        when(storedSettings.configuration(any())).thenReturn(Mono.just(Map.of()));
    }

    @Test
    void actualPostPermissionIsCheckedBeforeReadingAnySnapshotsOrSettings() {
        fixture(true);
        when(access.requirePost(
                        eq(HaloPostTools.COMPARE_POST_GLOBAL),
                        eq(HaloPostTools.COMPARE_POST_OWN),
                        any(),
                        any(Post.class)))
                .thenReturn(Mono.error(new AccessDeniedException("post owner changed")));
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, never()).fetch(eq(Snapshot.class), any());
        verifyNoInteractions(storedSettings);
    }

    @Test
    void aChangedPostOwnerOnTheFinalReadPreventsReturningTheComparedBodies() {
        fixture(true);
        when(access.requirePost(
                        eq(HaloPostTools.COMPARE_POST_GLOBAL),
                        eq(HaloPostTools.COMPARE_POST_OWN),
                        any(),
                        any(Post.class)))
                .thenReturn(Mono.empty(), Mono.error(new AccessDeniedException("post owner changed")));
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(access, times(2))
                .requirePost(
                        eq(HaloPostTools.COMPARE_POST_GLOBAL),
                        eq(HaloPostTools.COMPARE_POST_OWN),
                        any(),
                        any(Post.class));
        verify(client, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void comparesActualReconstructedHeadAndReleasedVersionsAndReturnsAPublishToken(boolean post) {
        fixture(post);
        var output = compare(post);
        assertThat(output)
                .containsEntry("metadataVersion", 7L)
                .containsEntry("headSnapshot", "head")
                .containsEntry("releaseSnapshot", "base")
                .containsEntry("hasReleasedVersion", true)
                .containsEntry("changed", true)
                .containsEntry("truncated", false);
        assertThat(map(output.get("head")))
                .containsEntry("raw", "Revised body\n")
                .containsEntry("content", "<p>Revised body</p>\n")
                .containsEntry("snapshotName", "head");
        assertThat(map(output.get("release")))
                .containsEntry("raw", "Original body\n")
                .containsEntry("content", "<p>Original body</p>\n")
                .containsEntry("snapshotName", "base");
        assertThat(map(output.get("rawDiff")))
                .containsEntry("removed", "Original")
                .containsEntry("added", "Revised")
                .containsEntry("changed", true);
        assertThat((String) output.get("headContentFingerprint")).matches("[a-f0-9]{64}");
        assertThat(map(output.get("head")).get("contentFingerprint")).isEqualTo(output.get("headContentFingerprint"));
        verify(client, never()).update(any());
    }

    @Test
    void comparesTheObservedReleaseInsteadOfAnUnconfirmedDesiredRelease() {
        fixture(true);
        ((Post) state.get()).getSpec().setReleaseSnapshot("head");
        var output = compare(true);
        assertThat(output).containsEntry("releaseSnapshot", "base").containsEntry("requestedReleaseSnapshot", "head");
        assertThat(map(output.get("release")))
                .containsEntry("snapshotName", "base")
                .containsEntry("raw", "Original body\n");
    }

    @Test
    void supportsANeverPublishedDraftWithoutInventingAReleasedVersion() {
        fixture(false);
        var page = (SinglePage) state.get();
        page.getMetadata().setAnnotations(Map.of());
        page.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
        page.getSpec().setPublish(false);
        page.getSpec().setReleaseSnapshot(null);
        var output = compare(false);
        assertThat(output)
                .containsEntry("published", false)
                .containsEntry("hasReleasedVersion", false)
                .containsEntry("release", null)
                .containsEntry("releaseSnapshot", "");
        assertThat(map(output.get("rawDiff"))).containsEntry("removed", "").containsEntry("added", "Revised body\n");
    }

    @Test
    void recognizesIdenticalVersionsAndSourceFormatChanges() {
        fixture(true);
        var post = (Post) state.get();
        post.getSpec().setHeadSnapshot("base");
        assertThat(compare(true)).containsEntry("changed", false).containsEntry("rawTypeChanged", false);
        post.getSpec().setHeadSnapshot("head");
        snapshots.get("head").getSpec().setRawPatch(PatchUtils.diffToJsonPatch("Original body\n", "Original body\n"));
        snapshots
                .get("head")
                .getSpec()
                .setContentPatch(PatchUtils.diffToJsonPatch("<p>Original body</p>\n", "<p>Original body</p>\n"));
        snapshots.get("head").getSpec().setRawType("MARKDOWN");
        var output = compare(true);
        assertThat(output).containsEntry("changed", true).containsEntry("rawTypeChanged", true);
        assertThat(map(output.get("rawDiff"))).containsEntry("changed", false);
    }

    @Test
    void limitsBothBodiesAndDiffSpansWithoutSplittingUnicodeAndFingerprintsTheFullText() {
        fixture(true);
        setHead("😀New body\n", "😀<p>New body</p>\n");
        var full = compare(true);
        when(storedSettings.configuration("ai-foundation/get_post"))
                .thenReturn(Mono.just(Map.of("maxContentCharacters", 1)));
        var limited = compare(true);
        assertThat(limited)
                .containsEntry("truncated", true)
                .containsEntry("headContentFingerprint", full.get("headContentFingerprint"));
        assertThat(map(limited.get("head")))
                .containsEntry("raw", "")
                .containsEntry("content", "")
                .containsEntry("rawTruncated", true)
                .containsEntry("contentTruncated", true);
        assertThat(map(limited.get("rawDiff"))).containsEntry("added", "").containsEntry("truncated", true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publishesOnlyTheReviewedHeadAndConfirmsTheActualReleasedSnapshot(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        var output = map(publish(post, input).block());
        assertThat(output)
                .containsEntry("operation", "publish")
                .containsEntry("published", true)
                .containsEntry("desiredPublished", true)
                .containsEntry("requestedReleaseSnapshot", output.get("releaseSnapshot"))
                .containsEntry("headSnapshot", output.get("releaseSnapshot"))
                .containsEntry("reconciliationPending", false)
                .containsEntry("changed", true)
                .containsEntry("metadataVersion", 8L);
        if (post) {
            verify(client, times(1)).update(any(Post.class));
            assertThat(((Post) state.get()).getSpec().getBaseSnapshot()).isEqualTo("base");
        } else {
            verify(client, times(1)).update(any(SinglePage.class));
            assertThat(((SinglePage) state.get()).getSpec().getBaseSnapshot()).isEqualTo("base");
        }
        var released = (String) output.get("releaseSnapshot");
        assertThat(released).startsWith("ai-release-").isNotEqualTo("head");
        assertThat(releasedContent(output).getRaw()).isEqualTo("Revised body\n");
        assertThat(snapshots.get(released).getSpec().getParentSnapshotName()).isEqualTo("head");
        verify(client, times(1)).create(any(Snapshot.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unpublishesWithoutDeletingOrOverwritingTheHeadAndReleasedBodies(boolean post) {
        fixture(post);
        var output = map(unpublish(post, Map.of("name", "existing", "expectedVersion", 7))
                .block());
        assertThat(output)
                .containsEntry("published", false)
                .containsEntry("desiredPublished", false)
                .containsEntry("releaseSnapshot", "base")
                .containsEntry("headSnapshot", "head")
                .containsEntry("reconciliationPending", false)
                .containsEntry("changed", true);
        assertThat(snapshots.get("base").getSpec().getRawPatch()).isEqualTo("Original body\n");
        verify(client, never()).delete(any());
        verify(client, never()).fetch(eq(Snapshot.class), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void publishingAnAlreadyConfirmedReviewedCloneDoesNotCreateAnotherSnapshot(boolean post) {
        fixture(post);
        var first = map(publish(post, publishInput(compare(post))).block());
        var repeated = map(publish(post, publishInput(compare(post))).block());
        assertThat(repeated)
                .containsEntry("changed", false)
                .containsEntry("releaseSnapshot", first.get("releaseSnapshot"));
        verify(client, times(1)).create(any(Snapshot.class));
    }

    @Test
    void repeatedPublicationAndWithdrawalDoNotPerformRedundantWrites() {
        fixture(true);
        var post = (Post) state.get();
        post.getSpec().setReleaseSnapshot("head");
        post.getMetadata().setAnnotations(Map.of(Post.LAST_RELEASED_SNAPSHOT_ANNO, "head"));
        assertThat(map(posts.publishPost(publishInput(compare(true))).block()))
                .containsEntry("changed", false)
                .containsEntry("reconciliationPending", false);
        post.getSpec().setPublish(false);
        post.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
        assertThat(map(posts.unpublishPost(Map.of("name", "existing", "expectedVersion", 7))
                        .block()))
                .containsEntry("changed", false)
                .containsEntry("published", false);
        verify(client, never()).update(any());
    }

    @Test
    void rejectsMetadataHeadOrContentChangesSinceTheReviewedComparison() {
        fixture(true);
        var input = publishInput(compare(true));
        var post = (Post) state.get();
        post.getMetadata().setVersion(8L);
        StepVerifier.create(posts.publishPost(input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        post.getMetadata().setVersion(7L);
        post.getSpec().setHeadSnapshot("new-head");
        StepVerifier.create(posts.publishPost(input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        post.getSpec().setHeadSnapshot("head");
        setHead("A new unreviewed draft\n", "<p>A new unreviewed draft</p>\n");
        StepVerifier.create(posts.publishPost(input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aConcurrentMetadataConflictIsNotRetriedAndTheUnlinkedReleaseCloneIsRemoved(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        if (post) {
            when(client.update(any(Post.class)))
                    .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));
        } else {
            when(client.update(any(SinglePage.class)))
                    .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));
        }
        StepVerifier.create(publish(post, input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any());
        verify(client, times(1)).delete(any(Snapshot.class));
        assertThat(snapshots).hasSize(2);
        assertThat(state.get().getMetadata().getAnnotations()).containsEntry(Post.LAST_RELEASED_SNAPSHOT_ANNO, "base");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSnapshotPersistenceFailureLeavesTheExistingPublishedBodyAndResourceUntouched(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        when(client.create(any(Snapshot.class))).thenReturn(Mono.error(new IllegalStateException("storage failed")));
        StepVerifier.create(publish(post, input))
                .expectErrorMessage("storage failed")
                .verify();
        verify(client, never()).update(any());
        verify(client, never()).delete(any());
        assertThat(snapshots).hasSize(2);
        assertThat(state.get().getMetadata().getVersion()).isEqualTo(7L);
        assertThat(state.get().getMetadata().getAnnotations()).containsEntry(Post.LAST_RELEASED_SNAPSHOT_ANNO, "base");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aConcurrentMutationOfTheOriginalHeadCannotChangeTheReviewedRelease(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        if (post) {
            when(client.update(any(Post.class))).thenAnswer(invocation -> {
                setHead("Concurrent unreviewed content\n", "<p>Concurrent unreviewed content</p>\n");
                return Mono.just(save(invocation.getArgument(0)));
            });
        } else {
            when(client.update(any(SinglePage.class))).thenAnswer(invocation -> {
                setHead("Concurrent unreviewed content\n", "<p>Concurrent unreviewed content</p>\n");
                return Mono.just(save(invocation.getArgument(0)));
            });
        }
        var output = map(publish(post, input).block());
        assertThat(output).containsEntry("published", true).containsEntry("reconciliationPending", false);
        assertThat(releasedContent(output).getRaw()).isEqualTo("Revised body\n");
        assertThat(releasedContent(output).getContent()).isEqualTo("<p>Revised body</p>\n");
        assertThat(ContentWrapper.patchSnapshot(snapshots.get("head"), snapshots.get("base"))
                        .getRaw())
                .isEqualTo("Concurrent unreviewed content\n");
        verify(client, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void initialDraftPublicationCopiesTheBaseAsANativeDeltaWithoutChangingHistory(boolean post) {
        fixture(post);
        var resource = state.get();
        resource.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
        resource.getMetadata().setAnnotations(Map.of());
        if (post) {
            var spec = ((Post) resource).getSpec();
            spec.setPublish(false);
            spec.setHeadSnapshot("base");
            spec.setReleaseSnapshot(null);
        } else {
            var spec = ((SinglePage) resource).getSpec();
            spec.setPublish(false);
            spec.setHeadSnapshot("base");
            spec.setReleaseSnapshot(null);
        }
        var before = JsonUtils.deepCopy(snapshots.get("base"));
        var output = map(publish(post, publishInput(compare(post))).block());
        assertThat(output).containsEntry("published", true).containsEntry("reconciliationPending", false);
        assertThat(releasedContent(output).getRaw()).isEqualTo("Original body\n");
        assertThat(snapshots.get("base")).isEqualTo(before);
        assertThat(snapshots
                        .get((String) output.get("releaseSnapshot"))
                        .getSpec()
                        .getParentSnapshotName())
                .isEqualTo("base");
        assertThat(compare(post)).containsEntry("changed", false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aBaseChangeDuringSnapshotCreationAbortsBeforePublicationAndRemovesTheOrphan(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        when(client.create(any(Snapshot.class)))
                .thenAnswer(invocation -> Mono.fromSupplier(() -> {
                    var snapshot = invocation.<Snapshot>getArgument(0);
                    snapshots.put(snapshot.getMetadata().getName(), snapshot);
                    snapshots.get("base").getSpec().setRawPatch("Unexpected trusted mutation\n");
                    return snapshot;
                }));
        StepVerifier.create(publish(post, input))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any());
        verify(client, times(1)).delete(any(Snapshot.class));
        assertThat(snapshots).hasSize(2);
        assertThat(state.get().getMetadata().getAnnotations()).containsEntry(Post.LAST_RELEASED_SNAPSHOT_ANNO, "base");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationBeforeTheResourceWriteRemovesOnlyTheUnlinkedClone(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        var waiting = Sinks.<Void>empty();
        when(client.fetch(eq(Snapshot.class), eq("base"))).thenAnswer(invocation -> {
            var base = snapshots.get("base");
            return snapshots.size() > 2 ? waiting.asMono().thenReturn(base) : Mono.just(base);
        });
        var failure = new AtomicReference<Throwable>();
        var subscription = publish(post, input).subscribe(ignored -> {}, failure::set);
        try {
            assertThat(failure.get()).isNull();
            assertThat(snapshots).hasSize(3);
            verify(client, times(1)).create(any(Snapshot.class));
        } finally {
            subscription.dispose();
        }
        assertThat(failure.get()).isNull();
        verify(client, never()).update(any());
        verify(client, times(1)).delete(any(Snapshot.class));
        assertThat(snapshots).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationAfterSnapshotPersistenceBeforeCreateEmitsStillCleansTheUnlinkedClone(boolean post) {
        fixture(post);
        var input = publishInput(compare(post));
        when(client.create(any(Snapshot.class))).thenAnswer(invocation -> {
            var snapshot = invocation.<Snapshot>getArgument(0);
            snapshot.getMetadata().setVersion(1L);
            snapshots.put(snapshot.getMetadata().getName(), snapshot);
            return Mono.never();
        });
        var failure = new AtomicReference<Throwable>();
        var subscription = publish(post, input).subscribe(ignored -> {}, failure::set);
        try {
            assertThat(failure.get()).isNull();
            assertThat(snapshots).hasSize(3);
            verify(client, times(1)).create(any(Snapshot.class));
        } finally {
            subscription.dispose();
        }
        assertThat(failure.get()).isNull();
        verify(client, never()).update(any());
        verify(client, times(1)).delete(any(Snapshot.class));
        assertThat(snapshots).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anAlreadyRunningConsoleSaveCompletesBeforeInitialDraftPublicationAndInvalidatesApproval(boolean post) {
        initialDraftFixture(post);
        var input = publishInput(compare(post));
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var finishSave = Sinks.<Void>empty();
        var nativeFailure = new AtomicReference<Throwable>();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put(consolePath(post) + "/content")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"rawType\":\"HTML\",\"raw\":\"Console edit\",\"content\":\"Console edit\"}"));
        var nativeSave = filter.filter(
                        exchange,
                        ignored -> finishSave.asMono().then(Mono.fromRunnable(() -> {
                            snapshots.get("base").getSpec().setRawPatch("Console edit");
                            snapshots.get("base").getSpec().setContentPatch("Console edit");
                            state.get().getMetadata().setVersion(8L);
                        })))
                .subscribe(ignored -> {}, nativeFailure::set);
        try {
            StepVerifier.create(publish(post, input))
                    .then(() -> {
                        verify(client, never()).create(any());
                        assertThat(finishSave.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
                    })
                    .expectError(OptimisticLockingFailureException.class)
                    .verify(Duration.ofSeconds(5));
            assertThat(nativeFailure.get()).isNull();
            assertThat(snapshots).hasSize(2);
            assertThat(state.get().getMetadata().getAnnotations()).doesNotContainKey(Post.LAST_RELEASED_SNAPSHOT_ANNO);
        } finally {
            nativeSave.dispose();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aStaleFullEditorSaveQueuedBehindInitialPublicationCannotMutateItsBase(boolean post) {
        initialDraftFixture(post);
        var input = publishInput(compare(post));
        var staleBody = JsonUtils.objectToJson(Map.of(
                post ? "post" : "page",
                JsonUtils.deepCopy(state.get()),
                "content",
                Map.of("rawType", "HTML", "raw", "Unreviewed append", "content", "Unreviewed append")));
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var continuePublication = Sinks.<Void>empty();
        var reads = new AtomicInteger();
        when(client.fetch(eq(Snapshot.class), eq("base"))).thenAnswer(invocation -> {
            var base = snapshots.get("base");
            return reads.incrementAndGet() == 2 ? continuePublication.asMono().thenReturn(base) : Mono.just(base);
        });
        var nativeEntered = new AtomicInteger();
        var rejected = Sinks.<Throwable>one();
        var nativeSubscription = new AtomicReference<reactor.core.Disposable>();
        try {
            StepVerifier.create(publish(post, input))
                    .then(() -> {
                        assertThat(snapshots).hasSize(3);
                        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put(consolePath(post))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(staleBody));
                        nativeSubscription.set(filter.filter(
                                        exchange,
                                        ignored -> Mono.fromRunnable(() -> {
                                            nativeEntered.incrementAndGet();
                                            snapshots.get("base").getSpec().setRawPatch("Unreviewed append");
                                        }))
                                .subscribe(ignored -> {}, error -> rejected.tryEmitValue(error)));
                        assertThat(nativeEntered).hasValue(0);
                        assertThat(continuePublication.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
                    })
                    .assertNext(value -> {
                        var output = map(value);
                        assertThat(output).containsEntry("published", true);
                        assertThat(releasedContent(output).getRaw()).isEqualTo("Original body\n");
                    })
                    .verifyComplete();
            var failure = rejected.asMono().block(Duration.ofSeconds(5));
            assertThat(failure).isInstanceOf(ResponseStatusException.class);
            assertThat(((ResponseStatusException) failure).getStatusCode().value())
                    .isEqualTo(409);
            assertThat(nativeEntered).hasValue(0);
            assertThat(snapshots.get("base").getSpec().getRawPatch()).isEqualTo("Original body\n");
        } finally {
            if (nativeSubscription.get() != null) {
                nativeSubscription.get().dispose();
            }
        }
    }

    /** Models Halo 2.26's fresh-metadata/old-spec Console helper, followed by its content request. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aStaleNativeConsoleSpecCannotUndoACompletedReviewedPublication(boolean post, boolean initiallyPublished) {
        editorFixture(post, initiallyPublished);
        var oldEditor = JsonUtils.deepCopy(state.get());
        var output = map(publish(post, publishInput(compare(post))).block());
        var approved = JsonUtils.deepCopy(state.get());
        var approvedSnapshots = copySnapshots();
        var payload = nativeEditorPayload(post, oldEditor, approved);
        assertThat(payload.getMetadata().getVersion())
                .isEqualTo(approved.getMetadata().getVersion());
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();

        StepVerifier.create(nativeCoreSave(filter, post, payload, coreEntered)
                        .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered))))
                .expectErrorSatisfies(HaloContentPublicationTest::assertEditorConflict)
                .verify();

        assertThat(coreEntered).hasValue(0);
        assertThat(contentEntered).hasValue(0);
        assertThat(state.get()).isEqualTo(approved);
        assertThat(snapshots).isEqualTo(approvedSnapshots);
        assertThat(output).containsEntry("published", true).containsEntry("desiredPublished", true);
        assertThat(releasedContent(output).getRaw())
                .isEqualTo(initiallyPublished ? "Revised body\n" : "Original body\n");
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** Fresh Console metadata and content saves must remain usable after tool-driven publication. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aFreshNativeConsoleSaveCreatesANewDraftAndPreservesTheReviewedRelease(
            boolean post, boolean initiallyPublished) {
        editorFixture(post, initiallyPublished);
        var output = map(publish(post, publishInput(compare(post))).block());
        var approved = JsonUtils.deepCopy(state.get());
        var release = (String) output.get("releaseSnapshot");
        var approvedSnapshot = JsonUtils.deepCopy(snapshots.get(release));
        var baseSnapshot = JsonUtils.deepCopy(snapshots.get("base"));
        var payload = nativeEditorPayload(post, approved, approved);
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();

        StepVerifier.create(nativeCoreSave(filter, post, payload, coreEntered)
                        .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered))))
                .verifyComplete();

        assertThat(coreEntered).hasValue(1);
        assertThat(contentEntered).hasValue(1);
        var current = compare(post);
        assertThat(current)
                .containsEntry("published", true)
                .containsEntry("desiredPublished", true)
                .containsEntry("headSnapshot", "native-editor-draft")
                .containsEntry("releaseSnapshot", release)
                .containsEntry("requestedReleaseSnapshot", release);
        assertThat(map(current.get("head"))).containsEntry("raw", "Native Console edit\n");
        assertThat(map(current.get("release")))
                .containsEntry("raw", initiallyPublished ? "Revised body\n" : "Original body\n");
        assertThat(snapshots.get(release)).isEqualTo(approvedSnapshot);
        assertThat(snapshots.get("base")).isEqualTo(baseSnapshot);
        assertThat(state.get().getMetadata().getVersion())
                .isEqualTo(approved.getMetadata().getVersion() + 2);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** SinglePageSettingModal publishes by selecting the unchanged current head in a core resource PUT. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aFreshNativePageSettingPublishMaySelectItsCurrentHead(boolean initiallyPublished) {
        editorFixture(false, initiallyPublished);
        var current = JsonUtils.deepCopy((SinglePage) state.get());
        var beforeSnapshots = copySnapshots();
        var payload = (SinglePage) nativeEditorPayload(false, current, current);
        payload.getSpec().setPublish(true);
        payload.getSpec().setReleaseSnapshot(current.getSpec().getHeadSnapshot());
        var entered = new AtomicInteger();
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());

        StepVerifier.create(nativeCoreSave(filter, false, payload, entered)).verifyComplete();

        assertThat(entered).hasValue(1);
        var saved = (SinglePage) state.get();
        assertThat(saved.getSpec().getBaseSnapshot())
                .isEqualTo(current.getSpec().getBaseSnapshot());
        assertThat(saved.getSpec().getHeadSnapshot())
                .isEqualTo(current.getSpec().getHeadSnapshot());
        assertThat(saved.getSpec().getReleaseSnapshot())
                .isEqualTo(current.getSpec().getHeadSnapshot());
        assertThat(saved.getSpec().getPublish()).isTrue();
        assertThat(snapshots).isEqualTo(beforeSnapshots);
        assertThat(compare(false)).containsEntry("published", true).containsEntry("changed", false);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** A queued native resource PUT must be validated only after the publication lease is released. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aStaleNativeConsoleResourceSaveQueuedBehindPublicationCannotReachEitherHandler(
            boolean post, boolean initiallyPublished) {
        editorFixture(post, initiallyPublished);
        var oldEditor = JsonUtils.deepCopy(state.get());
        var input = publishInput(compare(post));
        var payload = nativeEditorPayload(post, oldEditor, state.get());
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var continuePublication = Sinks.<Void>empty();
        var baseReads = new AtomicInteger();
        when(client.fetch(eq(Snapshot.class), eq("base"))).thenAnswer(invocation -> {
            var base = snapshots.get("base");
            return baseReads.incrementAndGet() == 2
                    ? continuePublication.asMono().thenReturn(base)
                    : Mono.just(base);
        });
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();
        var rejected = Sinks.<Throwable>one();
        var nativeSubscription = new AtomicReference<reactor.core.Disposable>();
        var publicationResult = new AtomicReference<Map<String, Object>>();

        try {
            StepVerifier.create(publish(post, input))
                    .then(() -> {
                        assertThat(snapshots).hasSize(3);
                        nativeSubscription.set(nativeCoreSave(filter, post, payload, coreEntered)
                                .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered)))
                                .subscribe(ignored -> {}, error -> rejected.tryEmitValue(error)));
                        assertThat(coreEntered).hasValue(0);
                        assertThat(contentEntered).hasValue(0);
                        assertThat(continuePublication.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
                    })
                    .assertNext(value -> publicationResult.set(map(value)))
                    .verifyComplete();

            assertEditorConflict(rejected.asMono().block(Duration.ofSeconds(5)));
            assertThat(coreEntered).hasValue(0);
            assertThat(contentEntered).hasValue(0);
            var output = publicationResult.get();
            assertThat(output).containsEntry("published", true).containsEntry("desiredPublished", true);
            assertThat(compare(post))
                    .containsEntry("headSnapshot", output.get("headSnapshot"))
                    .containsEntry("requestedReleaseSnapshot", output.get("releaseSnapshot"))
                    .containsEntry("releaseSnapshot", output.get("releaseSnapshot"))
                    .containsEntry("metadataVersion", output.get("metadataVersion"));
            assertThat(releasedContent(output).getRaw())
                    .isEqualTo(initiallyPublished ? "Revised body\n" : "Original body\n");
            assertThat(snapshots.get("base").getSpec().getRawPatch()).isEqualTo("Original body\n");
            assertThat(coordinator.activeKeyCount()).isZero();
        } finally {
            if (nativeSubscription.get() != null) {
                nativeSubscription.get().dispose();
            }
        }
    }

    /** The Console helper must not revive withdrawn content using fresh metadata and an old published spec. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aStaleNativeEditorCannotUndoToolUnpublication(boolean post, boolean separateDraft) {
        publishedEditorFixture(post, separateDraft);
        var oldEditor = JsonUtils.deepCopy(state.get());
        var output = map(unpublish(post, Map.of("name", "existing", "expectedVersion", 7))
                .block());
        var withdrawn = JsonUtils.deepCopy(state.get());
        assertThat(withdrawn.getMetadata().getAnnotations()).containsEntry("example.com/editor-note", "Keep this note");
        var unchangedSnapshots = copySnapshots();
        var payload = nativeEditorPayload(post, oldEditor, withdrawn);
        assertThat(payload.getMetadata().getVersion())
                .isEqualTo(withdrawn.getMetadata().getVersion());
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();

        StepVerifier.create(nativeCoreSave(filter, post, payload, coreEntered)
                        .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered))))
                .expectErrorSatisfies(HaloContentPublicationTest::assertEditorConflict)
                .verify();

        assertThat(coreEntered).hasValue(0);
        assertThat(contentEntered).hasValue(0);
        assertThat(state.get()).isEqualTo(withdrawn);
        assertThat(snapshots).isEqualTo(unchangedSnapshots);
        assertThat(output).containsEntry("published", false).containsEntry("desiredPublished", false);
        assertThat(compare(post))
                .containsEntry("published", false)
                .containsEntry("desiredPublished", false)
                .containsEntry("headSnapshot", separateDraft ? "head" : "base")
                .containsEntry("requestedReleaseSnapshot", "base")
                .containsEntry("releaseSnapshot", "base");
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** Reloading withdrawn content permits ordinary title and body edits without implicitly publishing it. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aFreshNativeEditorCanSaveWithdrawnContentWithoutRepublishing(boolean post, boolean separateDraft) {
        publishedEditorFixture(post, separateDraft);
        unpublish(post, Map.of("name", "existing", "expectedVersion", 7)).block();
        var withdrawn = JsonUtils.deepCopy(state.get());
        var released = JsonUtils.deepCopy(snapshots.get("base"));
        var payload = nativeEditorPayload(post, withdrawn, withdrawn);
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();

        StepVerifier.create(nativeCoreSave(filter, post, payload, coreEntered)
                        .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered))))
                .verifyComplete();

        assertThat(coreEntered).hasValue(1);
        assertThat(contentEntered).hasValue(1);
        var current = compare(post);
        assertThat(current)
                .containsEntry("title", "Title changed in the native Console editor")
                .containsEntry("published", false)
                .containsEntry("desiredPublished", false)
                .containsEntry("headSnapshot", separateDraft ? "head" : "native-editor-draft")
                .containsEntry("releaseSnapshot", "base")
                .containsEntry("requestedReleaseSnapshot", "base");
        assertThat(map(current.get("head"))).containsEntry("raw", "Native Console edit\n");
        assertThat(snapshots.get("base")).isEqualTo(released);
        assertThat(state.get().getMetadata().getVersion())
                .isEqualTo(withdrawn.getMetadata().getVersion() + 2);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** A fresh native publish action remains possible after an explicit withdrawal. */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aFreshNativeEditorCanExplicitlyRepublishWithdrawnContent(boolean post, boolean separateDraft) {
        publishedEditorFixture(post, separateDraft);
        unpublish(post, Map.of("name", "existing", "expectedVersion", 7)).block();
        var withdrawn = JsonUtils.deepCopy(state.get());
        var unchangedSnapshots = copySnapshots();
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var entered = new AtomicInteger();

        StepVerifier.create(nativeRepublish(filter, post, withdrawn, entered)).verifyComplete();

        assertThat(entered).hasValue(1);
        var current = compare(post);
        var selected = separateDraft ? "head" : "base";
        assertThat(current)
                .containsEntry("published", true)
                .containsEntry("desiredPublished", true)
                .containsEntry("headSnapshot", selected)
                .containsEntry("releaseSnapshot", selected)
                .containsEntry("requestedReleaseSnapshot", selected)
                .containsEntry("changed", false);
        assertThat(map(current.get("release")))
                .containsEntry("raw", separateDraft ? "Revised body\n" : "Original body\n");
        assertThat(snapshots).isEqualTo(unchangedSnapshots);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /** Repeated withdrawal is a no-op and must not invalidate a current withdrawn editor's metadata. */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedToolUnpublicationKeepsACurrentNativeEditorUsable(boolean post) {
        publishedEditorFixture(post, true);
        unpublish(post, Map.of("name", "existing", "expectedVersion", 7)).block();
        var currentEditor = JsonUtils.deepCopy(state.get());
        var unchangedSnapshots = copySnapshots();
        var output = map(unpublish(
                        post,
                        Map.of(
                                "name",
                                "existing",
                                "expectedVersion",
                                currentEditor.getMetadata().getVersion()))
                .block());

        assertThat(output).containsEntry("changed", false).containsEntry("published", false);
        assertThat(state.get()).isEqualTo(currentEditor);
        assertThat(state.get().getMetadata()).isEqualTo(currentEditor.getMetadata());
        assertThat(state.get().getMetadata().getAnnotations())
                .containsEntry("example.com/editor-note", "Keep this note");
        assertThat(snapshots).isEqualTo(unchangedSnapshots);
        if (post) {
            verify(client, times(1)).update(any(Post.class));
        } else {
            verify(client, times(1)).update(any(SinglePage.class));
        }

        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var entered = new AtomicInteger();
        var payload = nativeEditorPayload(post, currentEditor, state.get());
        StepVerifier.create(nativeCoreSave(filter, post, payload, entered)).verifyComplete();
        assertThat(entered).hasValue(1);
        assertThat(compare(post))
                .containsEntry("published", false)
                .containsEntry("desiredPublished", false)
                .containsEntry("headSnapshot", "head")
                .containsEntry("releaseSnapshot", "base");
        assertThat(snapshots).isEqualTo(unchangedSnapshots);
        assertThat(coordinator.activeKeyCount()).isZero();
    }

    /**
     * Even a fresh-version native PUT queued after the withdrawal write must retain the editor's observed lifecycle.
     */
    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void aStaleNativeEditorQueuedBehindUnpublicationCannotReviveContent(boolean post, boolean separateDraft) {
        publishedEditorFixture(post, separateDraft);
        var oldEditor = JsonUtils.deepCopy(state.get());
        var unchangedSnapshots = copySnapshots();
        var continueUnpublication = Sinks.<Void>empty();
        if (post) {
            doAnswer(invocation -> {
                        var saved = (Post) save(invocation.getArgument(0));
                        return continueUnpublication.asMono().thenReturn(saved);
                    })
                    .when(client)
                    .update(any(Post.class));
        } else {
            doAnswer(invocation -> {
                        var saved = (SinglePage) save(invocation.getArgument(0));
                        return continueUnpublication.asMono().thenReturn(saved);
                    })
                    .when(client)
                    .update(any(SinglePage.class));
        }
        var filter = new HaloContentMutationWebFilter(client, coordinator, new MockEnvironment());
        var coreEntered = new AtomicInteger();
        var contentEntered = new AtomicInteger();
        var rejected = Sinks.<Throwable>one();
        var nativeSubscription = new AtomicReference<reactor.core.Disposable>();
        var withdrawn = new AtomicReference<Extension>();

        try {
            StepVerifier.create(unpublish(post, Map.of("name", "existing", "expectedVersion", 7)))
                    .then(() -> {
                        withdrawn.set(JsonUtils.deepCopy(state.get()));
                        var payload = nativeEditorPayload(post, oldEditor, withdrawn.get());
                        assertThat(payload.getMetadata().getVersion()).isEqualTo(8L);
                        nativeSubscription.set(nativeCoreSave(filter, post, payload, coreEntered)
                                .then(Mono.defer(() -> nativeContentSave(filter, post, contentEntered)))
                                .subscribe(ignored -> {}, error -> rejected.tryEmitValue(error)));
                        assertThat(coreEntered).hasValue(0);
                        assertThat(contentEntered).hasValue(0);
                        assertThat(continueUnpublication.tryEmitEmpty()).isEqualTo(Sinks.EmitResult.OK);
                    })
                    .assertNext(value -> assertThat(map(value))
                            .containsEntry("published", false)
                            .containsEntry("desiredPublished", false))
                    .verifyComplete();

            assertEditorConflict(rejected.asMono().block(Duration.ofSeconds(5)));
            assertThat(coreEntered).hasValue(0);
            assertThat(contentEntered).hasValue(0);
            assertThat(state.get()).isEqualTo(withdrawn.get());
            assertThat(snapshots).isEqualTo(unchangedSnapshots);
            assertThat(coordinator.activeKeyCount()).isZero();
        } finally {
            if (nativeSubscription.get() != null) {
                nativeSubscription.get().dispose();
            }
        }
    }

    @Test
    void aResourceLinkedCloneSurvivesCancellationWhileReconciliationIsPending() {
        fixture(true);
        reconcile = false;
        var failure = new AtomicReference<Throwable>();
        var subscription = posts.publishPost(publishInput(compare(true))).subscribe(ignored -> {}, failure::set);
        String release;
        try {
            assertThat(failure.get()).isNull();
            release = ((Post) state.get()).getSpec().getReleaseSnapshot();
            assertThat(release).startsWith("ai-release-");
            verify(client, times(1)).update(any(Post.class));
        } finally {
            subscription.dispose();
        }
        assertThat(failure.get()).isNull();
        assertThat(snapshots).containsKey(release);
        verify(client, never()).delete(any());
    }

    @Test
    void doesNotClaimSuccessUntilTheReconcilerHasConfirmedTheExpectedRelease() {
        fixture(true);
        reconcile = false;
        var input = publishInput(compare(true));
        StepVerifier.withVirtualTime(() -> posts.publishPost(input))
                .thenAwait(Duration.ofSeconds(3))
                .assertNext(value -> {
                    var result = map(value);
                    assertThat(result)
                            .containsEntry("releaseSnapshot", "base")
                            .containsEntry("desiredPublished", true)
                            .containsEntry("reconciliationPending", true);
                    assertThat((String) result.get("requestedReleaseSnapshot")).startsWith("ai-release-");
                })
                .verifyComplete();
        verify(client, times(1)).update(any(Post.class));
    }

    @Test
    void observesAReconciledReleaseOnTheNextPollWithoutChangingToANewerHead() {
        fixture(true);
        reconcile = false;
        var input = publishInput(compare(true));
        StepVerifier.withVirtualTime(() -> posts.publishPost(input))
                .then(() -> {
                    var post = (Post) state.get();
                    post.getSpec().setHeadSnapshot("next-unapproved-head");
                    post.getMetadata()
                            .setAnnotations(Map.of(
                                    Post.LAST_RELEASED_SNAPSHOT_ANNO,
                                    post.getSpec().getReleaseSnapshot()));
                })
                .thenAwait(Duration.ofMillis(200))
                .assertNext(value -> assertThat(map(value))
                        .containsEntry(
                                "releaseSnapshot",
                                ((Post) state.get()).getSpec().getReleaseSnapshot())
                        .containsEntry("headSnapshot", "next-unapproved-head")
                        .containsEntry("reconciliationPending", false))
                .verifyComplete();
        verify(client, times(1)).update(any(Post.class));
    }

    @Test
    void aConcurrentDifferentPublicationIsReportedAsAConflictInsteadOfOverwritten() {
        fixture(true);
        reconcile = false;
        var input = publishInput(compare(true));
        StepVerifier.withVirtualTime(() -> posts.publishPost(input))
                .then(() -> ((Post) state.get()).getSpec().setReleaseSnapshot("other-approved-release"))
                .thenAwait(Duration.ofMillis(200))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any(Post.class));
    }

    @Test
    void comparisonRejectsAMetadataChangeWhileReadingTheTwoBodies() {
        fixture(true);
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.just((Post) state.get()), Mono.just(post(8)));
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
    }

    @Test
    void rejectsForeignDeletedMissingAndInvalidBaseSnapshots() {
        fixture(true);
        var input = publishInput(compare(true));
        var head = snapshots.get("head");
        head.getSpec().setSubjectRef(Ref.of(post(1)));
        head.getSpec().getSubjectRef().setName("someone-else");
        StepVerifier.create(posts.publishPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        head.getSpec().setSubjectRef(Ref.of(state.get()));
        head.getMetadata().setDeletionTimestamp(Instant.now());
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        head.getMetadata().setDeletionTimestamp(null);
        snapshots.remove("head");
        StepVerifier.create(posts.publishPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        snapshots.get("base").getMetadata().setAnnotations(Map.of());
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).update(any());
    }

    @Test
    void rejectsMissingAndRecycledOwnersEvenIfTheirContentStillExists() {
        fixture(true);
        var input = publishInput(compare(true));
        ((Post) state.get()).getSpec().setDeleted(true);
        StepVerifier.create(posts.publishPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(posts.unpublishPost(Map.of("name", "existing", "expectedVersion", 7)))
                .expectError(IllegalArgumentException.class)
                .verify();
        ((Post) state.get()).getSpec().setDeleted(false);
        state.get().getMetadata().setDeletionTimestamp(Instant.now());
        StepVerifier.create(posts.comparePostVersions(Map.of("name", "existing")))
                .expectError(IllegalArgumentException.class)
                .verify();
        when(client.fetch(Post.class, "existing")).thenReturn(Mono.empty());
        StepVerifier.create(posts.publishPost(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).update(any());
    }

    @Test
    void validatesNamesVersionsFingerprintsAndUnknownFieldsBeforeReadingAnyResource() {
        for (var version :
                List.of(0, -1, 7.5, "7", Double.NaN, Double.POSITIVE_INFINITY, new BigDecimal("9223372036854775808"))) {
            StepVerifier.create(pages.unpublishPage(Map.of("name", "existing", "expectedVersion", version)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        for (var input : List.<Map<String, Object>>of(
                Map.of("name", "../invalid"), Map.of("name", "existing", "caller", "admin"))) {
            StepVerifier.create(posts.comparePostVersions(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        for (var fingerprint : List.of("", "A".repeat(64), "0".repeat(63), "x".repeat(64))) {
            StepVerifier.create(pages.publishPage(Map.of(
                            "name",
                            "existing",
                            "expectedVersion",
                            7,
                            "headSnapshot",
                            "head",
                            "expectedContentFingerprint",
                            fingerprint)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(posts.publishPost(Map.of("name", "existing", "expectedVersion", 7, "headSnapshot", "head")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client, storedSettings);
    }

    @Test
    void configuredLimitFailuresAreNotSilentlyIgnored() {
        fixture(false);
        when(storedSettings.configuration("ai-foundation/get_page"))
                .thenReturn(Mono.error(new IllegalStateException("Unavailable")));
        StepVerifier.create(pages.comparePageVersions(Map.of("name", "existing")))
                .expectErrorMessage("Unavailable")
                .verify();
        verify(client, never()).update(any());
    }

    private void editorFixture(boolean post, boolean initiallyPublished) {
        if (initiallyPublished) {
            fixture(post);
        } else {
            initialDraftFixture(post);
        }
    }

    private void publishedEditorFixture(boolean post, boolean separateDraft) {
        fixture(post);
        var annotations = new HashMap<>(state.get().getMetadata().getAnnotations());
        annotations.put("example.com/editor-note", "Keep this note");
        state.get().getMetadata().setAnnotations(annotations);
        if (!separateDraft) {
            if (post) {
                ((Post) state.get()).getSpec().setHeadSnapshot("base");
            } else {
                ((SinglePage) state.get()).getSpec().setHeadSnapshot("base");
            }
        }
    }

    /** Halo 2.26's Console helper refreshes metadata while retaining the editor's complete spec. */
    private static Extension nativeEditorPayload(boolean post, Extension editor, Extension latest) {
        if (post) {
            var payload = JsonUtils.deepCopy((Post) latest);
            payload.setSpec(JsonUtils.deepCopy(((Post) editor).getSpec()));
            payload.getSpec().setTitle("Title changed in the native Console editor");
            payload.getMetadata().setAnnotations(editor.getMetadata().getAnnotations());
            return payload;
        }
        var payload = JsonUtils.deepCopy((SinglePage) latest);
        payload.setSpec(JsonUtils.deepCopy(((SinglePage) editor).getSpec()));
        payload.getSpec().setTitle("Title changed in the native Console editor");
        payload.getMetadata().setAnnotations(editor.getMetadata().getAnnotations());
        return payload;
    }

    /** Uses the ordinary resource route and verifies the native handler receives its exact original bytes. */
    private Mono<Void> nativeCoreSave(
            HaloContentMutationWebFilter filter, boolean post, Extension payload, AtomicInteger entered) {
        var body = JsonUtils.objectToJson(payload);
        var path = "/apis/content.halo.run/v1alpha1/" + (post ? "posts" : "singlepages") + "/existing";
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));
        return filter.filter(
                exchange,
                current -> DataBufferUtils.join(current.getRequest().getBody()).flatMap(buffer -> {
                    try {
                        assertThat(buffer.toString(StandardCharsets.UTF_8)).isEqualTo(body);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                    entered.incrementAndGet();
                    assertThat(payload.getMetadata().getVersion())
                            .isEqualTo(state.get().getMetadata().getVersion());
                    return post
                            ? client.update((Post) payload).then()
                            : client.update((SinglePage) payload).then();
                }));
    }

    /**
     * Models Halo's native content-save choices: fork a published head or edit the existing separate draft. Both
     * choices retain the owning resource's publication intent and released body.
     */
    private Mono<Void> nativeContentSave(HaloContentMutationWebFilter filter, boolean post, AtomicInteger entered) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.put(consolePath(post) + "/content")
                .contentType(MediaType.APPLICATION_JSON)
                .body(JsonUtils.objectToJson(Map.of(
                        "rawType",
                        "HTML",
                        "raw",
                        "Native Console edit\n",
                        "content",
                        "<p>Native Console edit</p>\n"))));
        return filter.filter(
                exchange,
                ignored -> Mono.defer(() -> {
                    entered.incrementAndGet();
                    var resource = JsonUtils.deepCopy(state.get());
                    var head = post
                            ? ((Post) resource).getSpec().getHeadSnapshot()
                            : ((SinglePage) resource).getSpec().getHeadSnapshot();
                    var release = post
                            ? ((Post) resource).getSpec().getReleaseSnapshot()
                            : ((SinglePage) resource).getSpec().getReleaseSnapshot();
                    var base = post
                            ? ((Post) resource).getSpec().getBaseSnapshot()
                            : ((SinglePage) resource).getSpec().getBaseSnapshot();
                    assertThat(base).isEqualTo("base");
                    var draft = JsonUtils.deepCopy(snapshots.get(head));
                    var fork = java.util.Objects.equals(head, release);
                    if (fork) {
                        draft.setMetadata(metadata("native-editor-draft", 1));
                        draft.getSpec().setParentSnapshotName(head);
                    }
                    draft.getSpec()
                            .setRawPatch(PatchUtils.diffToJsonPatch(
                                    snapshots.get(base).getSpec().getRawPatch(), "Native Console edit\n"));
                    draft.getSpec()
                            .setContentPatch(PatchUtils.diffToJsonPatch(
                                    snapshots.get(base).getSpec().getContentPatch(), "<p>Native Console edit</p>\n"));
                    var savedSnapshot = fork ? client.create(draft) : client.update(draft);
                    return savedSnapshot.flatMap(created -> {
                        if (post) {
                            var article = (Post) resource;
                            article.getSpec()
                                    .setHeadSnapshot(created.getMetadata().getName());
                            return client.update(article).then();
                        }
                        var page = (SinglePage) resource;
                        page.getSpec().setHeadSnapshot(created.getMetadata().getName());
                        return client.update(page).then();
                    });
                }));
    }

    /** Console PostEditor uses its publish endpoint; page settings use a fresh resource PUT selecting the head. */
    private Mono<Void> nativeRepublish(
            HaloContentMutationWebFilter filter, boolean post, Extension editor, AtomicInteger entered) {
        if (post) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.put(consolePath(true) + "/publish"));
            return filter.filter(
                    exchange,
                    ignored -> client.fetch(Post.class, "existing").flatMap(current -> {
                        entered.incrementAndGet();
                        current.getSpec().setPublish(true);
                        current.getSpec().setReleaseSnapshot(current.getSpec().getHeadSnapshot());
                        return client.update(current).then();
                    }));
        }
        var payload = (SinglePage) nativeEditorPayload(false, editor, state.get());
        payload.getSpec().setPublish(true);
        payload.getSpec().setReleaseSnapshot(payload.getSpec().getHeadSnapshot());
        return nativeCoreSave(filter, false, payload, entered);
    }

    private Map<String, Snapshot> copySnapshots() {
        return snapshots.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> JsonUtils.deepCopy(entry.getValue())));
    }

    private static void assertEditorConflict(Throwable error) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
    }

    private void fixture(boolean post) {
        state.set(post ? post(7) : page(7));
        snapshots.put("base", base(state.get()));
        setHead("Revised body\n", "<p>Revised body</p>\n");
        if (post) {
            when(client.fetch(Post.class, "existing"))
                    .thenAnswer(invocation -> Mono.fromSupplier(() -> JsonUtils.deepCopy((Post) state.get())));
            when(client.update(any(Post.class))).thenAnswer(invocation -> Mono.just(save(invocation.getArgument(0))));
        } else {
            when(client.fetch(SinglePage.class, "existing"))
                    .thenAnswer(invocation -> Mono.fromSupplier(() -> JsonUtils.deepCopy((SinglePage) state.get())));
            when(client.update(any(SinglePage.class)))
                    .thenAnswer(invocation -> Mono.just(save(invocation.getArgument(0))));
        }
        when(client.fetch(eq(Snapshot.class), any()))
                .thenAnswer(invocation -> Mono.justOrEmpty(snapshots.get(invocation.<String>getArgument(1))));
        when(client.create(any(Snapshot.class)))
                .thenAnswer(invocation -> Mono.fromSupplier(() -> {
                    var snapshot = JsonUtils.deepCopy(invocation.<Snapshot>getArgument(0));
                    snapshot.getMetadata().setVersion(1L);
                    snapshots.put(snapshot.getMetadata().getName(), snapshot);
                    return snapshot;
                }));
        when(client.delete(any(Snapshot.class)))
                .thenAnswer(invocation -> Mono.fromSupplier(() -> snapshots.remove(
                        invocation.<Snapshot>getArgument(0).getMetadata().getName())));
        when(client.update(any(Snapshot.class)))
                .thenAnswer(invocation -> Mono.fromSupplier(() -> {
                    var snapshot = JsonUtils.deepCopy(invocation.<Snapshot>getArgument(0));
                    snapshot.getMetadata().setVersion(snapshot.getMetadata().getVersion() + 1);
                    snapshots.put(snapshot.getMetadata().getName(), snapshot);
                    return snapshot;
                }));
    }

    private Extension save(Extension input) {
        input.getMetadata().setVersion(input.getMetadata().getVersion() + 1);
        if (reconcile) {
            var publish = input instanceof Post post
                    ? post.getSpec().getPublish()
                    : ((SinglePage) input).getSpec().getPublish();
            var labels = new HashMap<>(
                    input.getMetadata().getLabels() == null
                            ? Map.<String, String>of()
                            : input.getMetadata().getLabels());
            labels.put(Post.PUBLISHED_LABEL, publish.toString());
            input.getMetadata().setLabels(labels);
            if (publish) {
                var release = input instanceof Post post
                        ? post.getSpec().getReleaseSnapshot()
                        : ((SinglePage) input).getSpec().getReleaseSnapshot();
                var annotations = new HashMap<>(
                        input.getMetadata().getAnnotations() == null
                                ? Map.<String, String>of()
                                : input.getMetadata().getAnnotations());
                annotations.put(Post.LAST_RELEASED_SNAPSHOT_ANNO, release);
                input.getMetadata().setAnnotations(annotations);
            }
        }
        state.set(input);
        return input;
    }

    private void setHead(String raw, String content) {
        var head = new Snapshot();
        head.setMetadata(metadata("head", 3));
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(state.get()));
        spec.setRawType("HTML");
        spec.setRawPatch(PatchUtils.diffToJsonPatch("Original body\n", raw));
        spec.setContentPatch(PatchUtils.diffToJsonPatch("<p>Original body</p>\n", content));
        head.setSpec(spec);
        snapshots.put("head", head);
    }

    private static Snapshot base(Extension owner) {
        var base = new Snapshot();
        base.setMetadata(metadata("base", 1));
        base.getMetadata().setAnnotations(Map.of(Snapshot.KEEP_RAW_ANNO, "true"));
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(owner));
        spec.setRawType("HTML");
        spec.setRawPatch("Original body\n");
        spec.setContentPatch("<p>Original body</p>\n");
        base.setSpec(spec);
        return base;
    }

    private void initialDraftFixture(boolean post) {
        fixture(post);
        var resource = state.get();
        resource.getMetadata().setLabels(Map.of(Post.PUBLISHED_LABEL, "false"));
        resource.getMetadata().setAnnotations(Map.of());
        if (post) {
            var spec = ((Post) resource).getSpec();
            spec.setPublish(false);
            spec.setHeadSnapshot("base");
            spec.setReleaseSnapshot(null);
        } else {
            var spec = ((SinglePage) resource).getSpec();
            spec.setPublish(false);
            spec.setHeadSnapshot("base");
            spec.setReleaseSnapshot(null);
        }
    }

    private static String consolePath(boolean post) {
        return "/apis/api.console.halo.run/v1alpha1/" + (post ? "posts" : "singlepages") + "/existing";
    }

    private ContentWrapper releasedContent(Map<String, Object> output) {
        return ContentWrapper.patchSnapshot(
                snapshots.get((String) output.get("releaseSnapshot")), snapshots.get("base"));
    }

    private Map<String, Object> compare(boolean post) {
        return map((post
                        ? posts.comparePostVersions(Map.of("name", "existing"))
                        : pages.comparePageVersions(Map.of("name", "existing")))
                .block());
    }

    private Mono<Object> publish(boolean post, Map<String, Object> input) {
        return post ? posts.publishPost(input) : pages.publishPage(input);
    }

    private Mono<Object> unpublish(boolean post, Map<String, Object> input) {
        return post ? posts.unpublishPost(input) : pages.unpublishPage(input);
    }

    private static Map<String, Object> publishInput(Map<String, Object> comparison) {
        return Map.of(
                "name",
                comparison.get("name"),
                "expectedVersion",
                comparison.get("metadataVersion"),
                "headSnapshot",
                comparison.get("headSnapshot"),
                "expectedContentFingerprint",
                comparison.get("headContentFingerprint"));
    }

    private List<Function<Map<String, Object>, Mono<Object>>> actions() {
        return List.of(
                posts::comparePostVersions,
                pages::comparePageVersions,
                posts::publishPost,
                posts::unpublishPost,
                pages::publishPage,
                pages::unpublishPage);
    }

    private static Post post(long version) {
        var post = new Post();
        post.setMetadata(contentMetadata(version));
        var spec = new Post.PostSpec();
        spec.setTitle("Title");
        spec.setSlug("title");
        spec.setDeleted(false);
        spec.setPublish(true);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("base");
        post.setSpec(spec);
        return post;
    }

    private static SinglePage page(long version) {
        var page = new SinglePage();
        page.setMetadata(contentMetadata(version));
        var spec = new SinglePage.SinglePageSpec();
        spec.setTitle("Title");
        spec.setSlug("title");
        spec.setDeleted(false);
        spec.setPublish(true);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("base");
        page.setSpec(spec);
        return page;
    }

    private static Metadata contentMetadata(long version) {
        var metadata = metadata("existing", version);
        metadata.setAnnotations(Map.of(Post.LAST_RELEASED_SNAPSHOT_ANNO, "base"));
        metadata.setLabels(Map.of(Post.PUBLISHED_LABEL, "true"));
        return metadata;
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
}
