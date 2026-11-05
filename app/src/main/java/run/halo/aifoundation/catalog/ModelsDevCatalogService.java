package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import run.halo.app.plugin.PluginsRootGetter;

/** Background-only catalog synchronization; discovery and model calls never fetch this URL. */
@Slf4j
@Component
public class ModelsDevCatalogService {

    public static final String SOURCE_URL = "https://models.dev/experimental/v2.0/api.json";
    // Local cache envelope format, independent of the models.dev API schema.
    private static final int CACHE_FORMAT_VERSION = 1;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    static final Duration REFRESH_INTERVAL = Duration.ofHours(24);
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path cacheFile;
    private final Function<String, Mono<Download>> download;
    private final Clock clock;
    private final Supplier<Snapshot> bundledCatalog;
    private final Sinks.Empty<Void> stopped = Sinks.empty();
    private volatile Snapshot snapshot;
    private volatile boolean usingBundled;
    private volatile Instant lastAttemptAt;
    private volatile String error;
    private volatile Mono<ModelCatalogStatus> inFlight;
    private Disposable synchronization;

    @Autowired
    public ModelsDevCatalogService(PluginsRootGetter pluginsRootGetter) {
        this(pluginsRootGetter.get().resolve("ai-foundation/models-dev-cache.json"),
            downloader(WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                    .compress(true).followRedirect(false).responseTimeout(REQUEST_TIMEOUT)))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_BYTES))
                .build()), Clock.systemUTC(), ModelsDevBundledCatalog::load);
    }

    ModelsDevCatalogService(Path cacheFile, Mono<String> download, Clock clock) {
        this(cacheFile, ignored -> download.map(body -> new Download(body, null, false)), clock);
    }

    ModelsDevCatalogService(Path cacheFile, Function<String, Mono<Download>> download,
        Clock clock) {
        this(cacheFile, download, clock, () -> null);
    }

    ModelsDevCatalogService(Path cacheFile, Function<String, Mono<Download>> download, Clock clock,
        Supplier<Snapshot> bundledCatalog) {
        this.cacheFile = cacheFile;
        this.download = download;
        this.clock = clock;
        this.bundledCatalog = bundledCatalog;
    }

    static Function<String, Mono<Download>> downloader(WebClient client) {
        return etag -> {
            var request = client.get().uri(SOURCE_URL);
            if (etag != null && !etag.isBlank()) {
                request.ifNoneMatch(etag);
            }
            return request.exchangeToMono(response -> {
                var receivedEtag = response.headers().asHttpHeaders().getETag();
                if (response.statusCode() == HttpStatus.NOT_MODIFIED) {
                    return Mono.just(new Download(null, receivedEtag, true));
                }
                if (!response.statusCode().is2xxSuccessful()) {
                    return response.createException().flatMap(Mono::error);
                }
                return response.bodyToMono(String.class)
                    .map(body -> new Download(body, receivedEtag, false));
            });
        };
    }

    @PostConstruct
    void start() {
        synchronization = initialize()
            .thenMany(Flux.interval(REFRESH_INTERVAL))
            .concatMap(ignored -> refreshIfStale())
            .takeUntilOther(stopped.asMono())
            .subscribe(ignored -> { }, failure ->
                log.warn("models.dev background synchronization stopped", failure));
    }

    Mono<ModelCatalogStatus> initialize() {
        // A cache can serve discovery immediately, but never suppress the startup remote check.
        return Mono.fromRunnable(this::loadCache)
            .subscribeOn(Schedulers.boundedElastic())
            .then(Mono.defer(this::refresh));
    }

    Mono<ModelCatalogStatus> refreshIfStale() {
        return status().stale() ? refresh() : Mono.just(status());
    }

    public synchronized Mono<ModelCatalogStatus> refresh() {
        if (inFlight != null) {
            return inFlight;
        }
        inFlight = Mono.defer(() -> {
                lastAttemptAt = clock.instant();
                var current = snapshot;
                return download.apply(current == null ? null : current.etag())
                    .timeout(REQUEST_TIMEOUT)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty catalog")));
            })
            .publishOn(Schedulers.boundedElastic())
            .map(this::accept)
            .onErrorResume(this::recoverFromFailure)
            .takeUntilOther(stopped.asMono())
            .doOnTerminate(this::clearInFlight)
            .map(status -> new ModelCatalogStatus(status.sourceUrl(), status.fetchedAt(),
                status.lastAttemptAt(), status.available(), status.stale(), false, status.error()))
            .cache();
        return inFlight;
    }

    private synchronized void clearInFlight() {
        inFlight = null;
    }

    private Mono<ModelCatalogStatus> recoverFromFailure(Throwable failure) {
        loadBundledFallback();
        // Do not expose upstream bodies, redirects or arbitrary exception messages.
        if (usingBundled) {
            error = "目录同步失败，已使用内置目录";
        } else if (snapshot == null) {
            error = "目录同步失败，使用供应商信息和默认规则";
        } else {
            error = "目录同步失败，已保留上次成功数据";
        }
        log.warn("models.dev synchronization failed ({})", failure.getClass().getSimpleName());
        return Mono.just(status());
    }

    private ModelCatalogStatus accept(Download response) {
        final Snapshot next;
        try {
            next = parseDownload(response);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid catalog JSON", failure);
        }
        // Publish only a fully parsed snapshot. A bad response cannot replace good data.
        snapshot = next;
        usingBundled = false;
        error = null;
        try {
            persist(next);
        } catch (IOException failure) {
            error = "目录已更新，但本地缓存保存失败";
            log.warn("Could not persist models.dev cache ({})", failure.getClass().getSimpleName());
        }
        return status();
    }

    private Snapshot parseDownload(Download response) throws IOException {
        var current = snapshot;
        if (response.notModified()) {
            if (current == null || current.etag() == null) {
                throw new IllegalArgumentException("Catalog not modified without a cached ETag");
            }
            return new Snapshot(current.catalog(), clock.instant(),
                response.etag() == null ? current.etag() : response.etag());
        }
        var body = response.body();
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Empty or oversized catalog");
        }
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree(body))
            .retainProviders(ModelsDevProviderIdentity.catalogIds());
        return new Snapshot(catalog, clock.instant(), response.etag());
    }

    void loadCache() {
        if (!Files.exists(cacheFile)) {
            return;
        }
        try {
            if (Files.size(cacheFile) > MAX_BYTES + 1024) {
                throw new IOException("Catalog cache too large");
            }
            var root = MAPPER.readTree(Files.readString(cacheFile));
            var version = root.path("version");
            if (!version.isIntegralNumber() || !version.canConvertToInt()
                || version.intValue() != CACHE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported catalog cache version");
            }
            if (!SOURCE_URL.equals(root.path("sourceUrl").asText())) {
                throw new IllegalArgumentException("Invalid catalog cache source");
            }
            var fetchedAt = Instant.parse(root.path("fetchedAt").asText());
            var catalog = ModelsDevCatalog.parse(root.get("catalog"))
                .retainProviders(ModelsDevProviderIdentity.catalogIds());
            selectFallback(new Snapshot(catalog, fetchedAt, root.path("etag").asText(null)), false);
        } catch (IOException | RuntimeException failure) {
            error = "本地目录缓存不可用，将重新同步";
            log.warn("Could not read models.dev cache ({})", failure.getClass().getSimpleName());
        }
    }

    private void loadBundledFallback() {
        try {
            selectFallback(bundledCatalog.get(), true);
        } catch (RuntimeException failure) {
            log.warn("Could not read bundled models.dev catalog ({})",
                failure.getClass().getSimpleName());
        }
    }

    private synchronized void selectFallback(Snapshot candidate, boolean bundled) {
        if (candidate == null) {
            return;
        }
        var current = snapshot;
        if (current == null || candidate.fetchedAt().isAfter(current.fetchedAt())) {
            snapshot = candidate;
            usingBundled = bundled;
        }
    }

    private void persist(Snapshot current) throws IOException {
        var parent = cacheFile.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, "models-dev-", ".tmp");
        try {
            var envelope = MAPPER.createObjectNode();
            envelope.put("version", CACHE_FORMAT_VERSION);
            envelope.put("sourceUrl", SOURCE_URL);
            envelope.put("fetchedAt", current.fetchedAt().toString());
            if (current.etag() != null) {
                envelope.put("etag", current.etag());
            }
            envelope.set("catalog", current.catalog().toJson());
            Files.writeString(temporary, MAPPER.writeValueAsString(envelope));
            Files.move(temporary, cacheFile, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public ModelCatalogStatus status() {
        var current = snapshot;
        return new ModelCatalogStatus(SOURCE_URL, current == null ? null : current.fetchedAt(),
            lastAttemptAt, current != null,
            current == null || !current.fetchedAt().plus(REFRESH_INTERVAL).isAfter(clock.instant()),
            inFlight != null, error);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    @PreDestroy
    void stop() {
        stopped.tryEmitEmpty();
        if (synchronization != null) {
            synchronization.dispose();
        }
    }

    record Snapshot(ModelsDevCatalog catalog, Instant fetchedAt, String etag) {
    }

    record Download(String body, String etag, boolean notModified) {
    }
}