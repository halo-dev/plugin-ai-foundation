package run.halo.aifoundation.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.ProviderModelRef;
import run.halo.aifoundation.provider.support.StructuredOutputSupport;
import run.halo.app.extension.Metadata;

@ProviderContractSource(
    provider = "ollama",
    officialDocumentation = "https://docs.ollama.com/api/chat; "
        + "https://docs.ollama.com/api/openai-compatibility; "
        + "https://docs.ollama.com/api/anthropic-compatibility; "
        + "https://docs.ollama.com/api-reference/show-model-details",
    retrievedAt = "2026-08-27"
)
class OllamaProviderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final OllamaProvider providerType = new OllamaProvider();

    @Test
    void declaresDedicatedAdaptersAndNativeStructuredOutput() {
        assertThat(providerType.getSupportedAdapterTypes())
            .containsExactly(AdapterType.OLLAMA_CHAT, AdapterType.OLLAMA_OPENAI_CHAT,
                AdapterType.OLLAMA_RESPONSES, AdapterType.OLLAMA_MESSAGES,
                AdapterType.OLLAMA_EMBEDDING, AdapterType.OLLAMA_IMAGE);
        assertThat(providerType.getSupportedModelTypes())
            .containsExactly(ModelType.LANGUAGE, ModelType.EMBEDDING,
                ModelType.IMAGE_GENERATION);
        assertThat(providerType.languageModelProviderOptions().structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.JSON_SCHEMA);
        assertThat(providerType.languageModelProviderOptions().reasoningHistorySupported())
            .isTrue();
        var responses = new ProviderModelRef("qwen3", ModelType.LANGUAGE,
            AdapterType.OLLAMA_RESPONSES);
        assertThat(providerType.buildChatModel(provider("http://127.0.0.1:11434/api"), "",
            responses)).isInstanceOf(OllamaResponsesModel.class);
        assertThat(providerType.buildChatModel(provider("http://127.0.0.1:11434/api"), "",
            new ProviderModelRef("qwen3", ModelType.LANGUAGE,
                AdapterType.OLLAMA_OPENAI_CHAT))).isInstanceOf(OllamaOpenAiChatModel.class);
        assertThat(providerType.buildChatModel(provider("http://127.0.0.1:11434/api"), "",
            new ProviderModelRef("qwen3", ModelType.LANGUAGE,
                AdapterType.OLLAMA_MESSAGES))).isInstanceOf(OllamaMessagesModel.class);
        assertThat(providerType.languageModelProviderOptions(
            AdapterType.OLLAMA_RESPONSES).structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.PROMPT_ONLY);
        assertThat(providerType.languageModelProviderOptions(
            AdapterType.OLLAMA_MESSAGES).structuredOutputSupport())
            .isEqualTo(StructuredOutputSupport.PROMPT_ONLY);
        assertThat(providerType.languageModelProviderOptions(AdapterType.OLLAMA_RESPONSES)
            .reasoningControlOptions().supportedEfforts()).isEmpty();
        assertThat(providerType.getDefaultParameterMappings(AdapterType.OLLAMA_RESPONSES)
            .get(ModelParameter.REASONING).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
        assertThat(providerType.getDefaultParameterMappings(AdapterType.OLLAMA_CHAT)
            .get(ModelParameter.MAX_OUTPUT_TOKENS).template())
            .isEqualTo("ollama.num-predict");
        assertThat(providerType.getDefaultParameterMappings(AdapterType.OLLAMA_CHAT)
            .get(ModelParameter.LOGPROBS).mode())
            .isEqualTo(ModelParameterMappings.Mode.UNSUPPORTED);
        var registry = new ParameterMappingTemplateRegistry();
        for (var adapter : List.of(AdapterType.OLLAMA_OPENAI_CHAT,
            AdapterType.OLLAMA_RESPONSES, AdapterType.OLLAMA_MESSAGES)) {
            assertThat(providerType.getDefaultParameterMappings(adapter)
                .get(ModelParameter.MAX_OUTPUT_TOKENS).template())
                .as(adapter.getValue())
                .isEqualTo("openai.max-tokens");
            assertThat(registry.compatible(ModelParameter.MAX_OUTPUT_TOKENS, adapter))
                .extracting("id")
                .contains("openai.max-tokens");
        }
        assertThat(providerType.buildImageGenerationClient(
            provider("http://127.0.0.1:11434/api"), "", "x/z-image-turbo"))
            .isInstanceOf(OllamaImageGenerationClient.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesKeepsOnlyDocumentedStatelessFields() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("http://127.0.0.1:11434/v1")
            .model("opaque-model")
            .toolContext("ollama-responses.messages", List.of(new UserMessage("Hello")))
            .build();
        var model = new OllamaResponsesModel(options, WebClient.builder());

        var streamBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, true);
        assertThat(streamBody).containsEntry("stream", true)
            .doesNotContainKey("stream_options");

        var stateful = options.mutate()
            .extraBody(Map.of("previous_response_id", "resp-opaque"))
            .build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", stateful, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("previous_response_id");
    }

    @Test
    @SuppressWarnings("unchecked")
    void messagesAcceptsBase64ImagesAndRejectsUnsupportedForcingAndUrls() {
        var image = new Media(MimeTypeUtils.IMAGE_PNG,
            new ByteArrayResource(new byte[] {1, 2, 3}));
        var prompt = new Prompt(UserMessage.builder().text("Describe")
            .media(image).build());
        var options = ChatCompletionsOptions.builder()
            .baseUrl("http://127.0.0.1:11434")
            .model("opaque-model")
            .toolChoice("auto")
            .build();
        var model = new OllamaMessagesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);
        var messages = (List<Map<String, Object>>) body.get("messages");
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");
        var source = (Map<String, Object>) content.get(1).get("source");
        assertThat(body).doesNotContainKey("tool_choice");
        assertThat(source).containsEntry("type", "base64")
            .containsEntry("data", "AQID");

        var forced = options.mutate().toolChoice("required").build();
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, forced, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("forcing or disabling tool use");

        var urlPrompt = new Prompt(UserMessage.builder().text("Describe").media(
            new Media(MimeTypeUtils.IMAGE_PNG,
                URI.create("https://example.com/image.png"))).build());
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(model,
            "requestBody", urlPrompt, options, false))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not image URLs");
    }

    @Test
    void discoverModelsUsesTagsAndShowCapabilitiesInsteadOfNameHeuristics() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/tags", exchange -> respond(exchange, requests, """
            {"models":[{"name":"custom-chat"},{"name":"custom-vector"}]}
            """));
        server.createContext("/api/show", exchange -> {
            var request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            var name = request.path("model").asText();
            respond(exchange, requests, "custom-chat".equals(name) ? """
                {"capabilities":["completion","vision","tools","thinking"],
                 "model_info":{"custom.context_length":32768}}
                """ : """
                {"capabilities":["embedding"],
                 "model_info":{"custom.context_length":8192}}
                """);
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            StepVerifier.create(providerType.discoverModels(provider(baseUrl), "cloud-key"))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);
                    assertThat(models).anySatisfy(model -> {
                        assertThat(model.modelId()).isEqualTo("custom-chat");
                        assertThat(model.modelType()).isEqualTo(ModelType.LANGUAGE);
                        assertThat(model.adapterType()).isEqualTo(AdapterType.OLLAMA_CHAT);
                        assertThat(model.features()).containsExactlyInAnyOrder(
                            ModelFeature.STREAMING, ModelFeature.STRUCTURED_OUTPUT,
                            ModelFeature.VISION, ModelFeature.TOOL_CALL, ModelFeature.REASONING);
                        assertThat(model.source()).isEqualTo(DiscoverySource.REMOTE);
                        assertThat(model.confidence()).isEqualTo(DiscoveryConfidence.HIGH);
                    });
                    assertThat(models).anySatisfy(model -> {
                        assertThat(model.modelId()).isEqualTo("custom-vector");
                        assertThat(model.modelType()).isEqualTo(ModelType.EMBEDDING);
                        assertThat(model.adapterType()).isEqualTo(AdapterType.OLLAMA_EMBEDDING);
                    });
                })
                .verifyComplete();
            assertThat(requests).hasSize(3)
                .allSatisfy(value -> assertThat(value).endsWith("|Bearer cloud-key"));
        } finally {
            server.stop(0);
        }
    }

    private void respond(HttpExchange exchange, CopyOnWriteArrayList<String> requests, String body)
        throws IOException {
        try (exchange) {
            requests.add(exchange.getRequestURI().getPath() + "|"
                + exchange.getRequestHeaders().getFirst("Authorization"));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private AiProvider provider(String baseUrl) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("ollama-provider");
        provider.setMetadata(metadata);
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("ollama");
        spec.setBaseUrl(baseUrl);
        provider.setSpec(spec);
        return provider;
    }
}
