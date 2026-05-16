package run.halo.aifoundation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.test.StepVerifier;

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
}
