package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.CancellationSource;
import run.halo.aifoundation.EmbeddingCancelledException;
import run.halo.aifoundation.EmbeddingLifecycle;
import run.halo.aifoundation.GenerationTimeouts;

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
