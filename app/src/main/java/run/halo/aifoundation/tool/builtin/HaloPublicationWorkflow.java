package run.halo.aifoundation.tool.builtin;

import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.object;
import static run.halo.aifoundation.tool.builtin.HaloToolSchemas.string;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.content.ContentWrapper;
import run.halo.app.content.PatchUtils;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.Extension;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.infra.utils.JsonUtils;

/**
 * Executes content review, approval and reconciliation independently of a resource domain.
 *
 * <p>Each provider supplies a typed loader and saver. The workflow compares snapshots, validates the approved full-body
 * fingerprint, serializes native mutations and polls confirmed publication state; it never switches on post/page flags
 * or casts resources. For example, the article provider supplies a loader that rechecks article ownership on every
 * poll.
 *
 * @param <T> concrete extension type owned by the calling provider
 */
final class HaloPublicationWorkflow<T extends Extension> {
    private static final Duration POLL_DELAY = Duration.ofMillis(200);
    private static final int MAX_POLLS = 10;
    private static final Set<String> PUBLISH_FIELDS =
            Set.of("name", "expectedVersion", "headSnapshot", "expectedContentFingerprint");
    private final ReactiveExtensionClient client;
    private final Class<T> resourceType;
    private final HaloContentMutationCoordinator coordinator;
    private final HaloContentToolSettings settings;
    private final String outputToolId;
    private final Function<String, Mono<State<T>>> loader;
    private final Saver<T> saver;

    HaloPublicationWorkflow(
            ReactiveExtensionClient client,
            Class<T> resourceType,
            HaloContentMutationCoordinator coordinator,
            HaloContentToolSettings settings,
            String outputToolId,
            Function<String, Mono<State<T>>> loader,
            Saver<T> saver) {
        this.client = client;
        this.resourceType = resourceType;
        this.coordinator = coordinator;
        this.settings = settings;
        this.outputToolId = outputToolId;
        this.loader = loader;
        this.saver = saver;
    }

    private Mono<State<T>> load(String name) {
        return Mono.defer(() -> loader.apply(name)).switchIfEmpty(unavailable());
    }

    /**
     * Compares a stable resource version with its complete reconstructed content.
     *
     * <p>For example, {@code {"name":"post-example"}} produces the metadata version, approved snapshot and full-body
     * fingerprint needed by publication. Loading again after reconstruction detects concurrent resource changes.
     *
     * @param input strict resource-name input
     * @return bounded content differences and complete-content approval identifiers
     */
    Mono<Object> compare(Map<String, Object> input) {
        return Mono.defer(() -> {
            fields(input, Set.of("name"));
            var name = name(input, "name");
            return load(name)
                    .flatMap(resource -> settings.maxContentCharacters(outputToolId)
                            .flatMap(limit -> versions(resource)
                                    .flatMap(versions -> load(name).map(fresh -> {
                                        unchanged(resource, fresh);
                                        return (Object) comparisonResult(resource, versions, limit);
                                    }))));
        });
    }

    private static Map<String, Object> comparisonResult(State<?> resource, Versions versions, int limit) {
        var result = summary(resource);
        result.put("hasReleasedVersion", versions.release() != null);
        result.put("head", content(versions.head(), limit));
        result.put("release", versions.release() == null ? null : content(versions.release(), limit));
        result.put("headContentFingerprint", fingerprint(versions.head()));
        var rawDiff = difference(
                versions.release() == null ? "" : versions.release().getRaw(),
                versions.head().getRaw(),
                limit);
        var contentDiff = difference(
                versions.release() == null ? "" : versions.release().getContent(),
                versions.head().getContent(),
                limit);
        result.put("rawDiff", rawDiff);
        result.put("contentDiff", contentDiff);
        var typeChanged = versions.release() == null
                || !Objects.equals(
                        versions.head().getRawType(), versions.release().getRawType());
        result.put("rawTypeChanged", typeChanged);
        result.put(
                "changed",
                typeChanged
                        || Boolean.TRUE.equals(rawDiff.get("changed"))
                        || Boolean.TRUE.equals(contentDiff.get("changed")));
        result.put(
                "truncated",
                truncated(versions.head(), limit)
                        || versions.release() != null && truncated(versions.release(), limit)
                        || Boolean.TRUE.equals(rawDiff.get("truncated"))
                        || Boolean.TRUE.equals(contentDiff.get("truncated")));
        return result;
    }

