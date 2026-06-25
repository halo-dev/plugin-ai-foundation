package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;

class ZhiPuProviderTest {

    private final ZhiPuProvider providerType = new ZhiPuProvider();

    @Test
    void supportedAdapters_includeImageGeneration() {
        assertThat(providerType.getSupportedAdapterTypes())
            .containsExactly(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_EMBEDDING,
                AdapterType.RERANK, AdapterType.OPENAI_IMAGE);
        assertThat(providerType.getSupportedModelTypes())
            .containsExactly(ModelType.LANGUAGE, ModelType.EMBEDDING, ModelType.RERANK,
                ModelType.IMAGE_GENERATION);
    }

    @Test
    void inferModelProfile_detectsZhiPuImageGenerationModels() {
        var glmImage = providerType.inferModelProfile("glm-image");
        assertThat(glmImage.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(glmImage.adapterType()).isEqualTo(AdapterType.OPENAI_IMAGE);
        assertThat(glmImage.source()).isEqualTo(DiscoverySource.RULE);
        assertThat(glmImage.confidence()).isEqualTo(DiscoveryConfidence.LOW);

        var cogView = providerType.inferModelProfile("cogview-4");
        assertThat(cogView.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(cogView.adapterType()).isEqualTo(AdapterType.OPENAI_IMAGE);
    }
}
