package run.halo.aifoundation.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.provider.support.OpenAiEmbeddingOptionsFactory;

class EmbeddingModelRuntimeFactoryTest {

    private final EmbeddingModelRuntimeFactory factory = new EmbeddingModelRuntimeFactory();

    @Test
    void create_preservesBatchingLimitsAndProviderTypeMetadata() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {2.0f}, 0))));

        var model = factory.create(springModel, "openai", 1, false,
            EmbeddingModelProviderOptions.defaults("openai"));

        StepVerifier.create(model.embed(List.of("first", "second")))
            .assertNext(response -> {
                assertThat(response.getEmbeddings()).hasSize(2);
                assertThat(response.getProviderMetadata()).containsEntry("providerType", "openai");
            })
            .verifyComplete();
    }

    @Test
    void create_appliesProviderOptionsAtCompositionBoundary() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));
        var model = factory.create(springModel, "openai", 96, false,
            new EmbeddingModelProviderOptions("openai", OpenAiEmbeddingOptionsFactory::build));

        StepVerifier.create(model.embed(run.halo.aifoundation.embedding.EmbeddingRequest.builder()
                .inputs(List.of("first"))
                .providerOptions(Map.of("openai", Map.of("dimensions", 512)))
                .build()))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(1))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        org.mockito.Mockito.verify(springModel).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiEmbeddingOptions.class);
        assertThat(captor.getValue().getOptions().getDimensions()).isEqualTo(512);
    }
}