    /**
     * Applies a reviewed publication request and observes Halo's reconciliation result.
     *
     * <p>Publishing requires {@code expectedVersion}, {@code headSnapshot} and {@code expectedContentFingerprint} from
     * the reviewed comparison. Unpublishing requires only the resource name and current metadata version. A stale
     * version or fingerprint fails before a write, and a pending reconciliation is reported explicitly.
     *
     * @param input operation inputs validated against the strict publication field set
     * @param publish whether this operation publishes or withdraws content
     * @return requested and confirmed publication state
     */
    Mono<Object> publication(Map<String, Object> input, boolean publish) {
        return Mono.defer(() -> {
            fields(input, publish ? PUBLISH_FIELDS : Set.of("name", "expectedVersion"));
            var name = name(input, "name");
            var expectedVersion = version(input);
            var approvedHead = publish ? name(input, "headSnapshot") : null;
            var approvedFingerprint =
                    publish ? BuiltInToolInputs.requiredString(input, "expectedContentFingerprint", 64) : null;
            if (publish && !approvedFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("expectedContentFingerprint must come from compare versions");
            }
            return coordinator.withLock(
                    resourceType,
                    name,
                    () -> load(name).flatMap(resource -> {
                        if (!Objects.equals(
                                expectedVersion, resource.source().getMetadata().getVersion())) {
                            return Mono.error(conflict());
                        }
                        if (publish && !Objects.equals(approvedHead, resource.head())) {
                            return Mono.error(conflict());
                        }
                        if (!publish) {
                            var changed = resource.desiredPublished();
                            var saved = changed ? saver.save(resource, false, null) : Mono.just(resource);
                            return saved.flatMap(value -> publicationResult(value, false, null, null, changed));
                        }
                        return reviewedContent(resource).flatMap(reviewed -> {
                            if (!Objects.equals(approvedFingerprint, fingerprint(reviewed.content()))) {
                                return Mono.error(conflict());
                            }
                            var changed = !resource.desiredPublished()
                                    || !Objects.equals(resource.requestedRelease(), approvedHead);
                            if (!changed) {
                                return publicationResult(resource, true, approvedHead, approvedFingerprint, false);
                            }
                            return publishReviewedSnapshot(resource, reviewed, approvedFingerprint);
                        });
                    }));
        });
    }

    /**
     * Publishes an independent copy of the reviewed body while keeping native history and its base intact.
     *
     * <p>The shared mutation coordinator covers the entire load, reconstruction, clone and resource write. Native
     * Console and User Center saves use the same boundary, including saves that omit a snapshot version. Once the
     * resource selects the clone as both head and release, later native saves create a different draft snapshot.
     *
     * @param resource version-checked business resource
     * @param reviewed captured base and complete approved body
     * @param approvedFingerprint fingerprint already checked against the complete body
     * @return confirmed publication result for the independent snapshot
     */
    private Mono<Object> publishReviewedSnapshot(
            State<T> resource, ReviewedContent reviewed, String approvedFingerprint) {
        var linked = new AtomicBoolean();
        var proposed = publicationSnapshot(resource, reviewed);
        return Mono.usingWhen(
                Mono.just(proposed),
                snapshot -> Mono.defer(() -> client.create(snapshot))
                        .switchIfEmpty(
                                Mono.error(new IllegalStateException("The reviewed snapshot could not be saved")))
                        .flatMap(created -> unchangedBase(resource, reviewed.base())
                                .then(Mono.defer(() -> saver.save(
                                        resource, true, created.getMetadata().getName())))
                                .doOnNext(saved -> linked.set(true))
                                .flatMap(saved -> publicationResult(
                                        saved, true, created.getMetadata().getName(), approvedFingerprint, true))),
                snapshot -> Mono.empty(),
                (snapshot, error) ->
                        cleanupUnlinked(resource, snapshot.getMetadata().getName(), linked.get()),
                snapshot -> cleanupUnlinked(resource, snapshot.getMetadata().getName(), linked.get()));
    }

