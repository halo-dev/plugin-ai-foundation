package run.halo.aifoundation.service.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.control.CancellationToken;
import run.halo.aifoundation.exception.RerankCancelledException;
import run.halo.aifoundation.exception.RerankTimeoutException;
import run.halo.aifoundation.provider.support.ProviderRerankingClient;
import run.halo.aifoundation.provider.support.RerankingModelProviderOptions;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.rerank.RerankDocument;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankResult;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import org.junit.jupiter.api.Test;

class RerankingModelRuntimeFactoryTest {

    private final RerankingModelRuntimeFactory factory = new RerankingModelRuntimeFactory();

    @Test
    void rerank_preservesOriginalDocumentIndexes() {
        ProviderRerankingClient client = request -> Mono.just(RerankResponse.builder()
            .query(request.getQuery())
            .results(List.of(
                result(2, request.getDocuments().get(2), 0.9),
                result(0, request.getDocuments().get(0), 0.4)
            ))
            .build());
        var model = factory.create(client, configuration(null, null, null));

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha", "beta", "halo")
            .build();

        StepVerifier.create(model.rerank(request))
            .assertNext(response -> assertThat(response.getResults())
                .extracting(RerankResult::getIndex)
                .containsExactly(2, 0))
            .verifyComplete();
    }

    @Test
    void rerankAcceptsImageOnlyDocumentsAndRejectsEmptyDocuments() {
        ProviderRerankingClient client = request -> Mono.just(RerankResponse.builder()
            .query(request.getQuery())
            .results(List.of(result(0, request.getDocuments().getFirst(), 0.9)))
            .build());
        var model = factory.create(client, configuration(null, null, null));
        var image = RerankDocument.builder()
            .image(DataContent.url("https://example.com/halo.png"))
            .build();

        StepVerifier.create(model.rerank(RerankRequest.builder()
                .query("halo")
                .documents(List.of(image))
                .build()))
            .expectNextCount(1)
            .verifyComplete();

        StepVerifier.create(model.rerank(RerankRequest.builder()
                .query("halo")
                .documents(List.of(RerankDocument.builder().text(" ").build()))
                .build()))
            .expectErrorMatches(error -> error instanceof IllegalArgumentException
                && error.getMessage().contains("text or an image"))
            .verify();
    }

    @Test
    void rerank_cancelledBeforeProviderCallFails() {
        ProviderRerankingClient client = request -> Mono.error(new AssertionError("not called"));
        var model = factory.create(client, configuration(null, null, null));

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha")
            .cancellationToken((CancellationToken) () -> true)
            .build();

        StepVerifier.create(model.rerank(request))
            .expectError(RerankCancelledException.class)
            .verify();
    }

    @Test
    void rerank_timeoutFailsWithRerankTimeoutException() {
        ProviderRerankingClient client = request -> Mono.never();
        var model = factory.create(client, configuration(null, null, null));

        var request = RerankRequest.builder()
            .query("halo")
            .documents("alpha")
            .timeouts(GenerationTimeouts.total(Duration.ofMillis(10)))
            .build();

        StepVerifier.create(model.rerank(request))
            .expectError(RerankTimeoutException.class)
            .verify(Duration.ofSeconds(2));
    }

    @Test
    void rerank_appliesNestedTopNTemplate() {
        var target = new AtomicReference<run.halo.aifoundation.provider.mapping.ParameterMappingTarget>();
        var client = capturingClient(target);
        var mappings = mappings(run.halo.aifoundation.extension.ModelParameterMappings.Mode.TEMPLATE,
            "rerank.parameters.top-n");
        var model = factory.create(client,
            configuration(mappings, "rerank-model", "provider-a"));

        StepVerifier.create(model.rerank(RerankRequest.builder()
                .query("halo").documents("alpha").topN(1).build()))
            .expectNextCount(1)
            .verifyComplete();

        assertThat(target.get().root()).isEmpty();
        assertThat(target.get().parameters()).containsEntry("top_n", 1);
    }

