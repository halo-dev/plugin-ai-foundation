package run.halo.aifoundation.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    public static final String SOURCE_URL = "https://models.dev/api.json";
    static final Duration REFRESH_INTERVAL = Duration.ofHours(24);
    static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path cacheFile;
    private final Mono<String> download;
    private final Clock clock;
    private final Sinks.Empty<Void> stopped = Sinks.empty();
    private volatile Snapshot snapshot;
    private volatile Instant lastAttemptAt;
    private volatile String error;
    private volatile Mono<ModelCatalogStatus> inFlight;
    private Disposable synchronization;

    @Autowired
    public ModelsDevCatalogService(PluginsRootGetter pluginsRootGetter) {
        this(pluginsRootGetter.get().resolve("ai-foundation/models-dev-cache.json"),
            WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                    .followRedirect(false).responseTimeout(Duration.ofSeconds(15))))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_BYTES))
                .build().get().uri(SOURCE_URL)
                .retrieve().bodyToMono(String.class), Clock.systemUTC());
    }

    ModelsDevCatalogService(Path cacheFile, Mono<String> download, Clock clock) {
        this.cacheFile = cacheFile;
        this.download = download;
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        synchronization = Mono.fromRunnable(this::loadCache)
            .subscribeOn(Schedulers.boundedElastic())
            .thenMany(Flux.interval(Duration.ZERO, REFRESH_INTERVAL))
            .concatMap(ignored -> refreshIfStale())
            .takeUntilOther(stopped.asMono())
            .subscribe(ignored -> { }, failure ->
                log.warn("models.dev background synchronization stopped", failure));
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
                return download.timeout(Duration.ofSeconds(15))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Empty catalog")));
            })
            .publishOn(Schedulers.boundedElastic())
            .map(this::accept)
            .onErrorResume(failure -> {
                // Do not expose upstream bodies, redirects or arbitrary exception messages.
                error = snapshot == null ? "目录同步失败，使用供应商信息和默认规则"
                    : "目录同步失败，已保留上次成功数据";
                log.warn("models.dev synchronization failed ({})", failure.getClass().getSimpleName());
                return Mono.just(status());
            })
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

    private ModelCatalogStatus accept(String body) {
        try {
            if (body.length() > MAX_BYTES) {
                throw new IllegalArgumentException("Catalog too large");
            }
            var root = MAPPER.readTree(body);
            var catalog = ModelsDevCatalog.parse(root);
            var fetchedAt = clock.instant();
            // Publish only a fully parsed snapshot. A bad response cannot replace good data.
            snapshot = new Snapshot(catalog, fetchedAt);
            error = null;
            try {
                persist(root, fetchedAt);
            } catch (IOException failure) {
                error = "目录已更新，但本地缓存保存失败";
                log.warn("Could not persist models.dev cache ({})",
                    failure.getClass().getSimpleName());
            }
            return status();
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid catalog JSON", failure);
        }
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
            var fetchedAt = Instant.parse(root.path("fetchedAt").asText());
            var catalog = ModelsDevCatalog.parse(root.get("catalog"));
            synchronized (this) {
                if (snapshot == null || snapshot.fetchedAt().isBefore(fetchedAt)) {
                    snapshot = new Snapshot(catalog, fetchedAt);
                }
            }
        } catch (IOException | RuntimeException failure) {
            error = "本地目录缓存不可用，将重新同步";
            log.warn("Could not read models.dev cache ({})", failure.getClass().getSimpleName());
        }
    }

    private void persist(JsonNode catalog, Instant fetchedAt) throws IOException {
        var parent = cacheFile.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        var temporary = Files.createTempFile(parent, "models-dev-", ".tmp");
        try {
            var envelope = MAPPER.createObjectNode();
            envelope.put("fetchedAt", fetchedAt.toString());
            envelope.set("catalog", catalog);
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

    record Snapshot(ModelsDevCatalog catalog, Instant fetchedAt) {
    }
}