    private Mono<Object> publicationResult(
            State<T> resource, boolean publish, String head, String approvedFingerprint, boolean changed) {
        return confirm(resource, publish, head, 0)
                .flatMap(confirmed -> verifyReleasedContent(confirmed, publish, head, approvedFingerprint))
                .map(confirmed -> {
                    var result = summary(confirmed);
                    result.put("operation", publish ? "publish" : "unpublish");
                    result.put("changed", changed);
                    result.put("reconciliationPending", !confirmed(confirmed, publish, head));
                    return (Object) result;
                });
    }

    private Mono<ReviewedContent> reviewedContent(State<T> resource) {
        if (resource.base() == null || resource.base().isBlank()) {
            return unavailable();
        }
        if (resource.head() == null || resource.head().isBlank()) {
            return unavailable();
        }
        return client.fetch(Snapshot.class, resource.base())
                .filter(base -> validSnapshot(base, resource.source()) && Snapshot.isBaseSnapshot(base))
                .switchIfEmpty(unavailable())
                .map(JsonUtils::deepCopy)
                .flatMap(base -> {
                    var head = Objects.equals(resource.head(), resource.base())
                            ? Mono.just(base)
                            : client.fetch(Snapshot.class, resource.head())
                                    .filter(snapshot -> validSnapshot(snapshot, resource.source()))
                                    .switchIfEmpty(unavailable())
                                    .map(JsonUtils::deepCopy);
                    return head.map(snapshot ->
                            new ReviewedContent(base, snapshot, ContentWrapper.patchSnapshot(snapshot, base)));
                });
    }

    private static Snapshot publicationSnapshot(State<?> resource, ReviewedContent reviewed) {
        var snapshot = JsonUtils.deepCopy(reviewed.head());
        var metadata = new Metadata();
        metadata.setName("ai-release-" + UUID.randomUUID());
        snapshot.setMetadata(metadata);
        var spec = snapshot.getSpec();
        spec.setSubjectRef(Ref.of(resource.source()));
        spec.setParentSnapshotName(reviewed.head().getMetadata().getName());
        spec.setRawPatch(PatchUtils.diffToJsonPatch(
                reviewed.base().getSpec().getRawPatch(), reviewed.content().getRaw()));
        spec.setContentPatch(PatchUtils.diffToJsonPatch(
                reviewed.base().getSpec().getContentPatch(), reviewed.content().getContent()));
        return snapshot;
    }

    private Mono<Void> unchangedBase(State<T> resource, Snapshot captured) {
        return client.fetch(Snapshot.class, resource.base())
                .filter(base -> validSnapshot(base, resource.source()) && Snapshot.isBaseSnapshot(base))
                .switchIfEmpty(unavailable())
                .doOnNext(current -> {
                    if (!Objects.equals(
                            captured.getMetadata().getVersion(),
                            current.getMetadata().getVersion())) {
                        throw conflict();
                    }
                    if (!Objects.equals(captured.getSpec(), current.getSpec())) {
                        throw conflict();
                    }
                })
                .then();
    }

    private Mono<Void> cleanupUnlinked(State<T> resource, String name, boolean linked) {
        if (linked) {
            return Mono.empty();
        }
        return load(resource.source().getMetadata().getName())
                .filter(current -> !Objects.equals(name, current.base())
                        && !Objects.equals(name, current.head())
                        && !Objects.equals(name, current.requestedRelease())
                        && !Objects.equals(name, current.released()))
                .flatMap(current -> client.fetch(Snapshot.class, name)
                        .filter(snapshot -> validSnapshot(snapshot, resource.source()))
                        .flatMap(snapshot -> client.delete(snapshot).then()))
                .onErrorComplete();
    }

    /**
     * Complete content and snapshot identities captured while native mutations are serialized.
     *
     * @param base independent copy of the full-content base, including its observed version
     * @param head independent copy of the reviewed draft snapshot and authorship metadata
     * @param content reconstructed raw and rendered bodies before truncation
     */
    private record ReviewedContent(Snapshot base, Snapshot head, ContentWrapper content) {}

    private Mono<State<T>> confirm(State<T> resource, boolean publish, String head, int attempt) {
        if (resource.desiredPublished() != publish || publish && !Objects.equals(resource.requestedRelease(), head)) {
            return Mono.error(conflict());
        }
        if (confirmed(resource, publish, head) || attempt >= MAX_POLLS) {
            return Mono.just(resource);
        }
        return Mono.delay(POLL_DELAY)
                .then(load(resource.source().getMetadata().getName()))
                .flatMap(fresh -> confirm(fresh, publish, head, attempt + 1));
    }