    @Test
    void rerank_omitsUnsupportedTopNAndReturnsWarning() {
        var target = new AtomicReference<run.halo.aifoundation.provider.mapping.ParameterMappingTarget>();
        var model = factory.create(capturingClient(target),
            configuration(
                mappings(run.halo.aifoundation.extension.ModelParameterMappings.Mode.UNSUPPORTED,
                    null),
                "rerank-model", "provider-a"));

        StepVerifier.create(model.rerank(RerankRequest.builder()
                .query("halo").documents("alpha").topN(1).build()))
            .assertNext(response -> assertThat(response.getWarnings()).singleElement()
                .satisfies(warning -> assertThat(warning.getProviderMetadata())
                    .containsEntry("parameter", "TOP_N")
                    .containsEntry("modelName", "rerank-model")
                    .containsEntry("providerName", "provider-a")))
            .verifyComplete();
        assertThat(target.get().root()).isEmpty();
        assertThat(target.get().parameters()).isEmpty();
    }

    @Test
    void rerank_appliesAdministratorNativeOptionsFromModelContext() {
        var capturedOptions = new AtomicReference<Map<String, Object>>();
        var client = new ProviderRerankingClient() {
            @Override
            public Mono<RerankResponse> rerank(RerankRequest request) {
                return response(request);
            }

            @Override
            public Mono<RerankResponse> rerank(RerankRequest request,
                run.halo.aifoundation.provider.mapping.ParameterMappingTarget target,
                Map<String, Object> nativeOptions) {
                capturedOptions.set(nativeOptions);
                return response(request);
            }

            private Mono<RerankResponse> response(RerankRequest request) {
                return Mono.just(RerankResponse.builder().query(request.getQuery())
                    .results(List.of(result(0, request.getDocuments().getFirst(), 0.8))).build());
            }
        };
        var provider = mock(AiProviderType.class);
        when(provider.rerankingModelProviderOptions())
            .thenReturn(RerankingModelProviderOptions.defaults());
        var context = new ModelRuntimeContext("model-a", "rerank-a", "provider-a", "test-provider",
            AdapterType.RERANK, provider, RuntimeParameterMappings.empty(),
            Map.of("return_documents", true));
        var model = factory.create(client, RerankingModelRuntimeConfiguration.from(context));

        StepVerifier.create(model.rerank(RerankRequest.builder()
                .query("halo").documents("alpha").build()))
            .expectNextCount(1)
            .verifyComplete();

        assertThat(capturedOptions.get()).isEqualTo(Map.of("return_documents", true));
    }

    private ProviderRerankingClient capturingClient(
        AtomicReference<run.halo.aifoundation.provider.mapping.ParameterMappingTarget> target) {
        return new ProviderRerankingClient() {
            @Override
            public Mono<RerankResponse> rerank(RerankRequest request) {
                return response(request);
            }

            @Override
            public Mono<RerankResponse> rerank(RerankRequest request,
                run.halo.aifoundation.provider.mapping.ParameterMappingTarget mappedTarget) {
                target.set(mappedTarget);
                return response(request);
            }

            private Mono<RerankResponse> response(RerankRequest request) {
                return Mono.just(RerankResponse.builder().query(request.getQuery())
                    .results(List.of(result(0, request.getDocuments().getFirst(), 0.8))).build());
            }
        };
    }

    private run.halo.aifoundation.provider.mapping.EffectiveParameterMappings mappings(
        run.halo.aifoundation.extension.ModelParameterMappings.Mode mode, String template) {
        return new run.halo.aifoundation.provider.mapping.EffectiveParameterMappings(Map.of(
            run.halo.aifoundation.provider.mapping.ModelParameter.TOP_N,
            new run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.EffectiveMapping(
                mode, template, null,
                run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.Source.MODEL)));
    }

    private RerankingModelRuntimeConfiguration configuration(
        run.halo.aifoundation.provider.mapping.EffectiveParameterMappings mappings,
        String modelName, String providerName) {
        var runtimeMappings = new RuntimeParameterMappings(mappings, null, modelName, providerName);
        var context = ModelRuntimeContext.unresolved("test-provider", modelName, providerName,
            runtimeMappings);
        return new RerankingModelRuntimeConfiguration(context,
            RerankingModelProviderOptions.defaults());
    }

    private RerankResult result(int index, RerankDocument document, double score) {
        return RerankResult.builder()
            .index(index)
            .document(document)
            .score(score)
            .build();
    }
}
