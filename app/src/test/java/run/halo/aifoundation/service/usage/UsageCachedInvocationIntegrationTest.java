package run.halo.aifoundation.service.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelUsage;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.GenerateTextNext;
import run.halo.aifoundation.chat.middleware.LanguageModelGenerateContext;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.service.audit.AuditedLanguageModel;
import run.halo.aifoundation.service.audit.CallerPluginAuditRecorder;
import run.halo.aifoundation.service.audit.CallerPluginInfo;
import run.halo.aifoundation.service.audit.CallerPluginResolver;
import run.halo.aifoundation.service.audit.ModelCallContext;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.language.LanguageModelRuntimeConfiguration;
import run.halo.aifoundation.service.language.LanguageModelRuntimeFactory;
import run.halo.aifoundation.service.language.LanguageModelRuntimeProperties;
import run.halo.aifoundation.service.language.LanguageModelRuntimeSupport;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageQuality;
import run.halo.aifoundation.service.observation.UsageStatus;
import run.halo.aifoundation.service.observation.UsageUnitKind;

class UsageCachedInvocationIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ModelCallContext MODEL_CONTEXT = new ModelCallContext(
        ModelType.LANGUAGE, "model", "provider", "openai", "gpt-test");

    @TempDir
    Path temp;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatedSubscriptionCountsOnlyObservedExecutions(boolean cached) throws Exception {
        var subscriptions = new AtomicInteger();
        var wraps = new AtomicInteger();
        var expected = result(10, 5);
        var source = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP, 0,
            () -> Mono.fromSupplier(() -> {
                subscriptions.incrementAndGet();
                return expected;
            }), value -> NormalizedUsage.from(value.getTotalUsage()), ignored -> "gpt-test");
        try (var fixture = new Fixture()) {
            var publisher = fixture.audit(localModel(source)).generateText(
                request(cached, wraps));
            assertThat(wraps).hasValue(1);
            assertThat(subscriptions).hasValue(0);
            assertThat(fixture.service.summary(fixture.query).block(TIMEOUT).callCount()).isZero();

            assertThat(publisher.block(TIMEOUT)).isSameAs(expected);
            fixture.awaitCalls(1);
            var firstId = fixture.service.listCalls(fixture.query, 10, null)
                .block(TIMEOUT).items().getFirst().id();
            assertThat(publisher.block(TIMEOUT)).isSameAs(expected);
            fixture.awaitCalls(2);

            assertThat(expected.getTotalUsage().getTotalTokens()).isEqualTo(15);
            assertThat(wraps).hasValue(1);
            assertThat(subscriptions).hasValue(cached ? 1 : 2);
            assertRepeatedCalls(fixture, cached, firstId);
            fixture.reopen();
            assertRepeatedCalls(fixture, cached, firstId);
            assertThat(subscriptions).hasValue(cached ? 1 : 2);
        }
    }

    @Test
    void cachedBuiltInProviderSendsOneHttpRequestAndPersistsFifteenTokens() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                var body = """
                    {"id":"chat-test","object":"chat.completion","created":1,
                     "model":"gpt-test","choices":[{"index":0,
                     "message":{"role":"assistant","content":"answer"},
                     "finish_reason":"stop"}],
                     "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try (var fixture = new Fixture()) {
            var provider = new AiProvider();
            var spec = new AiProvider.AiProviderSpec();
            spec.setProviderType("openai");
            spec.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            provider.setSpec(spec);
            var providerType = new OpenAiProvider();
            var chat = providerType.buildChatModel(provider, "local-test-key",
                new ProviderModelRef("gpt-test", ModelType.LANGUAGE, AdapterType.OPENAI_CHAT));
            var runtime = new LanguageModelRuntimeFactory(new LanguageModelRuntimeSupport(),
                new MediaResourcePolicy(), new ModelCapabilityMatcher(),
                new LanguageModelRuntimeProperties(), new UsageExecutionObserver());
            var configuration = new LanguageModelRuntimeConfiguration(
                new ModelRuntimeContext("model", "gpt-test", "provider", "openai",
                    AdapterType.OPENAI_CHAT, providerType, null, null),
                providerType.languageModelProviderOptions(AdapterType.OPENAI_CHAT), null);
            var wraps = new AtomicInteger();
            var publisher = fixture.audit(runtime.create(chat, configuration))
                .generateText(request(true, wraps));

            var first = publisher.block(TIMEOUT);
            assertThat(first).isNotNull();
            assertThat(first.getText()).isEqualTo("answer");
            assertThat(first.getTotalUsage().getTotalTokens()).isEqualTo(15);
            fixture.awaitCalls(1);
            var firstId = fixture.service.listCalls(fixture.query, 10, null)
                .block(TIMEOUT).items().getFirst().id();
            var second = publisher.block(TIMEOUT);
            assertThat(second).isSameAs(first);
            assertThat(second.getTotalUsage().getTotalTokens()).isEqualTo(15);
            fixture.awaitCalls(2);

            assertThat(requests).hasValue(1);
            assertThat(wraps).hasValue(1);
            assertRepeatedCalls(fixture, true, firstId);
            fixture.reopen();
            assertRepeatedCalls(fixture, true, firstId);
            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resultUsageWithoutExecutionStaysUnknownInStatistics() throws Exception {
        var expected = result(10, 5);
        try (var fixture = new Fixture()) {
            var actual = fixture.audit(localModel(Mono.just(expected)))
                .generateText(request(false, new AtomicInteger())).block(TIMEOUT);
            assertThat(actual).isSameAs(expected);
            assertThat(actual.getTotalUsage().getTotalTokens()).isEqualTo(15);
            fixture.awaitCalls(1);
            assertUnknownCall(fixture);
            fixture.reopen();
            assertUnknownCall(fixture);
        }
    }

    @Test
    void observedZeroTokensRemainKnownAndFullyCovered() throws Exception {
        var expected = result(0, 0);
        var source = new UsageExecutionObserver().observe(UsageUnitKind.GENERATION_STEP, 0,
            () -> Mono.just(expected), value -> NormalizedUsage.from(value.getTotalUsage()),
            ignored -> "gpt-test");
        try (var fixture = new Fixture()) {
            assertThat(fixture.audit(localModel(source))
                .generateText(request(false, new AtomicInteger())).block(TIMEOUT))
                .isSameAs(expected);
            fixture.awaitCalls(1);
            assertKnownZero(fixture);
            fixture.reopen();
            assertKnownZero(fixture);
        }
    }

    @Test
    void lateStreamResultDoesNotRestoreUsageWithoutExecution() throws Exception {
        var expected = result(10, 5);
        try (var fixture = new Fixture()) {
            var stream = fixture.audit(localModel(Mono.just(expected)))
                .streamText(GenerateTextRequest.builder().prompt("hello").build());
            assertThat(stream.textStream().collectList().block(TIMEOUT)).containsExactly("answer");
            fixture.awaitCalls(1);
            assertUnknownCall(fixture);

            var actual = stream.result().block(TIMEOUT);
            assertThat(actual).isSameAs(expected);
            assertThat(actual.getTotalUsage().getTotalTokens()).isEqualTo(15);
            // Closing drains the late terminal before reopening the same SQLite database.
            fixture.reopen();
            assertUnknownCall(fixture);
        }
    }

    private static void assertRepeatedCalls(Fixture fixture, boolean cached, String firstId) {
        var summary = fixture.service.summary(fixture.query).block(TIMEOUT);
        assertThat(summary.callCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.inProgressCount()).isZero();
        assertThat(summary.accountedTotalTokens()).isEqualTo(cached ? 15L : 30L);
        assertThat(summary.missingUsageCalls()).isEqualTo(cached ? 1 : 0);
        assertThat(summary.partialUsageCalls()).isZero();
        assertThat(summary.usageCoverage()).isEqualTo(cached ? 0.5 : 1.0);
        assertThat(summary.complete()).isTrue();
        var calls = fixture.service.listCalls(fixture.query, 10, null).block(TIMEOUT).items();
        assertThat(calls).hasSize(2);
        var executionCount = 0;
        for (var call : calls) {
            assertThat(call.status()).isEqualTo(UsageStatus.SUCCEEDED);
            var detail = fixture.service.getCall(call.id()).block(TIMEOUT).orElseThrow();
            executionCount += detail.executions().size();
            if (cached && !call.id().equals(firstId)) {
                assertThat(detail.executions()).isEmpty();
                assertThat(call.attemptCount()).isZero();
                assertThat(call.usage().quality()).isEqualTo(UsageQuality.MISSING);
                assertThat(call.usage().accountedTotalTokens()).isNull();
            } else {
                assertThat(call.attemptCount()).isEqualTo(1);
                assertThat(call.usage().accountedTotalTokens()).isEqualTo(15L);
                assertThat(detail.executions()).singleElement().satisfies(execution -> {
                    assertThat(execution.status()).isEqualTo(UsageStatus.SUCCEEDED);
                    assertThat(execution.usage().accountedTotalTokens()).isEqualTo(15L);
                });
            }
        }
        assertThat(executionCount).isEqualTo(cached ? 1 : 2);
        assertHealthy(fixture);
    }

    private static void assertUnknownCall(Fixture fixture) {
        var summary = fixture.service.summary(fixture.query).block(TIMEOUT);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isNull();
        assertThat(summary.missingUsageCalls()).isEqualTo(1);
        assertThat(summary.usageCoverage()).isZero();
        var calls = fixture.service.listCalls(fixture.query, 10, null).block(TIMEOUT).items();
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.attemptCount()).isZero();
            assertThat(call.usage().quality()).isEqualTo(UsageQuality.MISSING);
            assertThat(call.usage().accountedTotalTokens()).isNull();
            assertThat(fixture.service.getCall(call.id()).block(TIMEOUT).orElseThrow().executions())
                .isEmpty();
        });
        assertHealthy(fixture);
    }

    private static void assertKnownZero(Fixture fixture) {
        var summary = fixture.service.summary(fixture.query).block(TIMEOUT);
        assertThat(summary.callCount()).isEqualTo(1);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.accountedTotalTokens()).isZero();
        assertThat(summary.missingUsageCalls()).isZero();
        assertThat(summary.usageCoverage()).isEqualTo(1.0);
        var calls = fixture.service.listCalls(fixture.query, 10, null).block(TIMEOUT).items();
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.attemptCount()).isEqualTo(1);
            assertThat(call.usage().accountedTotalTokens()).isZero();
            assertThat(call.usage().quality()).isEqualTo(UsageQuality.REPORTED_COMPONENTS);
            assertThat(fixture.service.getCall(call.id()).block(TIMEOUT).orElseThrow().executions())
                .singleElement().satisfies(execution -> {
                    assertThat(execution.usage().accountedTotalTokens()).isZero();
                    assertThat(execution.usage().quality()).isEqualTo(UsageQuality.REPORTED_COMPONENTS);
                });
        });
        assertHealthy(fixture);
    }

    private static void assertHealthy(Fixture fixture) {
        assertThat(fixture.service.health().complete()).isTrue();
        assertThat(fixture.service.health().droppedEvents()).isZero();
        assertThat(fixture.service.health().writeFailures()).isZero();
    }

    private static GenerateTextRequest request(boolean cached, AtomicInteger wraps) {
        return GenerateTextRequest.builder().prompt("hello").maxRetries(0)
            .middleware(new LanguageModelMiddleware() {
                @Override
                public Mono<GenerateTextResult> wrapGenerate(LanguageModelGenerateContext context,
                    GenerateTextNext next) {
                    wraps.incrementAndGet();
                    var source = next.generate(context.request());
                    return cached ? source.cache() : source;
                }
            }).build();
    }

    private static GenerateTextResult result(int input, int output) {
        return GenerateTextResult.builder().text("answer")
            .totalUsage(LanguageModelUsage.builder().inputTokens(input).outputTokens(output)
                .totalTokens(input + output).build()).build();
    }

    private static LanguageModel localModel(Mono<GenerateTextResult> source) {
        return new LanguageModel() {
            @Override
            public Mono<GenerateTextResult> generateText(String prompt) {
                return source;
            }

            @Override
            public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
                return source;
            }

            @Override
            public StreamTextResult streamText(GenerateTextRequest request) {
                return new StreamTextResult(Flux.empty(), Flux.just("answer"), Flux.empty(),
                    Flux.empty(), Mono.empty(), source);
            }
        };
    }

    private final class Fixture implements AutoCloseable {
        private final UsageDatabasePaths paths = new UsageDatabasePaths(temp);
        private final Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
        private final UsageQuery query = new UsageQuery(clock.instant().minusSeconds(1),
            clock.instant().plusSeconds(1), null, null, null, null, null, null, null, null, null);
        private final CallerPluginResolver resolver = mock(CallerPluginResolver.class);
        private UsageStatisticsService service;

        private Fixture() {
            when(resolver.resolveCurrentCallerSnapshot()).thenReturn(CallerPluginInfo.builder()
                .pluginName("integration-test").version("1").detectionSource("test").build());
            open();
        }

        private void open() {
            service = new UsageStatisticsService(new SqliteUsageStatisticsStore(paths), resolver,
                clock);
            service.initialize();
            assertThat(service.health().available()).isTrue();
        }

        private LanguageModel audit(LanguageModel delegate) {
            return new AuditedLanguageModel(delegate, MODEL_CONTEXT,
                mock(CallerPluginAuditRecorder.class), service);
        }

        private void awaitCalls(long count) throws InterruptedException {
            var deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (service.summary(query).block(TIMEOUT).successCount() < count
                && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(service.summary(query).block(TIMEOUT).successCount()).isEqualTo(count);
        }

        private void reopen() {
            service.close();
            open();
        }

        @Override
        public void close() {
            service.close();
        }
    }
}
