package run.halo.aifoundation.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.openailike.OpenAiCompatibleEmbeddingOptions;
import run.halo.aifoundation.provider.openailike.OpenAiEmbeddingOptionsFactory;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.EmbeddingModelProviderOptions;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

class EmbeddingModelRuntimeFactoryTest {

    private final EmbeddingModelRuntimeFactory factory = new EmbeddingModelRuntimeFactory();

    @Test
    void create_preservesBatchingLimitsAndProviderTypeMetadata() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {2.0f}, 0))));

        var model = factory.create(springModel, configuration(1,
            EmbeddingModelProviderOptions.defaults()));

        StepVerifier.create(model.embed(List.of("first", "second")))
            .assertNext(response -> {
                assertThat(response.getEmbeddings()).hasSize(2);
                assertThat(response.getProviderMetadata()).containsEntry("providerType", "openai");
            })
            .verifyComplete();
    }

    @Test
    void create_appliesTypedDimensionsAtCompositionBoundary() {
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));
        var model = factory.create(springModel, configuration(96,
            new EmbeddingModelProviderOptions(OpenAiEmbeddingOptionsFactory::build)));

        StepVerifier.create(model.embed(run.halo.aifoundation.embedding.EmbeddingRequest.builder()
                .inputs(List.of("first"))
                .dimensions(512)
                .build()))
            .assertNext(response -> assertThat(response.getEmbeddings()).hasSize(1))
            .verifyComplete();

        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        org.mockito.Mockito.verify(springModel).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isInstanceOf(OpenAiCompatibleEmbeddingOptions.class);
        assertThat(captor.getValue().getOptions().getDimensions()).isEqualTo(512);
    }

    @Test
    void create_appliesAdministratorNativeOptionsFromModelContext() {
        var capturedOptions = new AtomicReference<Map<String, Object>>();
        var providerOptions = new EmbeddingModelProviderOptions((request, options, warnings) -> {
            capturedOptions.set(options.nativeOptions());
            return null;
        });
        var provider = mock(AiProviderType.class);
        when(provider.maxEmbeddingsPerCall()).thenReturn(96);
        when(provider.embeddingModelProviderOptions()).thenReturn(providerOptions);
        var context = new ModelRuntimeContext("model-a", "embedding-a", "provider-a", "openai",
            AdapterType.OPENAI_EMBEDDING, provider, RuntimeParameterMappings.empty(),
            Map.of("encoding_format", "base64"));
        var springModel = mock(EmbeddingModel.class);
        when(springModel.call(any(EmbeddingRequest.class)))
            .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {1.0f}, 0))));
        var model = factory.create(springModel, EmbeddingModelRuntimeConfiguration.from(context));

        StepVerifier.create(model.embed(List.of("first")))
            .expectNextCount(1)
            .verifyComplete();

        assertThat(capturedOptions.get()).isEqualTo(Map.of("encoding_format", "base64"));
    }

    private EmbeddingModelRuntimeConfiguration configuration(int maxEmbeddingsPerCall,
        EmbeddingModelProviderOptions providerOptions) {
        return new EmbeddingModelRuntimeConfiguration(ModelRuntimeContext.unresolved("openai"),
            maxEmbeddingsPerCall, false, providerOptions);
    }
}