    private static boolean confirmed(State<?> resource, boolean publish, String head) {
        return resource.published() == publish && (!publish || Objects.equals(resource.released(), head));
    }

    private Mono<State<T>> verifyReleasedContent(
            State<T> resource, boolean publish, String head, String approvedFingerprint) {
        if (!publish || !confirmed(resource, true, head)) {
            return Mono.just(resource);
        }
        // Keep a final integrity check for trusted background writers outside the native HTTP
        // mutation boundary; normal editor writes are serialized before the reviewed clone is made.
        return snapshotContent(resource, resource.released())
                .onErrorMap(IllegalArgumentException.class, ignored -> conflict())
                .doOnNext(content -> {
                    if (!Objects.equals(approvedFingerprint, fingerprint(content))) {
                        throw conflict();
                    }
                })
                .thenReturn(resource);
    }

    private Mono<Versions> versions(State<T> resource) {
        return snapshotContent(resource, resource.head()).flatMap(head -> {
            if (resource.released() == null || resource.released().isBlank()) {
                if (resource.published()) {
                    return Mono.error(new IllegalStateException("Halo has no confirmed released snapshot; read again"));
                }
                return Mono.just(new Versions(head, null));
            }
            if (Objects.equals(resource.released(), resource.head())) {
                return Mono.just(new Versions(head, head));
            }
            return snapshotContent(resource, resource.released()).map(release -> new Versions(head, release));
        });
    }

    private Mono<ContentWrapper> snapshotContent(State<T> resource, String snapshotName) {
        if (snapshotName == null
                || snapshotName.isBlank()
                || resource.base() == null
                || resource.base().isBlank()) {
            return unavailable();
        }
        return client.fetch(Snapshot.class, resource.base())
                .filter(base -> validSnapshot(base, resource.source()) && Snapshot.isBaseSnapshot(base))
                .switchIfEmpty(unavailable())
                .flatMap(base -> {
                    if (Objects.equals(snapshotName, resource.base())) {
                        return Mono.fromSupplier(() -> ContentWrapper.patchSnapshot(base, base));
                    }
                    return client.fetch(Snapshot.class, snapshotName)
                            .filter(snapshot -> validSnapshot(snapshot, resource.source()))
                            .switchIfEmpty(unavailable())
                            .map(snapshot -> ContentWrapper.patchSnapshot(snapshot, base));
                });
    }

    private static boolean validSnapshot(Snapshot snapshot, Extension owner) {
        if (snapshot.getMetadata() == null || snapshot.getSpec() == null) {
            return false;
        }
        if (snapshot.getMetadata().getDeletionTimestamp() != null) {
            return false;
        }
        var subject = snapshot.getSpec().getSubjectRef();
        return subject != null && Ref.equals(subject, owner);
    }

    private static void unchanged(State<?> before, State<?> after) {
        if (!Objects.equals(
                before.source().getMetadata().getVersion(),
                after.source().getMetadata().getVersion())) {
            throw conflict();
        }
        if (!Objects.equals(before.base(), after.base()) || !Objects.equals(before.head(), after.head())) {
            throw conflict();
        }
        if (!Objects.equals(before.released(), after.released()) || before.published() != after.published()) {
            throw conflict();
        }
    }

    private static Map<String, Object> summary(State<?> resource) {
        var result = new LinkedHashMap<String, Object>();
        result.put("name", resource.source().getMetadata().getName());
        result.put("type", resource.type());
        result.put("metadataVersion", resource.source().getMetadata().getVersion());
        result.put("title", Objects.requireNonNullElse(resource.title(), ""));
        result.put("slug", Objects.requireNonNullElse(resource.slug(), ""));
        result.put("headSnapshot", Objects.requireNonNullElse(resource.head(), ""));
        result.put("releaseSnapshot", Objects.requireNonNullElse(resource.released(), ""));
        result.put("requestedReleaseSnapshot", Objects.requireNonNullElse(resource.requestedRelease(), ""));
        result.put("published", resource.published());
        result.put("desiredPublished", resource.desiredPublished());
        return result;
    }

