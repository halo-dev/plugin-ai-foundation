package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.CancellationSource;
import run.halo.aifoundation.EmbeddingCancelledException;
import run.halo.aifoundation.EmbeddingLifecycle;
import run.halo.aifoundation.GenerationTimeouts;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiEmbeddingOptionsFactory;

class EmbeddingModelImplTest {

    @Test
    void embed_mapsDimensionsAndMaxBatchSizeToSpringRequests() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {2.0f}, 0))));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first", "second"))
            .dimensions(256)
            .maxBatchSize(1)
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(2))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(springModel, org.mockito.Mockito.times(2)).call(captor.capture());

        assertThat(captor.getAllValues())
            .extracting(EmbeddingRequest::getInstructions)
            .containsExactly(List.of("first"), List.of("second"));
        assertThat(captor.getAllValues())
            .extracting(req -> req.getOptions().getDimensions())
            .containsExactly(256, 256);
    }

    @Test
    void embed_mapsOpenAiProviderOptionsToSpringOptions() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false,
            new EmbeddingModelProviderOptions("openai", false,
                OpenAiEmbeddingOptionsFactory::build));
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .providerOptions(Map.of("openai", Map.of(
                "dimensions", 512,
                "user", "tester"
            )))
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(1))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(springModel).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiEmbeddingOptions.class);
        var options = (OpenAiEmbeddingOptions) captor.getValue().getOptions();
        assertThat(options.getDimensions()).isEqualTo(512);
        assertThat(options.getUser()).isEqualTo("tester");
    }

    @Test
    void embed_warnsForUnsupportedHeadersAndUnknownProviderOptions() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false,
            new EmbeddingModelProviderOptions("openai", false,
                OpenAiEmbeddingOptionsFactory::build));
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .headers(Map.of("X-Test", "value"))
            .providerOptions(Map.of(
                "openai", Map.of("unknown", true),
                "google", Map.of("taskType", "CLASSIFICATION")
            ))
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getWarnings())
                .extracting(run.halo.aifoundation.EmbeddingWarning::getCode)
                .contains(
                    "unsupported-request-headers",
                    "unsupported-provider-option",
                    "ignored-provider-option-namespace"
                ))
            .verifyComplete();
    }

    @Test
    void embed_preservesInputOrderAndLimitsParallelCalls() {
        var springModel = mock(EmbeddingModel.class);
        var active = new AtomicInteger();
        var maxActive = new AtomicInteger();
        when(springModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            var current = active.incrementAndGet();
            maxActive.updateAndGet(value -> Math.max(value, current));
            try {
                var request = invocation.getArgument(0, EmbeddingRequest.class);
                var input = request.getInstructions().get(0);
                if ("first".equals(input)) {
                    Thread.sleep(100);
                }
                return new EmbeddingResponse(List.of(new Embedding(new float[] {
                    switch (input) {
                        case "first" -> 1.0f;
                        case "second" -> 2.0f;
                        default -> 3.0f;
                    }
                }, 0)));
            } finally {
                active.decrementAndGet();
            }
        });

        var model = new EmbeddingModelImpl(springModel, "openai", 96, true);
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first", "second", "third"))
            .maxBatchSize(1)
            .maxParallelCalls(2)
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getEmbeddings())
                .extracting(vector -> vector[0])
                .containsExactly(1.0f, 2.0f, 3.0f))
            .verifyComplete();
        assertThat(maxActive.get()).isLessThanOrEqualTo(2);
    }

    @Test
    void embed_retriesRetryableBatchFailures() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("temporary"))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .maxRetries(1)
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(1))
            .verifyComplete();
        verify(springModel, org.mockito.Mockito.times(2)).call(any(EmbeddingRequest.class));
    }

    @Test
    void embed_doesNotRetryWhenMaxRetriesIsZero() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenThrow(new RuntimeException("temporary"));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .maxRetries(0)
            .build();

        StepVerifier.create(model.embed(request))
            .expectError(RuntimeException.class)
            .verify();
        verify(springModel).call(any(EmbeddingRequest.class));
    }

    @Test
    void embed_mapsUsageAndResponseMetadata() {
        var springModel = mock(EmbeddingModel.class);
        var metadata = new EmbeddingResponseMetadata("text-embedding-3-small",
            new DefaultUsage(4, 0, 4, Map.of("tokens", 4)),
            Map.of("id", "emb-1"));
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0)),
                metadata));

        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);

        StepVerifier.create(model.embed(List.of("first")))
            .assertNext(response -> {
                assertThat(response.getUsage().getTokens()).isEqualTo(4);
                assertThat(response.getResponse().getModel()).isEqualTo("text-embedding-3-small");
                assertThat(response.getResponse().getMetadata()).containsEntry("id", "emb-1");
                assertThat(response.getProviderMetadata()).containsKey("batches");
            })
            .verifyComplete();
    }

    @Test
    void embed_rejectsInvalidRequestBeforeProviderCall() {
        var springModel = mock(EmbeddingModel.class);
        var model = new EmbeddingModelImpl(springModel, "openai", 96, true);
        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .maxParallelCalls(0)
            .build();

        StepVerifier.create(model.embed(request))
            .expectError(IllegalArgumentException.class)
            .verify();
        verifyNoInteractions(springModel);
    }

    @Test
    void embed_invokesLifecycleCallbacks() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));
        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);
        var events = new ArrayList<String>();

        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .dimensions(256)
            .lifecycle(new EmbeddingLifecycle() {
                @Override
                public Mono<Void> onStart(run.halo.aifoundation.EmbeddingStartEvent event) {
                    events.add("start:" + event.getInputs().size());
                    return Mono.empty();
                }

                @Override
                public Mono<Void> onFinish(run.halo.aifoundation.EmbeddingFinishEvent event) {
                    events.add("finish:" + event.getEmbeddingsCount());
                    return Mono.empty();
                }
            })
            .build();

        StepVerifier.create(model.embed(request))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(1))
            .verifyComplete();

        assertThat(events).containsExactly("start:1", "finish:1");
    }

    @Test
    void embed_failsBeforeProviderWhenCancelled() {
        var springModel = mock(EmbeddingModel.class);
        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);
        var source = new CancellationSource();
        source.cancel();

        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .cancellationToken(source.token())
            .build();

        StepVerifier.create(model.embed(request))
            .expectError(EmbeddingCancelledException.class)
            .verify();
    }

    @Test
    void embed_appliesTimeout() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(200);
            return new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0)));
        });
        var model = new EmbeddingModelImpl(springModel, "openai", 96, false);

        var request = run.halo.aifoundation.EmbeddingRequest.builder()
            .inputs(List.of("first"))
            .dimensions(256)
            .timeouts(GenerationTimeouts.total(Duration.ofMillis(20)))
            .build();

        StepVerifier.create(model.embed(request))
            .expectError(run.halo.aifoundation.EmbeddingTimeoutException.class)
            .verify();
    }
}
