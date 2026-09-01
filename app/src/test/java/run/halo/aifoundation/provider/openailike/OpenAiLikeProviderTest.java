package run.halo.aifoundation.provider.openailike;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ProviderFeatureSets;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "openailike",
    officialDocumentation = "https://platform.openai.com/docs/api-reference/chat; "
        + "https://platform.openai.com/docs/api-reference/embeddings; "
        + "https://platform.openai.com/docs/api-reference/images",
    retrievedAt = "2026-08-24"
)
class OpenAiLikeProviderTest {

    @Test
    void ownsFallbackClientsEndpointOverridesAndConservativeCapabilities() {
        var provider = provider("http://localhost:8080/v1");
        provider.getSpec().setChatEndpointPath("custom/chat");
        provider.getSpec().setEmbeddingEndpointPath("custom/embeddings");
        provider.getSpec().setImageEndpointPath("custom/images");
        var type = new OpenAiLikeProvider();

        var chat = type.buildChatModel(provider, "", "chat-model");
        var embedding = type.buildEmbeddingModel(provider, "", "embedding-model");
        var image = type.buildImageGenerationClient(provider, "", "image-model");

        assertThat(chat).isInstanceOf(OpenAiCompatibleChatModel.class);
        assertThat(embedding).isInstanceOf(OpenAiCompatibleEmbeddingModel.class);
        assertThat(image).isInstanceOf(OpenAiCompatibleImageGenerationClient.class);
        assertThat(((OpenAiCompatibleChatModel) chat).getOptions().getEndpointPath())
            .isEqualTo("/custom/chat");
        assertThat(((OpenAiCompatibleEmbeddingModel) embedding).getOptions().getEndpointPath())
            .isEqualTo("/custom/embeddings");
        var imageOptions = (OpenAiCompatibleImageOptions) ReflectionTestUtils.getField(image,
            "options");
        assertThat(imageOptions.endpointPath()).isEqualTo("/custom/images");
        assertThat(type.getSupportedAdapterTypes()).containsExactly(AdapterType.OPENAI_CHAT,
            AdapterType.OPENAI_EMBEDDING, AdapterType.RERANK, AdapterType.OPENAI_IMAGE);
        assertThat(type.getSupportedFeatures()).isEqualTo(ProviderFeatureSets.TEXT);
    }

    @Test
    void customHeadersWorkWithoutAnApiKey() {
        var authorization = new AtomicReference<String>();
        var customHeader = new AtomicReference<String>();
        DisposableServer server = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .route(routes -> routes.post("/chat", (request, response) -> {
                authorization.set(request.requestHeaders().get("Authorization"));
                customHeader.set(request.requestHeaders().get("X-Tenant"));
                return request.receive().aggregate().then(response
                    .header("Content-Type", "application/json")
                    .sendString(reactor.core.publisher.Mono.just("""
                        {"id":"response-1","model":"model-a","choices":[{
                          "message":{"role":"assistant","content":"ok"},
                          "finish_reason":"stop"}]}
                        """))
                    .then());
            }))
            .bindNow();
        try {
            var options = ChatCompletionsOptions.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .endpointPath("/chat")
                .apiKey("")
                .model("model-a")
                .customHeaders(Map.of("X-Tenant", "tenant-a"))
                .timeout(Duration.ofSeconds(5))
                .build();
            var model = new OpenAiCompatibleChatModel(options, WebClient.builder());

            assertThat(model.call(new Prompt("hello")).getResult().getOutput().getText())
                .isEqualTo("ok");
            assertThat(authorization).hasNullValue();
            assertThat(customHeader).hasValue("tenant-a");
        } finally {
            server.disposeNow();
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("custom-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openailike");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
