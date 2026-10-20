package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
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

class HaloDraftContentServiceTest {
    private static final String ACTOR = "author";
    private static final Map<String, Object> BODY =
            Map.of("rawType", "MARKDOWN", "raw", "# Revised\n\nBody\n", "content", "<h1>Revised</h1>\n<p>Body</p>\n");
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloDraftContentService service = new HaloDraftContentService(client);

    @BeforeEach
    void snapshotStorage() {
        when(client.create(any(Snapshot.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.delete(any(Snapshot.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.delete(any(Post.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
    }

    @Test
    void createsSubjectBeforeFullBaseContentAndReturnsVersionUsableForNextEdit() {
        var original = post(0);
        original.getSpec().setBaseSnapshot(null);
        original.getSpec().setHeadSnapshot(null);
        original.getSpec().setReleaseSnapshot(null);
        when(client.create(any(Post.class))).thenAnswer(call -> Mono.just(JsonUtils.deepCopy(call.getArgument(0))));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(original));
        when(client.update(any(Post.class))).thenAnswer(call -> {
            Post saved = JsonUtils.deepCopy(call.getArgument(0));
            saved.getMetadata().setVersion(1L);
            return Mono.just(saved);
        });
        var saved = service.createPost(original, BODY, ACTOR).block();
        assertThat(saved.getMetadata().getVersion()).isEqualTo(1);
        var snapshot = ArgumentCaptor.forClass(Snapshot.class);
        var order = inOrder(client);
        order.verify(client).create(any(Post.class));
        order.verify(client).create(snapshot.capture());
        order.verify(client).fetch(Post.class, "post");
        order.verify(client).update(any(Post.class));
        var base = snapshot.getValue();
        assertThat(Snapshot.isBaseSnapshot(base)).isTrue();
        assertThat(base.getSpec().getSubjectRef()).isEqualTo(Ref.of(saved));
        assertThat(base.getSpec().getOwner()).isEqualTo(ACTOR);
        assertThat(base.getSpec().getContributors()).containsExactly(ACTOR);
        assertThat(base.getSpec().getLastModifyTime()).isNotNull();
        assertThat(base.getSpec().getParentSnapshotName()).isNull();
        var content = ContentWrapper.patchSnapshot(base, base);
        assertThat(content.getRaw()).isEqualTo(BODY.get("raw"));
        assertThat(content.getContent()).isEqualTo(BODY.get("content"));
        assertThat(content.getRawType()).isEqualTo("MARKDOWN");
        assertThat(saved.getSpec().getBaseSnapshot())
                .isEqualTo(base.getMetadata().getName());
        assertThat(saved.getSpec().getHeadSnapshot())
                .isEqualTo(base.getMetadata().getName());
        assertThat(saved.getSpec().getReleaseSnapshot()).isNull();
        assertThat(original.getSpec().getHeadSnapshot()).isNull();
    }

    @Test
    void createsPageUsingTheSamePublicSnapshotContract() {
        var page = new SinglePage();
        page.setMetadata(metadata("page", 0));
        var spec = new SinglePage.SinglePageSpec();
        spec.setOwner(ACTOR);
        page.setSpec(spec);
        when(client.create(any(SinglePage.class))).thenReturn(Mono.just(page));
        when(client.fetch(SinglePage.class, "page")).thenReturn(Mono.just(page));
        when(client.update(any(SinglePage.class))).thenAnswer(call -> {
            SinglePage saved = JsonUtils.deepCopy(call.getArgument(0));
            saved.getMetadata().setVersion(1L);
            return Mono.just(saved);
        });
        var saved = service.createPage(page, BODY, ACTOR).block();
        var snapshot = ArgumentCaptor.forClass(Snapshot.class);
        verify(client).create(snapshot.capture());
        assertThat(snapshot.getValue().getSpec().getSubjectRef()).isEqualTo(Ref.of(page));
        assertThat(saved.getMetadata().getVersion()).isPositive();
        assertThat(saved.getSpec().getBaseSnapshot()).isEqualTo(saved.getSpec().getHeadSnapshot());
    }

    @Test
    void editsPageBodyWithPageSnapshotReferencesAndPreservesThePublishedVersion() {
        var page = page(7);
        var base = base(page);
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head(page, base)));
        when(client.update(any(SinglePage.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        var saved = service.updatePage(page, BODY, ACTOR).block();

        var snapshot = ArgumentCaptor.forClass(Snapshot.class);
        verify(client).create(snapshot.capture());
        var patch = snapshot.getValue();
        assertThat(patch.getSpec().getSubjectRef()).isEqualTo(Ref.of(page));
        assertThat(patch.getSpec().getParentSnapshotName()).isEqualTo("head");
        assertThat(ContentWrapper.patchSnapshot(patch, base).getRaw()).isEqualTo(BODY.get("raw"));
        assertThat(saved.getSpec().getHeadSnapshot())
                .isEqualTo(patch.getMetadata().getName());
        assertThat(saved.getSpec().getBaseSnapshot()).isEqualTo("base");
        assertThat(saved.getSpec().getReleaseSnapshot()).isEqualTo("release");
        assertThat(saved.getMetadata().getVersion()).isEqualTo(7);
        verify(client, never()).update(any(Post.class));
        verify(client, never()).update(any(Snapshot.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedPageCreateCleansUpOnlyTheUnchangedIncompletePage(boolean concurrentEdit) {
        var created = page(0);
        created.getSpec().setBaseSnapshot(null);
        created.getSpec().setHeadSnapshot(null);
        created.getSpec().setReleaseSnapshot(null);
        var current = JsonUtils.deepCopy(created);
        if (concurrentEdit) {
            current.getSpec().setTitle("Edited by another user");
        }
        when(client.create(any(SinglePage.class))).thenReturn(Mono.just(created));
        when(client.create(any(Snapshot.class))).thenReturn(Mono.error(new IllegalStateException("snapshot failed")));
        when(client.fetch(SinglePage.class, "page")).thenReturn(Mono.just(current));
        when(client.delete(any(SinglePage.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));

        StepVerifier.create(service.createPage(created, BODY, ACTOR))
                .expectErrorMessage("snapshot failed")
                .verify();

        if (concurrentEdit) {
            verify(client, never()).delete(any(SinglePage.class));
        } else {
            verify(client).delete(current);
        }
        verify(client, never()).delete(any(Post.class));
    }

    @Test
    void ambiguousPageWriteKeepsItsNewSnapshotWhenTheSavedPageReferencesIt() {
        var current = page(7);
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base(current)));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head(current, base(current))));
        var latest = new AtomicReference<SinglePage>();
        when(client.update(any(SinglePage.class))).thenAnswer(call -> {
            latest.set(JsonUtils.deepCopy(call.getArgument(0)));
            return Mono.error(new IllegalStateException("response lost after persistence"));
        });
        when(client.fetch(SinglePage.class, "page")).thenAnswer(call -> Mono.just(latest.get()));

        StepVerifier.create(service.updatePage(current, BODY, ACTOR))
                .expectErrorMessage("response lost after persistence")
                .verify();

        verify(client, never()).delete(any());
    }

    @Test
    void bodyEditCreatesNewPatchRelativeToBaseWhileExistingSnapshotsStayImmutable() {
        var post = post(7);
        var base = base(post);
        var head = head(post, base);
        var baseBefore = JsonUtils.objectToJson(base);
        var headBefore = JsonUtils.objectToJson(head);
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head));
        when(client.update(any(Post.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var saved = service.updatePost(post, BODY, ACTOR).block();
        var snapshot = ArgumentCaptor.forClass(Snapshot.class);
        verify(client).create(snapshot.capture());
        var patch = snapshot.getValue();
        assertThat(Snapshot.isBaseSnapshot(patch)).isFalse();
        assertThat(patch.getMetadata().getName()).isNotIn("base", "head", "release");
        assertThat(patch.getSpec().getParentSnapshotName()).isEqualTo("head");
        assertThat(patch.getSpec().getOwner()).isEqualTo(ACTOR);
        assertThat(patch.getSpec().getContributors()).containsExactly("previous-author", ACTOR);
        var reconstructed = ContentWrapper.patchSnapshot(patch, base);
        assertThat(reconstructed.getRaw()).isEqualTo(BODY.get("raw"));
        assertThat(reconstructed.getContent()).isEqualTo(BODY.get("content"));
        assertThat(saved.getSpec().getHeadSnapshot())
                .isEqualTo(patch.getMetadata().getName());
        assertThat(saved.getSpec().getBaseSnapshot()).isEqualTo("base");
        assertThat(saved.getSpec().getReleaseSnapshot()).isEqualTo("release");
        assertThat(saved.getMetadata().getVersion()).isEqualTo(7);
        assertThat(JsonUtils.objectToJson(base)).isEqualTo(baseBefore);
        assertThat(JsonUtils.objectToJson(head)).isEqualTo(headBefore);
        verify(client, never()).update(any(Snapshot.class));
    }

    @Test
    void metadataOnlySaveDoesNotTouchSnapshots() {
        var post = post(7);
        when(client.update(post)).thenReturn(Mono.just(post));
        assertThat(service.updatePost(post, null, ACTOR).block()).isSameAs(post);
        verify(client, never()).fetch(eq(Snapshot.class), anyString());
        verify(client, never()).create(any());
    }

    @Test
    void failedCasRemovesOnlyItsNewUnlinkedSnapshotWithoutRetryingStaleVersion() {
        var current = post(7);
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base(current)));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head(current, base(current))));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(JsonUtils.deepCopy(current)));
        when(client.update(any(Post.class))).thenReturn(Mono.error(new OptimisticLockingFailureException("changed")));
        StepVerifier.create(service.updatePost(current, BODY, ACTOR))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        var created = ArgumentCaptor.forClass(Snapshot.class);
        verify(client).create(created.capture());
        verify(client).delete(created.getValue());
        verify(client, times(1)).update(any(Post.class));
        verify(client, never()).delete(any(Post.class));
    }

    @Test
    void ambiguousWriteFailureKeepsSnapshotIfLatestSubjectAlreadyReferencesIt() {
        var current = post(7);
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base(current)));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head(current, base(current))));
        var latest = new AtomicReference<Post>();
        when(client.update(any(Post.class))).thenAnswer(call -> {
            latest.set(JsonUtils.deepCopy(call.getArgument(0)));
            return Mono.error(new IllegalStateException("response lost after persistence"));
        });
        when(client.fetch(Post.class, "post")).thenAnswer(call -> Mono.just(latest.get()));
        StepVerifier.create(service.updatePost(current, BODY, ACTOR))
                .expectErrorMessage("response lost after persistence")
                .verify();
        verify(client, never()).delete(any());
    }

    @Test
    void initialSnapshotFailureCompensatesOnlyTheUnchangedIncompleteDraft() {
        var post = emptyPost();
        when(client.create(any(Post.class))).thenReturn(Mono.just(post));
        when(client.create(any(Snapshot.class))).thenReturn(Mono.error(new IllegalStateException("snapshot failed")));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        StepVerifier.create(service.createPost(post, BODY, ACTOR))
                .expectErrorMessage("snapshot failed")
                .verify();
        verify(client).delete(post);
        verify(client, never()).update(any());
    }

    @Test
    void initialReferenceCasFailureCompensatesUnlinkedSnapshotAndIncompleteDraft() {
        var post = emptyPost();
        when(client.create(any(Post.class))).thenReturn(Mono.just(post));
        when(client.update(any(Post.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("create CAS failed")));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(post));
        StepVerifier.create(service.createPost(post, BODY, ACTOR))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client).delete(any(Snapshot.class));
        verify(client).delete(post);
        verify(client, times(3)).update(any(Post.class));
    }

    @Test
    void failedCreateDoesNotRemoveDraftChangedByAnotherWriter() {
        var post = emptyPost();
        var changed = JsonUtils.deepCopy(post);
        changed.getMetadata().setVersion(1L);
        changed.getSpec().setTitle("concurrent edit");
        when(client.create(any(Post.class))).thenReturn(Mono.just(post));
        when(client.create(any(Snapshot.class))).thenReturn(Mono.error(new IllegalStateException("snapshot failed")));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(changed));
        StepVerifier.create(service.createPost(post, BODY, ACTOR))
                .expectErrorMessage("snapshot failed")
                .verify();
        verify(client, never()).delete(any(Post.class));
    }

    @Test
    void initialLinkRefetchesNativeReconcilerMetadataAndRetriesOnlyTheUnchangedNewDraft() {
        var post = emptyPost();
        var reconciled = JsonUtils.deepCopy(post);
        reconciled.getMetadata().setVersion(2L);
        reconciled.getMetadata().setLabels(Map.of("content.halo.run/published", "false"));
        when(client.create(any(Post.class))).thenReturn(Mono.just(post));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(reconciled));
        var attempts = new java.util.concurrent.atomic.AtomicInteger();
        when(client.update(any(Post.class))).thenAnswer(call -> {
            if (attempts.incrementAndGet() == 1) {
                return Mono.error(new OptimisticLockingFailureException("reconciler updated metadata"));
            }
            Post saved = JsonUtils.deepCopy(call.getArgument(0));
            saved.getMetadata().setVersion(3L);
            return Mono.just(saved);
        });
        var saved = service.createPost(post, BODY, ACTOR).block();
        assertThat(saved.getMetadata().getVersion()).isEqualTo(3);
        assertThat(saved.getMetadata().getLabels()).containsEntry("content.halo.run/published", "false");
        assertThat(saved.getSpec().getHeadSnapshot())
                .isEqualTo(saved.getSpec().getBaseSnapshot())
                .isNotBlank();
        verify(client, times(2)).fetch(Post.class, "post");
        verify(client, times(1)).create(any(Snapshot.class));
        verify(client, never()).delete(any());
    }

    @Test
    void initialLinkRejectsAConcurrentBusinessOrOwnershipChangeWithoutOverwritingOrDeletingIt() {
        var post = emptyPost();
        var changed = JsonUtils.deepCopy(post);
        changed.getMetadata().setVersion(1L);
        changed.getSpec().setOwner("another-author");
        changed.getSpec().setTitle("Concurrent title");
        when(client.create(any(Post.class))).thenReturn(Mono.just(post));
        when(client.fetch(Post.class, "post")).thenReturn(Mono.just(changed));
        StepVerifier.create(service.createPost(post, BODY, ACTOR))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any(Post.class));
        verify(client, never()).delete(any(Post.class));
        verify(client).delete(any(Snapshot.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "deleted", "patch-base", "missing-content"})
    void unusableBaseSnapshotIsRejectedBeforeAnyWrite(String failure) {
        var post = post(7);
        var base = base(post);
        switch (failure) {
            case "foreign" -> base.getSpec().getSubjectRef().setName("another-post");
            case "deleted" -> base.getMetadata().setDeletionTimestamp(Instant.now());
            case "patch-base" -> base.getMetadata().setAnnotations(Map.of());
            case "missing-content" -> base.getSpec().setRawPatch(null);
            default -> throw new IllegalArgumentException(failure);
        }
        when(client.fetch(Snapshot.class, "base")).thenReturn(Mono.just(base));
        when(client.fetch(Snapshot.class, "head")).thenReturn(Mono.just(head(post, base(post))));
        StepVerifier.create(service.updatePost(post, BODY, ACTOR))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void missingActorIsRejectedBeforeCreatingAnyResource() {
        StepVerifier.create(service.createPost(emptyPost(), BODY, " "))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private static Post emptyPost() {
        var post = post(0);
        post.getSpec().setBaseSnapshot(null);
        post.getSpec().setHeadSnapshot(null);
        post.getSpec().setReleaseSnapshot(null);
        return post;
    }

    private static Post post(long version) {
        var post = new Post();
        post.setMetadata(metadata("post", version));
        var spec = new Post.PostSpec();
        spec.setTitle("Original");
        spec.setOwner(ACTOR);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        post.setSpec(spec);
        return post;
    }

    private static SinglePage page(long version) {
        var page = new SinglePage();
        page.setMetadata(metadata("page", version));
        var spec = new SinglePage.SinglePageSpec();
        spec.setTitle("Original page");
        spec.setOwner(ACTOR);
        spec.setBaseSnapshot("base");
        spec.setHeadSnapshot("head");
        spec.setReleaseSnapshot("release");
        page.setSpec(spec);
        return page;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        metadata.setCreationTimestamp(Instant.EPOCH);
        return metadata;
    }

    private static Snapshot base(Extension owner) {
        var snapshot = new Snapshot();
        snapshot.setMetadata(metadata("base", 1));
        snapshot.getMetadata().setAnnotations(Map.of(Snapshot.KEEP_RAW_ANNO, "true"));
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(owner));
        spec.setRawType("MARKDOWN");
        spec.setRawPatch("# Original\n");
        spec.setContentPatch("<h1>Original</h1>\n");
        snapshot.setSpec(spec);
        return snapshot;
    }

    private static Snapshot head(Extension owner, Snapshot base) {
        var snapshot = new Snapshot();
        snapshot.setMetadata(metadata("head", 2));
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(owner));
        spec.setRawType("MARKDOWN");
        spec.setContributors(Set.of("previous-author"));
        spec.setRawPatch(PatchUtils.diffToJsonPatch(base.getSpec().getRawPatch(), "# Existing head\n"));
        spec.setContentPatch(PatchUtils.diffToJsonPatch(base.getSpec().getContentPatch(), "<h1>Existing head</h1>\n"));
        snapshot.setSpec(spec);
        return snapshot;
    }
}
