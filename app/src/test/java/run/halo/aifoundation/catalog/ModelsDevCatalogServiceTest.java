package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class ModelsDevCatalogServiceTest {

    static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String CATALOG = """
        {"openai":{"id":"openai","models":{"exact-model":{"id":"exact-model",
          "type":"chat","description":"Unused display text","cost":{"input":999},
          "capabilities":{"tools":{"supported":true},"structured_output":false,
            "reasoning":{"supported":false},"temperature":false,"top_p":false,"top_k":false},
          "limit":{"context":32768,"output":4096},
          "api":{"responses":{"base_url":"https://api.openai.com/v1","sdk":{"aisdk":"unused-sdk"}},
            "chat-completions":{"base_url":"https://api.openai.com/v1"}},
          "modalities":{"input":["text","image/png","image/jpeg","application/pdf"],
            "output":["text"]}}}}}
        """;
    static final String LEGACY_CATALOG = """
        {"openai":{"id":"openai","models":{"exact-model":{"id":"exact-model",
          "tool_call":true,"structured_output":false,"reasoning":false,
          "modalities":{"input":["text","image"],"output":["text"]}}}}}
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
        assertThat(Files.exists(file)).isTrue();

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
            CATALOG.replace("\"supported\":true", "\"supported\":\"yes\""))) {
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

    @Test
    void conditionalRefreshPreservesCatalogAndRenewsPersistedFreshness() throws Exception {
        var file = directory.resolve("catalog.json");
        var etag = "\"revision-one\"";
        new ModelsDevCatalogService(file,
            ignored -> Mono.just(new ModelsDevCatalogService.Download(CATALOG, etag, false)), CLOCK)
            .refresh().block();
        var requestedEtag = new AtomicReference<String>();
        var refreshedClock = Clock.offset(CLOCK, Duration.ofHours(25));
        var service = new ModelsDevCatalogService(file, received -> {
            requestedEtag.set(received);
            return Mono.just(new ModelsDevCatalogService.Download(null, null, true));
        }, refreshedClock);
        service.loadCache();
        var original = service.snapshot().catalog();
        var status = service.refreshIfStale().block();
        assertThat(requestedEtag).hasValue(etag);
        assertThat(service.snapshot().catalog()).isSameAs(original);
        assertThat(status.fetchedAt()).isEqualTo(refreshedClock.instant());
        assertThat(status.stale()).isFalse();
        assertThat(status.error()).isNull();
        var saved = new ObjectMapper().readTree(Files.readString(file));
        assertThat(saved.path("version").asInt()).isEqualTo(2);
        assertThat(saved.path("sourceUrl").asText()).isEqualTo(ModelsDevCatalogService.SOURCE_URL);
        assertThat(saved.path("etag").asText()).isEqualTo(etag);
        assertThat(saved.path("fetchedAt").asText()).isEqualTo(refreshedClock.instant().toString());
        assertThat(saved.path("catalog").path("openai").path("models").path("exact-model")
            .has("cost")).isFalse();
        assertThat(saved.toString()).doesNotContain("Unused display text");
        assertThat(saved.toString()).doesNotContain("unused-sdk", "\"cost\"", "\"sdk\"");
    }

    @Test
    void rejectsNotModifiedWithoutSuccessfulVersionedSnapshot() {
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            ignored -> Mono.just(new ModelsDevCatalogService.Download(null, "\"unknown\"", true)),
            CLOCK);
        assertThat(service.refresh().block().available()).isFalse();
        assertThat(service.snapshot()).isNull();
    }

    @Test
    void legacyCacheRemainsAvailableOfflineAndUpgradesOnlyAfterSuccessfulV2Response() throws Exception {
        var file = directory.resolve("catalog.json");
        var legacy = "{\"fetchedAt\":\"" + NOW + "\",\"catalog\":" + LEGACY_CATALOG + "}";
        Files.writeString(file, legacy);
        var response = new AtomicReference<String>();
        var attempted = new AtomicInteger();
        var service = new ModelsDevCatalogService(file, etag -> {
            assertThat(etag).isNull();
            attempted.incrementAndGet();
            return response.get() == null ? Mono.error(new IllegalStateException("offline"))
                : Mono.just(new ModelsDevCatalogService.Download(response.get(), "\"v2\"", false));
        }, CLOCK);
        service.loadCache();
        assertThat(service.status().stale()).isTrue();
        assertThat(service.snapshot().legacy()).isTrue();
        assertThat(service.snapshot().catalog().find("openai", "exact-model").model()).isNull();
        assertThat(service.refreshIfStale().block().available()).isTrue();
        assertThat(attempted).hasValue(1);
        assertThat(Files.readString(file)).isEqualTo(legacy);
        response.set(CATALOG);
        assertThat(service.refreshIfStale().block().stale()).isFalse();
        assertThat(service.snapshot().legacy()).isFalse();
        assertThat(Files.readString(file)).contains("\"version\":2");
    }

    @Test
    void downloaderSendsConditionalHeaderAndHandlesStatusWithoutReadingBody() {
        var requests = new AtomicInteger();
        var client = WebClient.builder().exchangeFunction(request -> {
            assertThat(request.url().toString()).isEqualTo(ModelsDevCatalogService.SOURCE_URL);
            if (requests.getAndIncrement() == 0) {
                assertThat(request.headers().getFirst(HttpHeaders.IF_NONE_MATCH)).isNull();
                return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.ETAG, "\"revision\"").body(CATALOG).build());
            }
            assertThat(request.headers().getFirst(HttpHeaders.IF_NONE_MATCH))
                .isEqualTo("\"revision\"");
            return Mono.just(ClientResponse.create(HttpStatus.NOT_MODIFIED)
                .header(HttpHeaders.ETAG, "\"revision\"").build());
        }).build();
        var downloader = ModelsDevCatalogService.downloader(client);
        var first = downloader.apply(null).block();
        assertThat(first.body()).isEqualTo(CATALOG);
        assertThat(first.notModified()).isFalse();
        var second = downloader.apply(first.etag()).block();
        assertThat(second.notModified()).isTrue();
        assertThat(second.body()).isNull();
    }

    @Test
    void productionSnapshotsAndCachesRetainOnlyReviewedProviderIdentities() throws Exception {
        var mapper = new ObjectMapper();
        var source = (ObjectNode) mapper.readTree(CATALOG);
        var unknown = (ObjectNode) source.path("openai").deepCopy();
        unknown.put("id", "unreviewed-provider");
        source.set("unreviewed-provider", unknown);
        var file = directory.resolve("catalog.json");
        var service = new ModelsDevCatalogService(file, Mono.just(source.toString()), CLOCK);
        assertThat(service.refresh().block().available()).isTrue();
        assertThat(service.snapshot().catalog().providers()).containsOnlyKeys("openai");
        assertThat(Files.readString(file)).doesNotContain("unreviewed-provider");

        // Restore a previous v2 cache that still contains providers we cannot identify safely.
        var oldCache = mapper.createObjectNode();
        oldCache.put("version", 2);
        oldCache.put("sourceUrl", ModelsDevCatalogService.SOURCE_URL);
        oldCache.put("fetchedAt", NOW.toString());
        oldCache.set("catalog", source);
        var oldFile = directory.resolve("previous-v2-cache.json");
        Files.writeString(oldFile, oldCache.toString());
        var restored = new ModelsDevCatalogService(oldFile, Mono.empty(), CLOCK);
        restored.loadCache();
        assertThat(restored.snapshot().catalog().providers()).containsOnlyKeys("openai");
    }

    @Test
    void stillValidatesUnselectedProvidersBeforeReplacingTheLastSuccessfulSnapshot() throws Exception {
        var body = new AtomicReference<>(CATALOG);
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            Mono.defer(() -> Mono.just(body.get())), CLOCK);
        service.refresh().block();
        var successful = service.snapshot();
        var malformed = (ObjectNode) new ObjectMapper().readTree(CATALOG);
        malformed.putObject("unreviewed-provider")
            .put("id", "different-provider").putObject("models");
        body.set(malformed.toString());
        assertThat(service.refresh().block().error()).isNotBlank();
        assertThat(service.snapshot()).isSameAs(successful);
    }

    @Test
    void offlineFirstStartupLoadsBundleOnlyAfterRemoteFailureAndKeepsItsFetchTime() throws Exception {
        var attempts = new AtomicInteger();
        var loaded = new AtomicInteger();
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(3)));
        var file = directory.resolve("catalog.json");
        var service = new ModelsDevCatalogService(file, ignored -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("offline"));
        }, CLOCK, () -> {
            assertThat(attempts.get()).isEqualTo(loaded.get() + 1);
            loaded.incrementAndGet();
            return bundle;
        });
        var status = service.initialize().block(Duration.ofSeconds(5));
        assertThat(status.available()).isTrue();
        assertThat(status.stale()).isTrue();
        assertThat(status.sourceUrl()).isEqualTo(ModelsDevCatalogService.SOURCE_URL);
        assertThat(status.fetchedAt()).isEqualTo(bundle.fetchedAt());
        assertThat(status.lastAttemptAt()).isEqualTo(NOW);
        assertThat(status.error()).contains("内置目录");
        assertThat(service.snapshot()).isSameAs(bundle);
        assertThat(loaded).hasValue(1);
        assertThat(Files.exists(file)).isFalse();
        assertThat(service.refresh().block().error()).contains("内置目录");
        assertThat(loaded).hasValue(2);
    }

    @Test
    void freshCacheStillTriggersRemoteStartupCheckAndWinsOverOlderBundle() throws Exception {
        var file = directory.resolve("catalog.json");
        var cachedAt = NOW.minus(Duration.ofHours(1));
        new ModelsDevCatalogService(file, Mono.just(CATALOG), Clock.fixed(cachedAt, ZoneOffset.UTC))
            .refresh().block();
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(3)));
        var attempts = new AtomicInteger();
        var service = new ModelsDevCatalogService(file, ignored -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("offline"));
        }, CLOCK, () -> bundle);
        var status = service.initialize().block(Duration.ofSeconds(5));
        assertThat(attempts).hasValue(1);
        assertThat(status.available()).isTrue();
        assertThat(status.stale()).isFalse();
        assertThat(status.fetchedAt()).isEqualTo(cachedAt);
        assertThat(service.snapshot()).isNotSameAs(bundle);
    }

    @Test
    void newerV2BundleWinsOverOlderV2CacheWithoutRewritingTheCache() throws Exception {
        var file = directory.resolve("catalog.json");
        new ModelsDevCatalogService(file, Mono.just(CATALOG), Clock.offset(CLOCK, Duration.ofDays(-5)))
            .refresh().block();
        var persisted = Files.readString(file);
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(2)));
        var service = new ModelsDevCatalogService(file,
            ignored -> Mono.error(new IllegalStateException("offline")), CLOCK, () -> bundle);
        service.initialize().block(Duration.ofSeconds(5));
        assertThat(service.snapshot()).isSameAs(bundle);
        assertThat(Files.readString(file)).isEqualTo(persisted);
    }

    @Test
    void v2BundleWinsOverNewerLegacyCache() throws Exception {
        var file = directory.resolve("catalog.json");
        Files.writeString(file, "{\"fetchedAt\":\"" + NOW + "\",\"catalog\":" + LEGACY_CATALOG + "}");
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(3)));
        var service = new ModelsDevCatalogService(file,
            ignored -> Mono.error(new IllegalStateException("offline")), CLOCK, () -> bundle);
        service.initialize().block(Duration.ofSeconds(5));
        assertThat(service.snapshot()).isSameAs(bundle);
        assertThat(service.snapshot().legacy()).isFalse();
    }

    @Test
    void successfulRemoteResponseTakesPriorityWithoutLoadingBundle() throws Exception {
        var bundle = bundledSnapshot(NOW.plus(Duration.ofDays(1)));
        var loaded = new AtomicInteger();
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            ignored -> Mono.just(new ModelsDevCatalogService.Download(CATALOG, "\"remote\"", false)),
            CLOCK, () -> {
                loaded.incrementAndGet();
                return bundle;
            });
        var status = service.initialize().block(Duration.ofSeconds(5));
        assertThat(status.error()).isNull();
        assertThat(status.fetchedAt()).isEqualTo(NOW);
        assertThat(service.snapshot().etag()).isEqualTo("\"remote\"");
        assertThat(loaded).hasValue(0);
    }

    @Test
    void laterRemoteSuccessClearsTheBundledFallbackOrigin() throws Exception {
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(1)));
        var attempts = new AtomicInteger();
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"), ignored ->
            attempts.getAndIncrement() == 1
                ? Mono.just(new ModelsDevCatalogService.Download(CATALOG, null, false))
                : Mono.error(new IllegalStateException("offline")), CLOCK, () -> bundle);
        assertThat(service.initialize().block(Duration.ofSeconds(5)).error()).contains("内置目录");
        assertThat(service.refresh().block().error()).isNull();
        assertThat(service.refresh().block().error()).contains("上次成功数据").doesNotContain("内置目录");
    }

    @Test
    void startupConditionalResponseRevalidatesCacheWithoutLoadingBundle() {
        var file = directory.resolve("catalog.json");
        var etag = "\"cached\"";
        new ModelsDevCatalogService(file,
            ignored -> Mono.just(new ModelsDevCatalogService.Download(CATALOG, etag, false)),
            Clock.offset(CLOCK, Duration.ofHours(-1))).refresh().block();
        var service = new ModelsDevCatalogService(file, sentEtag -> {
            assertThat(sentEtag).isEqualTo(etag);
            return Mono.just(new ModelsDevCatalogService.Download(null, null, true));
        }, CLOCK, () -> {
            throw new AssertionError("A successful revalidation must not load the bundled fallback");
        });
        var status = service.initialize().block(Duration.ofSeconds(5));
        assertThat(status.error()).isNull();
        assertThat(status.fetchedAt()).isEqualTo(NOW);
        assertThat(service.snapshot().etag()).isEqualTo(etag);
    }

    @Test
    void subsequentRemoteFailureCannotReplaceSuccessfulSnapshotWithOlderOrCorruptBundle() throws Exception {
        var online = new AtomicInteger();
        var bundle = new AtomicReference<>(bundledSnapshot(NOW.minus(Duration.ofDays(1))));
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"), ignored ->
            online.getAndIncrement() == 0
                ? Mono.just(new ModelsDevCatalogService.Download(CATALOG, "\"remote\"", false))
                : Mono.error(new IllegalStateException("offline")), CLOCK, () -> {
                    if (bundle.get() == null) {
                        throw new IllegalStateException("broken bundle");
                    }
                    return bundle.get();
                });
        service.refresh().block();
        var successful = service.snapshot();
        assertThat(service.refresh().block().available()).isTrue();
        assertThat(service.snapshot()).isSameAs(successful);
        bundle.set(null);
        assertThat(service.refresh().block().available()).isTrue();
        assertThat(service.snapshot()).isSameAs(successful);
    }

    @Test
    void timeoutUsesBundleAfterFifteenSecondsWithoutBlockingSnapshotReads() throws Exception {
        var loaded = new AtomicInteger();
        var bundle = bundledSnapshot(NOW.minus(Duration.ofDays(1)));
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            ignored -> Mono.never(), CLOCK, () -> {
                loaded.incrementAndGet();
                return bundle;
            });
        StepVerifier.withVirtualTime(service::refresh)
            .expectSubscription()
            .thenAwait(Duration.ofSeconds(14))
            .then(() -> {
                assertThat(service.snapshot()).isNull();
                assertThat(loaded).hasValue(0);
            })
            .thenAwait(Duration.ofSeconds(1))
            .assertNext(status -> {
                assertThat(status.available()).isTrue();
                assertThat(status.fetchedAt()).isEqualTo(bundle.fetchedAt());
                assertThat(loaded).hasValue(1);
            })
            .expectComplete()
            .verify(Duration.ofSeconds(5));
    }

    @Test
    void corruptCacheAndCorruptBundleLeaveProviderFallbackAvailable() throws Exception {
        var file = directory.resolve("catalog.json");
        Files.writeString(file, "corrupt");
        var service = new ModelsDevCatalogService(file,
            ignored -> Mono.error(new IllegalStateException("offline")), CLOCK, () -> {
                throw new IllegalStateException("bad bundled digest");
            });
        var status = service.initialize().block(Duration.ofSeconds(5));
        assertThat(status.available()).isFalse();
        assertThat(status.error()).contains("供应商信息");
        assertThat(service.snapshot()).isNull();
    }

    private ModelsDevCatalogService.Snapshot bundledSnapshot(Instant fetchedAt) throws Exception {
        var catalog = ModelsDevCatalog.parse(new ObjectMapper().readTree(CATALOG))
            .retainProviders(ModelsDevProviderIdentity.catalogIds());
        return new ModelsDevCatalogService.Snapshot(catalog, fetchedAt, null, false);
    }
}