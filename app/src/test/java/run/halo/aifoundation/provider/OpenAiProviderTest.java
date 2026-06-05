package run.halo.aifoundation.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiProvider;
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

        var options = (OpenAiChatOptions) providerType.languageModelProviderOptions()
            .chatOptionsFactory()
            .build(request);

        assertThat(options.getReasoningEffort()).isEqualTo("high");
        assertThat(options.getSeed()).isEqualTo(42);
    }

    @Test
    void openAiApi_usesVersionedBaseUrlAndResourcePaths() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/chat/completions",
            exchange -> handleRequest(exchange, requests, chatCompletionBody()));
        server.createContext("/v1/embeddings",
            exchange -> handleRequest(exchange, requests, embeddingsBody()));
        server.start();

        try {
            var provider = provider("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            var chatApi = openAiApi(providerType.buildChatModel(provider, "sk-test", "gpt-test"));
            chatApi.chatCompletionEntity(new ChatCompletionRequest(
                List.of(new ChatCompletionMessage("hello", ChatCompletionMessage.Role.USER)),
                "gpt-test",
                0.1
            ));

            var embeddingApi = openAiApi(providerType.buildEmbeddingModel(provider, "sk-test",
                "text-embedding-test"));
            embeddingApi.embeddings(new EmbeddingRequest<>("hello", "text-embedding-test"));

            assertThat(requests).containsExactly(
                "POST /v1/chat/completions HTTP/1.1",
                "POST /v1/embeddings HTTP/1.1"
            );
        } finally {
            server.stop(0);
        }
    }

    private OpenAiApi openAiApi(Object model) throws ReflectiveOperationException {
        var field = model.getClass().getDeclaredField("openAiApi");
        field.setAccessible(true);
        return (OpenAiApi) field.get(model);
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

    private void handleRequest(HttpExchange exchange, CopyOnWriteArrayList<String> requests,
        String body) throws IOException {
        try (exchange) {
            exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                + " HTTP/1.1");
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private String chatCompletionBody() {
        return """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 0,
              "model": "gpt-test",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "ok"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
            """;
    }

    private String embeddingsBody() {
        return """
            {
              "object": "list",
              "data": [
                {
                  "object": "embedding",
                  "embedding": [0.1],
                  "index": 0
                }
              ],
              "model": "text-embedding-test"
            }
            """;
    }
}
