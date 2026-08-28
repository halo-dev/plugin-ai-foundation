package run.halo.aifoundation.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;

@ProviderContractSource(
    provider = "ollama",
    officialDocumentation = "https://docs.ollama.com/api/chat; "
        + "https://docs.ollama.com/capabilities/thinking; "
        + "https://docs.ollama.com/capabilities/tool-calling",
    retrievedAt = "2026-08-24"
)
class OllamaChatModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void sendsNativeImagesToolsSchemaOptionsAndLosslessThinkingHistory() throws Exception {
        var capturedBody = new AtomicReference<Map<String, Object>>();
        var capturedAuth = new AtomicReference<String>();
        var capturedHeader = new AtomicReference<String>();
        var server = server(exchange -> {
            capturedBody.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedHeader.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            respond(exchange, "application/json", """
                {"model":"qwen3","created_at":"2026-08-24T00:00:00Z",
                 "message":{"role":"assistant","thinking":"verify","content":"done",
                  "tool_calls":[{"function":{"index":0,"name":"weather",
                   "arguments":{"city":"Hangzhou"}}}]},
                 "done":true,"done_reason":"stop","prompt_eval_count":18,
                 "eval_count":6,"total_duration":1234}
                """);
        });
        try {
            var callback = toolCallback();
            var request = GenerateTextRequest.builder()
                .headers(Map.of("X-Trace", "trace-1"))
                .output(run.halo.aifoundation.schema.OutputSpec.object(
                    Map.of("type", "object", "properties", Map.of())))
                .build();
            var options = (OllamaChatOptions) OllamaChatOptionsSupport.applyNativeOptions(
                OllamaChatOptionsSupport.structured(request), Map.of(
                    "think", "high", "keep_alive", "10m",
                    "options", Map.of("num_ctx", 8192, "mirostat", 2)));
            options = options.mutate()
                .model("qwen3").toolCallbacks(List.of(callback)).build();
            var model = new OllamaChatModel(baseUrl(server), "secret", options,
                WebClient.builder());
            var prior = AssistantMessage.builder().content("")
                .properties(Map.of("reasoningContent", "inspect first"))
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                    "call-1", "function", "weather", "{\"city\":\"Shanghai\"}")))
                .build();
            var toolResult = ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse("call-1", "weather", "24°C"))).build();
            var user = UserMessage.builder().text("compare")
                .media(List.of(Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG)
                    .data(new byte[] {1, 2, 3}).build()))
                .build();

            var response = model.call(new Prompt(List.of(user, prior, toolResult), options));

            assertThat(capturedAuth.get()).isEqualTo("Bearer secret");
            assertThat(capturedHeader.get()).isEqualTo("trace-1");
            assertThat(capturedBody.get()).containsEntry("model", "qwen3")
                .containsEntry("think", "high")
                .containsEntry("keep_alive", "10m")
                .containsEntry("stream", false)
                .containsKey("format");
            assertThat((Map<String, Object>) capturedBody.get().get("options"))
                .containsEntry("num_ctx", 8192).containsEntry("mirostat", 2);
            var messages = (List<Map<String, Object>>) capturedBody.get().get("messages");
            assertThat(messages.get(0).get("images")).isEqualTo(List.of("AQID"));
            assertThat(messages.get(1)).containsEntry("thinking", "inspect first")
                .containsKey("tool_calls");
            assertThat(messages.get(2)).containsEntry("role", "tool")
                .containsEntry("tool_name", "weather");
            assertThat(response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningContent", "verify");
            assertThat(response.getResult().getOutput().getToolCalls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.id()).isEqualTo("ollama-tool-0");
                    assertThat(call.arguments()).isEqualTo("{\"city\":\"Hangzhou\"}");
                });
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(18);
            assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(6);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamsNdjsonReasoningToolLifecycleAndFinalUsageInWireOrder() throws Exception {
        var server = server(exchange -> respond(exchange, "application/x-ndjson", String.join("\n",
            "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\","
                + "\"thinking\":\"inspect\",\"content\":\"\"},\"done\":false}",
            "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\",\"tool_calls\":[{\"function\":{\"index\":0,"
                + "\"name\":\"weather\",\"arguments\":{\"city\":\"Hangzhou\"}}}]},"
                + "\"done\":false}",
            "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\","
                + "\"content\":\"\"},\"done\":true,\"done_reason\":\"stop\","
                + "\"prompt_eval_count\":10,\"eval_count\":4}", "")));
        try {
            var options = OllamaChatOptions.builder().model("qwen3").build();
            var model = new OllamaChatModel(baseUrl(server), "", options,
                WebClient.builder());

            var parts = model.streamParts(new Prompt(new UserMessage("weather"), options))
                .collectList().block();

            assertThat(parts).isNotNull();
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputStartPart.class::isInstance)
                .containsExactly(new ProviderStreamPart.ToolInputStartPart(
                    0, "ollama-tool-0", "weather"));
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputDeltaPart.class::isInstance)
                .containsExactly(new ProviderStreamPart.ToolInputDeltaPart(
                    0, "{\"city\":\"Hangzhou\"}"));
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputEndPart.class::isInstance)
                .hasSize(1);
            var responses = parts.stream()
                .filter(ProviderStreamPart.ChatResponsePart.class::isInstance)
                .map(ProviderStreamPart.ChatResponsePart.class::cast)
                .map(ProviderStreamPart.ChatResponsePart::response).toList();
            assertThat(responses).anySatisfy(response -> assertThat(
                response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningContent", "inspect"));
            assertThat(responses).anySatisfy(response -> {
                assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
                assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(4);
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsProtocolLevelBooleanThinkingWithoutInspectingModelId() {
        var request = GenerateTextRequest.builder()
            .build();
        var options = (OllamaChatOptions) OllamaChatOptionsSupport.applyNativeOptions(
            OllamaChatOptionsSupport.basic(request), Map.of("think", false));
        options = options.mutate()
            .model("gpt-oss:20b").build();
        assertThat(options.getToolContext()).containsEntry(
            OllamaChatOptionsSupport.MODEL_NATIVE_OPTIONS_CONTEXT_KEY,
            Map.of("think", false));
    }

    private ToolCallback toolCallback() {
        var callback = org.mockito.Mockito.mock(ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(
            ToolDefinition.builder().name("weather").description("Get weather")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .build());
        return callback;
    }

    private HttpServer server(ExchangeHandler handler) throws Exception {
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/api/chat", exchange -> {
            try (exchange) {
                handler.handle(exchange);
            }
        });
        server.start();
        return server;
    }

    private void respond(HttpExchange exchange, String contentType, String body)
        throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
