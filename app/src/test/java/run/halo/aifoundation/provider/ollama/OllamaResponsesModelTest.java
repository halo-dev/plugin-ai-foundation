package run.halo.aifoundation.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

@ProviderContractSource(
    provider = "ollama",
    officialDocumentation = "https://docs.ollama.com/api/openai-compatibility#v1-responses",
    retrievedAt = "2026-08-25"
)
class OllamaResponsesModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void usesStatelessResponsesEndpoint() throws Exception {
        var body = new AtomicReference<Map<String, Object>>();
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/v1/responses", exchange -> {
            try (exchange) {
                body.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
                var bytes = """
                    {"id":"resp_1","model":"qwen3","status":"completed",
                     "output":[{"type":"message","role":"assistant","status":"completed",
                      "content":[{"type":"output_text","text":"done","annotations":[]}]}],
                     "usage":{"input_tokens":3,"output_tokens":2,"total_tokens":5}}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            var options = ChatCompletionsOptions.builder()
                .baseUrl(baseUrl).endpointPath("/responses").model("qwen3").build();
            var model = new OllamaResponsesModel(options, WebClient.builder());

            var response = model.call(new Prompt(new UserMessage("hello"), options));

            assertThat(body.get()).containsEntry("model", "qwen3")
                .doesNotContainKey("store");
            assertThat((java.util.List<Map<String, Object>>) body.get().get("input"))
                .singleElement().satisfies(item -> assertThat(item)
                    .containsEntry("role", "user"));
            assertThat(response.getResult().getOutput().getText()).isEqualTo("done");
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(5);
        } finally {
            server.stop(0);
        }
    }
}
