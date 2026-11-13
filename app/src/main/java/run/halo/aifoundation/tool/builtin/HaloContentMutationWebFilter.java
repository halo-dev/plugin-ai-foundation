package run.halo.aifoundation.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.SinglePage;
import run.halo.app.core.extension.content.Snapshot;
import run.halo.app.extension.Extension;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.security.AfterSecurityWebFilter;

/**
 * Coordinates native Halo HTTP content writes with shared-tool publication after Halo authorization.
 *
 * <p>For example, a Console {@code PUT /apis/api.console.halo.run/v1alpha1/posts/hello/content} holds the same lease as
 * publishing {@code hello} through a shared tool, starting before the native handler reads content. Core resource
 * writes, Console article/page mutations and User Center article mutations are covered. Snapshot resource writes
 * resolve and recheck their content owner before entering the handler.
 *
 * <p>Full native saves validate their body name, resource version and publication revision before Halo can write
 * content. Console editors can combine freshly fetched metadata with an older spec and annotations, so the version
 * alone does not identify a fresh edit. Core resource and Console editor bodies also validate snapshot references. A
 * resource save may still publish its current head, as the native page settings do. User Center handlers protect those
 * spec fields themselves, but their full saves must retain the publication revision. Valid bodies are replayed for the
 * native handler. Authentication and authorization remain Halo's responsibility.
 */
