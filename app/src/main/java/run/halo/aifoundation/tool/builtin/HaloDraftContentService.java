package run.halo.aifoundation.tool.builtin;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
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
 * Persists article and page drafts using Halo's base and head snapshot model.
 *
 * <p>Initial creation links a new base snapshot to the new subject. Later body edits create a new patch snapshot and
 * update the head reference with optimistic locking; released snapshots remain intact. Failed or cancelled operations
 * clean up only resources proven to be unlinked.
 *
 * <pre>{@code
 * content.updatePost(post, Map.of("rawType", "HTML", "raw", "<p>Hello</p>",
 *     "content", "<p>Hello</p>"), actorName);
 * }</pre>
 */
@Component
@RequiredArgsConstructor
public class HaloDraftContentService {
    private static final ContentAccess<Post> POSTS = new ContentAccess<>(
            Post.class,
            post -> new SnapshotReferences(
                    post.getSpec().getBaseSnapshot(),
                    post.getSpec().getHeadSnapshot(),
                    post.getSpec().getReleaseSnapshot()),
            (post, name) -> {
                post.getSpec().setBaseSnapshot(name);
                post.getSpec().setHeadSnapshot(name);
            },
            Post::getSpec);
    private static final ContentAccess<SinglePage> PAGES = new ContentAccess<>(
            SinglePage.class,
            page -> new SnapshotReferences(
                    page.getSpec().getBaseSnapshot(),
                    page.getSpec().getHeadSnapshot(),
                    page.getSpec().getReleaseSnapshot()),
            (page, name) -> {
                page.getSpec().setBaseSnapshot(name);
                page.getSpec().setHeadSnapshot(name);
            },
            SinglePage::getSpec);

    private final ReactiveExtensionClient client;

    Mono<Post> createPost(Post post, Map<String, Object> body, String actor) {
        return create(POSTS, post, body, actor);
    }

    Mono<SinglePage> createPage(SinglePage page, Map<String, Object> body, String actor) {
        return create(PAGES, page, body, actor);
    }

    Mono<Post> updatePost(Post post, Map<String, Object> body, String actor) {
        if (body == null) {
            return Mono.defer(() -> client.update(post));
        }
        return save(
                POSTS,
                post,
                post.getSpec().getBaseSnapshot(),
                post.getSpec().getHeadSnapshot(),
                body,
                actor,
                name -> post.getSpec().setHeadSnapshot(name));
    }

    Mono<SinglePage> updatePage(SinglePage page, Map<String, Object> body, String actor) {
        if (body == null) {
            return Mono.defer(() -> client.update(page));
        }
        return save(
                PAGES,
                page,
                page.getSpec().getBaseSnapshot(),
                page.getSpec().getHeadSnapshot(),
                body,
                actor,
                name -> page.getSpec().setHeadSnapshot(name));
    }

    private <T extends Extension> Mono<T> create(
            ContentAccess<T> access, T resource, Map<String, Object> body, String actor) {
        return Mono.defer(() -> {
            validateActor(actor);
            // Match Halo's lifecycle: persist the subject first, then its base content, then CAS
            // the snapshot references. A completed create therefore has a usable positive version.
            return Mono.usingWhen(
                    client.create(resource),
                    created -> {
                        return Mono.usingWhen(
                                client.create(snapshot(created, null, null, body, actor)),
                                saved -> linkInitialSnapshot(
                                        access, created, saved.getMetadata().getName()),
                                saved -> Mono.empty(),
                                (saved, error) -> cleanupUnlinked(access, created, saved),
                                saved -> cleanupUnlinked(access, created, saved));
                    },
                    created -> Mono.empty(),
                    (created, error) -> cleanupIncomplete(access, created),
                    created -> cleanupIncomplete(access, created));
        });
    }

    private <T extends Extension> Mono<T> linkInitialSnapshot(ContentAccess<T> access, T created, String name) {
        return Mono.defer(() -> client.fetch(
                                access.type(), created.getMetadata().getName())
                        .switchIfEmpty(Mono.error(new IllegalStateException("The new draft is unavailable")))
                        .flatMap(current -> {
                            if (!sameInitialDraft(access, created, current)) {
                                return Mono.error(new OptimisticLockingFailureException(
                                        "The new draft changed before its content could be saved"));
                            }
                            var update = JsonUtils.deepCopy(current);
                            access.initialize().accept(update, name);
                            return client.update(update);
                        }))
                // A new subject's normal reconciler can add metadata/status between create and link.
                // Refetch only this still-empty, unchanged draft; user supplied expectedVersion writes
                // never enter this path. Business changes remain a conflict on every attempt.
                .retryWhen(Retry.max(2)
                        .filter(OptimisticLockingFailureException.class::isInstance)
                        .onRetryExhaustedThrow((retry, signal) -> signal.failure()));
    }

