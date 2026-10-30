package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class ModelsDevCatalogServiceTest {

    static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String CATALOG = """
        {"openai":{"id":"openai","models":{"exact-model":{"id":"exact-model",
          "tool_call":true,"structured_output":false,"reasoning":false,
          "modalities":{"input":["text","image","pdf"],"output":["text"]}}}}}
        """;

    @TempDir
    Path directory;

    @Test
    void persistsSuccessfulSnapshotAndRestoresItWithoutNetwork() {
        var file = directory.resolve("catalog.json");
        var service = new ModelsDevCatalogService(file, Mono.just(CATALOG), CLOCK);
        var status = service.refresh().block();
        assertThat(status.available()).isTrue();
        assertThat(status.refreshing()).isFalse();
        assertThat(status.fetchedAt()).isEqualTo(NOW);
        assertThat(status.error()).isNull();

        var downloads = new AtomicInteger();
        var restored = new ModelsDevCatalogService(file, Mono.defer(() -> {
            downloads.incrementAndGet();
            return Mono.error(new IllegalStateException("offline"));
        }), CLOCK);
        restored.loadCache();
        restored.refreshIfStale().block();
        assertThat(downloads).hasValue(0);
        assertThat(restored.snapshot().catalog().find("openai", "exact-model").tools()).isTrue();
        assertThat(restored.status().fetchedAt()).isEqualTo(NOW);
    }

    @Test
    void networkFailureAndMalformedResponsesRetainLastSuccessAndDiskCache() throws Exception {
        var body = new AtomicReference<>(CATALOG);
        var file = directory.resolve("catalog.json");
        var service = new ModelsDevCatalogService(file,
            Mono.defer(() -> body.get() == null ? Mono.error(new IllegalStateException("offline"))
                : Mono.just(body.get())), CLOCK);
        service.refresh().block();
        var persisted = Files.readString(file);
        for (var invalid : java.util.List.of("[]", "{}", "not json",
            CATALOG.replace("\"tool_call\":true", "\"tool_call\":\"yes\""))) {
            body.set(invalid);
            assertThat(service.refresh().block().error()).isNotBlank();
            assertThat(service.status().available()).isTrue();
            assertThat(Files.readString(file)).isEqualTo(persisted);
        }
        body.set(null);
        assertThat(service.refresh().block().error()).isNotBlank();
        assertThat(service.snapshot().catalog().find("openai", "exact-model")).isNotNull();
    }

    @Test
    void noCacheOrCorruptCacheStillAllowsFallback() throws Exception {
        var file = directory.resolve("catalog.json");
        Files.writeString(file, "broken");
        var service = new ModelsDevCatalogService(file, Mono.error(new IllegalStateException()), CLOCK);
        service.loadCache();
        assertThat(service.refresh().block().available()).isFalse();
        assertThat(service.snapshot()).isNull();
    }

    @Test
    void coalescesConcurrentRefreshesAndAllowsRetryAfterCompletion() {
        var response = Sinks.<String>one();
        var count = new AtomicInteger();
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            Mono.defer(() -> {
                count.incrementAndGet();
                return response.asMono();
            }), CLOCK);
        var first = service.refresh().toFuture();
        var second = service.refresh().toFuture();
        assertThat(service.status().refreshing()).isTrue();
        response.tryEmitValue(CATALOG);
        assertThat(first.join().available()).isTrue();
        assertThat(second.join().available()).isTrue();
        assertThat(count).hasValue(1);
        service.refresh().block();
        assertThat(count).hasValue(2);
    }

    @Test
    void staleCacheSurvivesOfflineRefresh() {
        var file = directory.resolve("catalog.json");
        new ModelsDevCatalogService(file, Mono.just(CATALOG), CLOCK).refresh().block();
        var service = new ModelsDevCatalogService(file, Mono.error(new IllegalStateException()),
            Clock.offset(CLOCK, Duration.ofHours(25)));
        service.loadCache();
        var status = service.refreshIfStale().block();
        assertThat(status.stale()).isTrue();
        assertThat(status.fetchedAt()).isEqualTo(NOW);
        assertThat(status.available()).isTrue();
    }

    @Test
    void failedDiskWriteStillPublishesValidInMemoryCatalog() throws Exception {
        var parent = directory.resolve("not-a-directory");
        Files.writeString(parent, "file");
        var service = new ModelsDevCatalogService(parent.resolve("catalog.json"), Mono.just(CATALOG), CLOCK);
        var status = service.refresh().block();
        assertThat(status.available()).isTrue();
        assertThat(status.error()).contains("本地缓存");
    }
}
