package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.test.StepVerifier;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.openai.OpenAiChatModel;
import run.halo.aifoundation.provider.openai.OpenAiEmbeddingModel;
import run.halo.aifoundation.provider.openai.OpenAiImageGenerationClient;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openai.OpenAiResponsesModel;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "openai",
    officialDocumentation = "https://developers.openai.com/api/reference/resources/responses; "
        + "https://developers.openai.com/api/reference/resources/chat; "
        + "https://developers.openai.com/api/reference/resources/models/methods/list",
    retrievedAt = "2026-08-26"
)
class OpenAiProviderTest {

    private final OpenAiProvider providerType = new OpenAiProvider();

    @Test
    void optionsUseExplicitNativeReasoningEffort() {
        var request = GenerateTextRequest.builder()
            .prompt("Think carefully")
            .seed(42)
            .build();

        var options = (ChatCompletionsOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory()
            .build(request);
        options = options.mutate().extraBody(Map.of("reasoning_effort", "high")).build();

        assertThat(options.getExtraBody()).containsEntry("reasoning_effort", "high");
        assertThat(options.getSeed()).isEqualTo(42);
    }

    @Test
    void openAiModels_useRc1Options() {
        var provider = provider("http://127.0.0.1:8080/v1");

        var chatModel = (OpenAiResponsesModel) providerType.buildChatModel(provider, "sk-test",
            new ProviderModelRef("gpt-test", ModelType.LANGUAGE,
                AdapterType.OPENAI_RESPONSES));
        var chatOptions = chatModel.getOptions();
        assertThat(chatOptions.getBaseUrl()).isEqualTo("http://127.0.0.1:8080/v1");
        assertThat(chatOptions.getApiKey()).isEqualTo("sk-test");
        assertThat(chatOptions.getModel()).isEqualTo("gpt-test");
        assertThat(providerType.buildChatModel(provider, "sk-test",
            new ProviderModelRef("gpt-test", ModelType.LANGUAGE, AdapterType.OPENAI_CHAT)))
            .isInstanceOf(OpenAiChatModel.class);

        var embeddingModel = (OpenAiEmbeddingModel) providerType.buildEmbeddingModel(
            provider, "sk-test", "text-embedding-test");
        var embeddingOptions = ReflectionTestUtils.getField(embeddingModel, "defaults");
        assertThat(ReflectionTestUtils.getField(embeddingOptions, "baseUrl"))
            .isEqualTo("http://127.0.0.1:8080/v1");
        assertThat(ReflectionTestUtils.getField(embeddingOptions, "apiKey"))
            .isEqualTo("sk-test");
        assertThat(ReflectionTestUtils.getField(embeddingOptions, "model"))
            .isEqualTo("text-embedding-test");

        assertThat(providerType.getSupportedAdapterTypes()).contains(AdapterType.OPENAI_IMAGE);
        assertThat(providerType.buildImageGenerationClient(provider, "sk-test", "gpt-image-1"))
            .isInstanceOf(OpenAiImageGenerationClient.class);
    }

    @Test
    void documentedCatalogIsProviderOwnedAndKeepsIdentifierOnlyEvidenceConservative()
        throws Exception {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/models", exchange -> {
            try (exchange) {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                var body = """
                    {"object":"list","data":[
                      {"id":"opaque-provider-model","object":"model","owned_by":"openai"}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();

        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "sk-test"))
                .assertNext(models -> assertThat(models).singleElement().satisfies(model -> {
                    assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                    assertThat(model.adapterType()).isEqualTo(AdapterType.OPENAI_RESPONSES);
                    assertThat(model.features()).containsExactlyInAnyOrderElementsOf(
                        providerType.getSupportedFeatures(AdapterType.OPENAI_RESPONSES));
                    assertThat(model.source()).isEqualTo(DiscoverySource.RULE);
                    assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.LOW);
                }))
                .verifyComplete();
            assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        } finally {
            server.stop(0);
        }
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