    private <T extends Extension> Mono<T> save(
            ContentAccess<T> access,
            T resource,
            String baseName,
            String headName,
            Map<String, Object> body,
            String actor,
            Consumer<String> selectSnapshot) {
        return Mono.defer(() -> {
            validateActor(actor);
            if (baseName == null || baseName.isBlank() || headName == null || headName.isBlank()) {
                return Mono.error(new IllegalArgumentException("The draft has no readable content snapshot"));
            }
            var snapshot = fetchSnapshot(resource, baseName)
                    .filter(Snapshot::isBaseSnapshot)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("The draft base snapshot is unavailable")))
                    .flatMap(base -> (baseName.equals(headName) ? Mono.just(base) : fetchSnapshot(resource, headName))
                            .map(head -> snapshot(resource, base, head, body, actor)));
            return Mono.usingWhen(
                    snapshot.flatMap(client::create),
                    saved -> {
                        selectSnapshot.accept(saved.getMetadata().getName());
                        // The resource keeps the caller's exact metadata.version. Never retry stale writes.
                        return client.update(resource);
                    },
                    saved -> Mono.empty(),
                    (saved, error) -> cleanupUnlinked(access, resource, saved),
                    saved -> cleanupUnlinked(access, resource, saved));
        });
    }

    private static void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("A current authenticated content owner is required");
        }
    }

    private Mono<Snapshot> fetchSnapshot(Extension owner, String name) {
        return client.fetch(Snapshot.class, name)
                .filter(snapshot -> snapshot.getMetadata() != null
                        && snapshot.getMetadata().getDeletionTimestamp() == null
                        && snapshot.getSpec() != null
                        && snapshot.getSpec().getSubjectRef() != null
                        && Ref.equals(snapshot.getSpec().getSubjectRef(), owner))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("The draft content snapshot is unavailable")));
    }

    private static Snapshot snapshot(
            Extension owner, Snapshot base, Snapshot head, Map<String, Object> body, String actor) {
        var snapshot = new Snapshot();
        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        if (base == null) {
            metadata.setAnnotations(Map.of(Snapshot.KEEP_RAW_ANNO, "true"));
        }
        snapshot.setMetadata(metadata);
        var spec = new Snapshot.SnapShotSpec();
        spec.setSubjectRef(Ref.of(owner));
        spec.setRawType((String) body.get("rawType"));
        var raw = (String) body.get("raw");
        var rendered = (String) body.get("content");
        if (base == null) {
            spec.setRawPatch(raw);
            spec.setContentPatch(rendered);
        } else {
            if (base.getSpec().getRawPatch() == null || base.getSpec().getContentPatch() == null) {
                throw new IllegalArgumentException("The draft base content is unavailable");
            }
            spec.setRawPatch(PatchUtils.diffToJsonPatch(base.getSpec().getRawPatch(), raw));
            spec.setContentPatch(PatchUtils.diffToJsonPatch(base.getSpec().getContentPatch(), rendered));
            spec.setParentSnapshotName(head.getMetadata().getName());
        }
        spec.setLastModifyTime(Instant.now());
        spec.setOwner(actor);
        var contributors = head != null && head.getSpec().getContributors() != null
                ? new LinkedHashSet<>(head.getSpec().getContributors())
                : new LinkedHashSet<String>();
        contributors.add(actor);
        spec.setContributors(contributors);
        snapshot.setSpec(spec);
        return snapshot;
    }

    private <T extends Extension> Mono<Void> cleanupUnlinked(ContentAccess<T> access, T resource, Snapshot snapshot) {
        return Mono.defer(
                        () -> client.fetch(access.type(), resource.getMetadata().getName())
                                .map(current -> access.references()
                                        .apply(current)
                                        .contains(snapshot.getMetadata().getName()))
                                .defaultIfEmpty(false)
                                .flatMap(linked -> linked
                                        ? Mono.<Void>empty()
                                        : client.delete(snapshot).then()))
                // Preserve the original write failure, and never delete a snapshot whose link cannot be checked.
                .onErrorComplete();
    }

    private <T extends Extension> Mono<Void> cleanupIncomplete(ContentAccess<T> access, T created) {
        return Mono.defer(
                        () -> client.fetch(access.type(), created.getMetadata().getName())
                                // Native metadata/status reconciliation is harmless; a business edit or link is not.
                                .filter(current -> sameInitialDraft(access, created, current))
                                .flatMap(current -> client.delete(current).then()))
                .onErrorComplete();
    }

    private static <T extends Extension> boolean sameInitialDraft(ContentAccess<T> access, T created, T current) {
        if (current.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        if (!access.references().apply(current).empty()) {
            return false;
        }
        if (!Objects.equals(
                created.getMetadata().getCreationTimestamp(),
                current.getMetadata().getCreationTimestamp())) {
            return false;
        }
        return JsonUtils.objectToJson(access.spec().apply(created))
                .equals(JsonUtils.objectToJson(access.spec().apply(current)));
    }

    /**
     * Typed resource operations supplied once by each content business.
     *
     * <p>The snapshot lifecycle uses these callbacks without inspecting the runtime resource type.
     *
     * @param type concrete resource class for persistence
     * @param references current snapshot references
     * @param initialize selection of the initial base/head snapshot
     * @param spec business specification compared before linking or cleaning up an initial draft
     * @param <T> concrete content resource
     */
    private record ContentAccess<T extends Extension>(
            Class<T> type,
            Function<T, SnapshotReferences> references,
            BiConsumer<T, String> initialize,
            Function<T, Object> spec) {}

    /**
     * Snapshot identifiers used to verify links independently of a content resource's business type.
     *
     * @param base initial full-content snapshot
     * @param head latest editable snapshot
     * @param release requested release snapshot
     */
    private record SnapshotReferences(String base, String head, String release) {
        boolean empty() {
            return base == null && head == null && release == null;
        }

        boolean contains(String name) {
            return name.equals(base) || name.equals(head) || name.equals(release);
        }
    }
}