    private static Map<String, Object> content(ContentWrapper content, int limit) {
        return Map.of(
                "snapshotName",
                content.getSnapshotName(),
                "rawType",
                Objects.requireNonNullElse(content.getRawType(), ""),
                "raw",
                truncate(content.getRaw(), limit),
                "content",
                truncate(content.getContent(), limit),
                "rawTruncated",
                length(content.getRaw()) > limit,
                "contentTruncated",
                length(content.getContent()) > limit,
                "contentFingerprint",
                fingerprint(content));
    }

    private static Map<String, Object> difference(String released, String head, int limit) {
        var before = Objects.requireNonNullElse(released, "");
        var after = Objects.requireNonNullElse(head, "");
        var prefix = 0;
        while (prefix < before.length() && prefix < after.length() && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        prefix = boundary(before, prefix);
        prefix = boundary(after, prefix);
        var suffix = 0;
        while (suffix < before.length() - prefix
                && suffix < after.length() - prefix
                && before.charAt(before.length() - suffix - 1) == after.charAt(after.length() - suffix - 1)) {
            suffix++;
        }
        // Avoid exposing half a surrogate pair at either edge of the changed span.
        if (boundary(before, before.length() - suffix) < before.length() - suffix
                || boundary(after, after.length() - suffix) < after.length() - suffix) {
            suffix--;
        }
        var removed = before.substring(prefix, before.length() - suffix);
        var added = after.substring(prefix, after.length() - suffix);
        return Map.of(
                "changed",
                !before.equals(after),
                "commonPrefixCharacters",
                prefix,
                "removedCharacters",
                removed.length(),
                "addedCharacters",
                added.length(),
                "removed",
                truncate(removed, limit),
                "added",
                truncate(added, limit),
                "truncated",
                removed.length() > limit || added.length() > limit);
    }

    static String fingerprint(ContentWrapper content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var value : new String[] {content.getRawType(), content.getRaw(), content.getContent()}) {
                var bytes = Objects.requireNonNullElse(value, "").getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static int boundary(String value, int end) {
        return end > 0
                        && end < value.length()
                        && Character.isHighSurrogate(value.charAt(end - 1))
                        && Character.isLowSurrogate(value.charAt(end))
                ? end - 1
                : end;
    }

    private static String truncate(String value, int limit) {
        return value == null ? "" : value.substring(0, boundary(value, Math.min(value.length(), limit)));
    }

    private static boolean truncated(ContentWrapper content, int limit) {
        return length(content.getRaw()) > limit || length(content.getContent()) > limit;
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static void fields(Map<String, Object> input, Set<String> allowed) {
        if (input == null || !allowed.containsAll(input.keySet())) {
            throw new IllegalArgumentException("Unsupported publication input field");
        }
    }

    private static String name(Map<String, Object> input, String key) {
        var name = BuiltInToolInputs.requiredString(input, key, 253);
        if (!BuiltInToolInputs.validResourceName(name)) {
            throw new IllegalArgumentException(key + " must be a Halo metadata.name");
        }
        return name;
    }

    private static long version(Map<String, Object> input) {
        if (input.get("expectedVersion") instanceof Number value) {
            try {
                var version = new BigDecimal(value.toString()).longValueExact();
                if (version > 0) {
                    return version;
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Reject fractions, overflow and non-finite values without coercion.
            }
        }
        throw new IllegalArgumentException("expectedVersion must be the current positive metadataVersion");
    }

    static OptimisticLockingFailureException conflict() {
        return new OptimisticLockingFailureException(
                "Content changed; compare versions and approve the current draft again");
    }

    static <T> Mono<T> unavailable() {
        return Mono.error(new IllegalArgumentException("Content snapshot does not exist or is unavailable"));
    }

    static String nonblank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String released(Extension extension) {
        var annotations = extension.getMetadata().getAnnotations();
        return annotations == null ? null : annotations.get(Post.LAST_RELEASED_SNAPSHOT_ANNO);
    }

    static void check(Extension extension, Boolean deleted) {
        var metadata = extension.getMetadata();
        if (metadata == null || metadata.getDeletionTimestamp() != null || Boolean.TRUE.equals(deleted)) {
            throw new IllegalArgumentException("Content does not exist or is recycled");
        }
        var labels = metadata.getLabels();
        if (labels != null && "true".equals(labels.get(Post.DELETED_LABEL))) {
            throw new IllegalArgumentException("Content does not exist or is recycled");
        }
    }

    static String comparisonDescription(String type) {
        return "Read-only comparison of a Halo " + type + "'s latest draft and confirmed last-released body; "
                + "does not save, publish or list historical revisions. "
                + "Returns actual reconstructed raw/rendered content, bounded changed spans, truncation flags, "
                + "metadataVersion, headSnapshot and headContentFingerprint computed from the full untruncated "
                + "content. Uses the get_" + type + " output limit. Review these values before requesting publication; "
                + "truncated text must not be treated as a complete review.";
    }

    static String publishDescription(String type) {
        return "Publish only the reviewed Halo " + type + " draft. First call compare_" + type + "_versions; "
                + "pass its metadataVersion as expectedVersion, headSnapshot, and headContentFingerprint as "
                + "expectedContentFingerprint. Any resource/head/content change requires a fresh comparison and "
                + "approval. Does not retry stale writes or publish a newer draft automatically. Returns actual "
                + "published/desiredPublished states and reconciliationPending; pending does not mean published.";
    }

    static String unpublishDescription(String type) {
        return "Unpublish a Halo " + type + " using its current metadataVersion as expectedVersion. "
                + "Preserves draft and released snapshots. Already-unpublished resources are returned without "
                + "a redundant write. Returns actual published state and reconciliationPending. Read again on conflict.";
    }

    static Map<String, Object> publicationSchema(boolean publish) {
        var properties = new LinkedHashMap<String, Object>();
        properties.put(
                "name",
                string(253, "Post name for article tools or SinglePage name for page tools; never title, slug or URL"));
        properties.put(
                "expectedVersion",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "description",
                        "Current metadataVersion from a fresh version comparison"));
        if (publish) {
            properties.put("headSnapshot", string(253, "Reviewed headSnapshot from compare versions"));
            properties.put(
                    "expectedContentFingerprint",
                    Map.of(
                            "type",
                            "string",
                            "pattern",
                            "^[0-9a-f]{64}$",
                            "description",
                            "Copy headContentFingerprint from the reviewed compare_post_versions/compare_page_versions result; never invent it"));
        }
        return object(
                properties,
                publish
                        ? List.of("name", "expectedVersion", "headSnapshot", "expectedContentFingerprint")
                        : List.of("name", "expectedVersion"));
    }

    /**
     * Reconstructed content versions used for comparison and draft fingerprinting.
     *
     * <p>The release is the confirmed last-released snapshot, rather than merely the snapshot requested in the resource
     * spec. A never-published draft has no release.
     *
     * @param head complete latest draft content before output truncation
     * @param release complete confirmed released content, or {@code null} if none exists
     */
    private record Versions(ContentWrapper head, ContentWrapper release) {}

    /**
     * Immutable publication view of one typed business resource.
     *
     * <p>The owning provider creates this view from its spec; the workflow retains the exact source type throughout
     * loading and persistence. Requested release and confirmed release remain separate during reconciliation.
     *
     * @param source typed resource with metadata version
     * @param type stable result type supplied by its business owner
     * @param title displayed title
     * @param slug URL slug
     * @param base base snapshot
     * @param head latest draft snapshot
     * @param requestedRelease desired released snapshot
     * @param released confirmed last-released snapshot
     * @param desiredPublished requested publication flag
     * @param published confirmed publication flag
     * @param <T> concrete resource type owned by the provider
     */
    record State<T extends Extension>(
            T source,
            String type,
            String title,
            String slug,
            String base,
            String head,
            String requestedRelease,
            String released,
            boolean desiredPublished,
            boolean published) {}

    /**
     * Persists a requested publication state through a domain-specific typed writer.
     *
     * @param <T> concrete resource type
     */
    @FunctionalInterface
    interface Saver<T extends Extension> {
        /**
         * Saves an approved state without changing its business resource type.
         *
         * @param current checked source state
         * @param publish desired publication flag
         * @param head approved draft snapshot when publishing
         * @return freshly saved state
         */
        Mono<State<T>> save(State<T> current, boolean publish, String head);
    }
}