@Component
@RequiredArgsConstructor
public class HaloContentMutationWebFilter implements AfterSecurityWebFilter {
    private static final Set<String> GROUPS =
            Set.of("content.halo.run", "api.console.halo.run", "uc.api.content.halo.run");
    private static final Set<HttpMethod> MUTATIONS =
            Set.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.POST);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ReactiveExtensionClient client;
    private final HaloContentMutationCoordinator coordinator;
    private final Environment environment;

    /**
     * Invokes a native mutation under its content lease, passing unrelated requests through unchanged.
     *
     * @param exchange already authorized native request
     * @param chain remaining Halo filter and handler chain
     * @return completion after the entire mutation and lease cleanup
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            var target = target(exchange);
            if (target == null) {
                return chain.filter(exchange);
            }
            if (target.resource().equals("snapshots")) {
                return snapshotTarget(target.name())
                        .flatMap(owner -> coordinator.withLock(
                                owner.type(),
                                owner.name(),
                                () -> snapshotTarget(target.name())
                                        .filter(owner::equals)
                                        .switchIfEmpty(Mono.error(conflict()))
                                        .flatMap(current -> protectPublishedSnapshot(current, target.name()))
                                        .then(Mono.defer(() -> chain.filter(exchange)))));
            }
            var type = target.resource().equals("posts") ? Post.class : SinglePage.class;
            return coordinator.withLock(type, target.name(), () -> {
                if (target.saveBody() != SaveBody.NONE) {
                    return contentSave(exchange, chain, type, target);
                }
                return chain.filter(exchange);
            });
        });
    }

    private Mono<Void> contentSave(
            ServerWebExchange exchange, WebFilterChain chain, Class<? extends Extension> type, Target target) {
        var contentType = exchange.getRequest().getHeaders().getContentType();
        if (contentType == null
                || !(MediaType.APPLICATION_JSON.isCompatibleWith(contentType)
                        || contentType.getType().equalsIgnoreCase("application")
                                && contentType.getSubtype().endsWith("+json"))) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        }
        return DataBufferUtils.join(exchange.getRequest().getBody(), maxRequestBytes())
                .map(buffer -> {
                    try {
                        var bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> validateContentBody(type, target, bytes)
                        .then(Mono.defer(() -> chain.filter(replay(exchange, bytes)))))
                .onErrorMap(
                        DataBufferLimitException.class,
                        error -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    private Mono<Void> validateContentBody(Class<? extends Extension> type, Target target, byte[] bytes) {
        return Mono.defer(() -> {
            JsonNode root;
            try {
                root = JSON.readTree(bytes);
            } catch (IOException error) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid editor request"));
            }
            var body = resourceBody(root, type, target.saveBody());
            if (body == null
                    || !body.isObject()
                    || !target.name().equals(body.path("metadata").path("name").asText())) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Editor resource name mismatch"));
            }
            return client.fetch(type, target.name())
                    .switchIfEmpty(Mono.error(conflict()))
                    .flatMap(current -> {
                        var expected = body.path("metadata").path("version");
                        if (!expected.isIntegralNumber()
                                || !Objects.equals(current.getMetadata().getVersion(), expected.longValue())) {
                            return Mono.error(conflict());
                        }
                        var revision =
                                body.path("metadata").path("annotations").path(HaloPublicationRevision.ANNOTATION);
                        if (!Objects.equals(HaloPublicationRevision.current(current), text(revision))) {
                            return Mono.error(conflict());
                        }
                        if (target.saveBody() == SaveBody.USER_RESOURCE) {
                            return Mono.empty();
                        }
                        var currentSpec = contentState(current);
                        if (currentSpec == null) {
                            return Mono.error(conflict());
                        }
                        var requestedSpec = body.path("spec");
                        if (!Objects.equals(currentSpec.base(), text(requestedSpec.path("baseSnapshot")))) {
                            return Mono.error(conflict());
                        }
                        if (!Objects.equals(currentSpec.head(), text(requestedSpec.path("headSnapshot")))) {
                            return Mono.error(conflict());
                        }
                        if (!validRelease(currentSpec, requestedSpec, target.saveBody())) {
                            return Mono.error(conflict());
                        }
                        return Mono.empty();
                    });
        });
    }

    private static JsonNode resourceBody(JsonNode root, Class<? extends Extension> type, SaveBody saveBody) {
        if (root == null || saveBody != SaveBody.EDITOR) {
            return root;
        }
        return root.get(type == Post.class ? "post" : "page");
    }

    private static boolean validRelease(ContentState current, JsonNode requested, SaveBody saveBody) {
        var release = text(requested.path("releaseSnapshot"));
        if (Objects.equals(current.release(), release)) {
            return true;
        }
        if (saveBody != SaveBody.RESOURCE) {
            return false;
        }
        var publish = requested.path("publish");
        if (!publish.isBoolean() || !publish.booleanValue()) {
            return false;
        }
        if (current.head() == null || current.head().isBlank()) {
            return false;
        }
        return current.head().equals(release);
    }

    private Mono<Owner> snapshotTarget(String name) {
        return client.fetch(Snapshot.class, name)
                .mapNotNull(snapshot -> owner(
                        snapshot.getSpec() == null ? null : snapshot.getSpec().getSubjectRef()))
                .switchIfEmpty(Mono.error(conflict()));
    }

    private Mono<Void> protectPublishedSnapshot(Owner owner, String snapshotName) {
        return client.fetch(owner.type(), owner.name())
                .switchIfEmpty(Mono.error(conflict()))
                .flatMap(resource -> {
                    var spec = contentState(resource);
                    if (spec == null) {
                        return Mono.error(conflict());
                    }
                    if (!spec.published()) {
                        return Mono.empty();
                    }
                    var annotations = resource.getMetadata().getAnnotations();
                    var lastReleased = annotations == null ? null : annotations.get(Post.LAST_RELEASED_SNAPSHOT_ANNO);
                    if (snapshotName.equals(spec.base())
                            || snapshotName.equals(spec.release())
                            || snapshotName.equals(lastReleased)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Published content snapshots cannot be changed directly; edit a new draft instead"));
                    }
                    return Mono.empty();
                });
    }

    private static ContentState contentState(Extension resource) {
        if (resource instanceof Post post) {
            var spec = post.getSpec();
            if (spec == null) {
                return null;
            }
            return new ContentState(
                    spec.getBaseSnapshot(),
                    spec.getHeadSnapshot(),
                    spec.getReleaseSnapshot(),
                    Boolean.TRUE.equals(spec.getPublish()) || Post.isPublished(post.getMetadata()));
        }
        var page = (SinglePage) resource;
        var spec = page.getSpec();
        if (spec == null) {
            return null;
        }
        return new ContentState(
                spec.getBaseSnapshot(),
                spec.getHeadSnapshot(),
                spec.getReleaseSnapshot(),
                Boolean.TRUE.equals(spec.getPublish()) || Post.isPublished(page.getMetadata()));
    }

    private static Owner owner(Ref ref) {
        if (ref == null
                || ref.getKind() == null
                || !"content.halo.run".equals(ref.getGroup())
                || !BuiltInToolInputs.validResourceName(ref.getName())) {
            return null;
        }
        return switch (ref.getKind()) {
            case "Post" -> new Owner(Post.class, ref.getName());
            case "SinglePage" -> new Owner(SinglePage.class, ref.getName());
            default -> null;
        };
    }

    private int maxRequestBytes() {
        var configured = environment.getProperty(
                "spring.http.codecs.max-in-memory-size",
                environment.getProperty("spring.codec.max-in-memory-size", "10MB"));
        var bytes = DataSize.parse(configured).toBytes();
        return bytes < 0 ? -1 : (int) Math.min(bytes, Integer.MAX_VALUE);
    }

    private static ServerWebExchange replay(ServerWebExchange exchange, byte[] bytes) {
        var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(
                        () -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
            }
        };
        return exchange.mutate().request(request).build();
    }

    private static Target target(ServerWebExchange exchange) {
        if (!MUTATIONS.contains(exchange.getRequest().getMethod())) {
            return null;
        }
        var segments = exchange.getRequest().getPath().pathWithinApplication().elements().stream()
                .filter(PathContainer.PathSegment.class::isInstance)
                .map(PathContainer.PathSegment.class::cast)
                .map(PathContainer.PathSegment::valueToMatch)
                .toList();
        if (segments.size() < 5 || !segments.get(0).equals("apis")) {
            return null;
        }
        var group = segments.get(1);
        if (!GROUPS.contains(group) || !segments.get(2).equals("v1alpha1")) {
            return null;
        }
        var resource = segments.get(3);
        if (!Set.of("posts", "singlepages", "snapshots").contains(resource)) {
            return null;
        }
        if (resource.equals("snapshots") && !group.equals("content.halo.run")) {
            return null;
        }
        var name = segments.get(4);
        if (!BuiltInToolInputs.validResourceName(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid content resource name");
        }
        var saveBody = SaveBody.NONE;
        if (!resource.equals("snapshots")
                && segments.size() == 5
                && exchange.getRequest().getMethod() == HttpMethod.PUT) {
            saveBody = switch (group) {
                case "content.halo.run" -> SaveBody.RESOURCE;
                case "api.console.halo.run" -> SaveBody.EDITOR;
                case "uc.api.content.halo.run" -> resource.equals("posts") ? SaveBody.USER_RESOURCE : SaveBody.NONE;
                default -> SaveBody.NONE;
            };
        }
        return new Target(resource, name, saveBody);
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, "Content changed; reload the Halo editor before saving");
    }

    /** Identifies the lease owner independently of the requested snapshot name. */
    private record Owner(Class<? extends Extension> type, String name) {}

    /** Records the native resource path and the full-save body shape that requires preflight validation. */
    private record Target(String resource, String name, SaveBody saveBody) {}

    /** Distinguishes full resource bodies from Console wrappers and User Center saves protecting their own spec. */
    private enum SaveBody {
        NONE,
        RESOURCE,
        USER_RESOURCE,
        EDITOR
    }

    /** Reads only lifecycle fields, avoiding serialization of unrelated dates or user-defined metadata. */
    private record ContentState(String base, String head, String release, boolean published) {}
}
