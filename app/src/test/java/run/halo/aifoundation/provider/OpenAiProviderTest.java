package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatModel;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleEmbeddingModel;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleImageGenerationClient;
import run.halo.app.extension.Metadata;

class OpenAiProviderTest {

    private final OpenAiProvider providerType = new OpenAiProvider();

    @Test
    void options_applyTypedReasoningEffort() {
        var request = GenerateTextRequest.builder()
            .prompt("Think carefully")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
            .seed(42)
            .build();

        var options = (OpenAiCompatibleChatOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory()
            .build(request);

        assertThat(options.getReasoningEffort()).isEqualTo("high");
        assertThat(options.getSeed()).isEqualTo(42);
    }

    @Test
    void openAiModels_useRc1Options() {
        var provider = provider("http://127.0.0.1:8080/v1");

        var chatModel = (OpenAiCompatibleChatModel) providerType.buildChatModel(provider, "sk-test",
            "gpt-test");
        var chatOptions = chatModel.getOptions();
        assertThat(chatOptions.getBaseUrl()).isEqualTo("http://127.0.0.1:8080/v1");
        assertThat(chatOptions.getApiKey()).isEqualTo("sk-test");
        assertThat(chatOptions.getModel()).isEqualTo("gpt-test");

        var embeddingModel = (OpenAiCompatibleEmbeddingModel) providerType.buildEmbeddingModel(
            provider, "sk-test", "text-embedding-test");
        var embeddingOptions = embeddingModel.getOptions();
        assertThat(embeddingOptions.getBaseUrl()).isEqualTo("http://127.0.0.1:8080/v1");
        assertThat(embeddingOptions.getApiKey()).isEqualTo("sk-test");
        assertThat(embeddingOptions.getModel()).isEqualTo("text-embedding-test");

        assertThat(providerType.getSupportedAdapterTypes()).contains(AdapterType.OPENAI_IMAGE);
        assertThat(providerType.buildImageGenerationClient(provider, "sk-test", "gpt-image-1"))
            .isInstanceOf(OpenAiCompatibleImageGenerationClient.class);
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("openai-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }

}
