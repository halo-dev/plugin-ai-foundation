package run.halo.aifoundation.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderClientCache;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.aifoundation.service.media.MediaResourcePolicy;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.app.extension.Metadata;

class DefaultImageGenerationModelFactoryTest {

    @Test
    void create_returnsRuntimeWhenProviderClientExists() {
        var client = (ProviderImageGenerationClient) request -> Mono.just(GenerateImageResult.builder()
            .images(List.of(GeneratedFile.base64("img", "image/png")))
            .build());
        var providerClientCache = mock(ProviderClientCache.class);
        var provider = provider();
        var model = model();
        when(providerClientCache.getOrCreateImageGenerationClient(provider, "sk-test",
            "image-model-id")).thenReturn(client);
        var factory = new DefaultImageGenerationModelFactory(providerClientCache,
            new ModelCapabilityService(), new MediaResourcePolicy(), new ModelCapabilityMatcher());

        var imageModel = factory.create(new ModelResolution(model, provider,
            new ImageProviderType(), "sk-test"));

        assertThat(imageModel).isInstanceOf(ImageGenerationModelImpl.class);
        StepVerifier.create(imageModel.generateImage("Draw"))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("img"))
            .verifyComplete();
        verify(providerClientCache).getOrCreateImageGenerationClient(provider, "sk-test",
            "image-model-id");
    }

    private AiModel model() {
        var model = new AiModel();
        model.setMetadata(new Metadata());
        model.getMetadata().setName("image-model");
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("openai-provider");
        spec.setModelId("image-model-id");
        spec.setDisplayName("Image Model");
        spec.setModelType(ModelType.IMAGE_GENERATION);
        spec.setAdapterType(AdapterType.OPENAI_IMAGE);
        model.setSpec(spec);
        return model;
    }

    private AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(new Metadata());
        provider.getMetadata().setName("openai-provider");
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setDisplayName("OpenAI");
        provider.setSpec(spec);
        return provider;
    }

    private static class ImageProviderType implements AiProviderType {
        @Override
        public String getProviderType() {
            return "openai";
        }

        @Override
        public String getDisplayName() {
            return "OpenAI";
        }

        @Override
        public boolean isBuiltIn() {
            return true;
        }

        @Override
        public boolean requiresBaseUrl() {
            return false;
        }

        @Override
        public String getDefaultBaseUrl() {
            return "https://example.com";
        }

        @Override
        public List<AdapterType> getSupportedAdapterTypes() {
            return List.of(AdapterType.OPENAI_IMAGE);
        }

        @Override
        public ChatModel buildChatModel(AiProvider provider, String apiKey, String modelId) {
            return null;
        }

        @Override
        public Mono<List<DiscoveredModel>> discoverModels(AiProvider provider, String apiKey) {
            return Mono.just(List.of());
        }
    }
}
