package run.halo.aifoundation.provider.protocol.messages;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;

@ProviderContractSource(
    provider = "messages-protocol",
    officialDocumentation = "https://docs.anthropic.com/en/api/messages",
    retrievedAt = "2026-08-24"
)
class AnthropicMessagesModelTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void sendsNativeHeadersToolsAndParsesSignedThinkingUsage() throws Exception {
        var requestBody = new AtomicReference<Map<String, Object>>();
        var apiKey = new AtomicReference<String>();
        var version = new AtomicReference<String>();
        var server = server("/v1/messages", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            version.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
            requestBody.set(OBJECT_MAPPER.readValue(exchange.getRequestBody(), Map.class));
            var bytes = """
                {"id":"msg-1","type":"message","role":"assistant","model":"model-1",
                 "container":null,
                 "content":[
                   {"type":"thinking","thinking":"Inspect first","signature":"signed-1"},
                   {"type":"text","text":"Done"},
                   {"type":"tool_use","id":"tool-1","name":"search",
                    "input":{"q":"Halo"}}],
                 "stop_reason":"tool_use",
                 "usage":{"input_tokens":12,"output_tokens":7,
                  "cache_creation_input_tokens":3,"cache_read_input_tokens":4}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        });
        try {
            var callback = org.mockito.Mockito.mock(ToolCallback.class);
            org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(
                ToolDefinition.builder().name("search").description("Search docs")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build());
            var options = ChatCompletionsOptions.builder()
                .baseUrl(baseUrl(server)).apiKey("secret").model("model-1")
                .maxTokens(1024).toolCallbacks(List.of(callback)).toolChoice("required")
                .build();
            var model = new AnthropicMessagesModel(options, WebClient.builder(),
                new TestProfile());

            var response = model.call(new Prompt(List.of(
                new SystemMessage("Be concise"), new UserMessage("Find Halo")), options));

            assertThat(apiKey.get()).isEqualTo("secret");
            assertThat(version.get()).isEqualTo("2023-06-01");
            assertThat(requestBody.get()).containsEntry("system", "Be concise")
                .containsEntry("max_tokens", 1024)
                .containsEntry("tool_choice", Map.of("type", "any"));
            var tools = (List<Map<String, Object>>) requestBody.get().get("tools");
            assertThat(tools).singleElement().satisfies(tool -> assertThat(tool)
                .containsEntry("name", "search")
                .containsKey("input_schema"));
            assertThat(response.getResult().getOutput().getText()).isEqualTo("Done");
            assertThat(response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningContent", "Inspect first")
                .containsEntry("reasoningSignature", "signed-1")
                .containsKey("reasoningBlocks");
            assertThat(response.getResult().getOutput().getToolCalls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.id()).isEqualTo("tool-1");
                    assertThat(call.name()).isEqualTo("search");
                    assertThat(call.arguments()).isEqualTo("{\"q\":\"Halo\"}");
                });
            assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(12);
            assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(7);
            assertThat((Object) response.getMetadata().get("container")).isNull();
            assertThat((Map<String, Object>) response.getMetadata().getUsage().getNativeUsage())
                .containsEntry("cache_creation_input_tokens", 3)
                .containsEntry("cache_read_input_tokens", 4);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void extraBodyCannotReplaceCanonicalInvocationFields() {
        var options = ChatCompletionsOptions.builder()
            .baseUrl("https://example.com/v1")
            .apiKey("secret")
            .model("model-1")
            .maxTokens(512)
            .extraBody(Map.of(
                "model", "injected-model",
                "messages", List.of(Map.of("role", "user", "content", "injected")),
                "stream", false))
            .build();
        var prompt = new Prompt(List.of(new UserMessage("Actual message")), options);
        var model = new AnthropicMessagesModel(options, WebClient.builder(), new TestProfile());

        var body = (Map<String, Object>) org.springframework.test.util.ReflectionTestUtils
            .invokeMethod(model, "requestBody", prompt, options, true);

        assertThat(body).containsEntry("model", "model-1")
            .containsEntry("max_tokens", 512)
            .containsEntry("stream", true);
        assertThat((List<Map<String, Object>>) body.get("messages"))
            .singleElement()
            .satisfies(message -> assertThat(message).containsEntry("content", "Actual message"));
    }

    @Test
    void streamsReasoningSignatureToolInputAndFinalUsageInWireOrder() throws Exception {
        var server = server("/v1/messages", exchange -> {
            var bytes = String.join("", List.of(
                event("{\"type\":\"message_start\",\"message\":{\"usage\":{"
                    + "\"input_tokens\":8,\"cache_read_input_tokens\":2}}}"),
                event("{\"type\":\"content_block_start\",\"index\":0,"
                    + "\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\"}}"),
                event("{\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"thinking_delta\","
                    + "\"thinking\":\"Inspect\"}}"),
                event("{\"type\":\"content_block_delta\",\"index\":0,"
                    + "\"delta\":{\"type\":\"signature_delta\","
                    + "\"signature\":\"signed-1\"}}"),
                event("{\"type\":\"content_block_stop\",\"index\":0}"),
                event("{\"type\":\"content_block_start\",\"index\":1,"
                    + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool-1\","
                    + "\"name\":\"search\",\"input\":{}}}"),
                event("{\"type\":\"content_block_delta\",\"index\":1,"
                    + "\"delta\":{\"type\":\"input_json_delta\","
                    + "\"partial_json\":\"{\\\"q\\\":\\\"Halo\\\"}\"}}"),
                event("{\"type\":\"content_block_stop\",\"index\":1}"),
                event("{\"type\":\"message_delta\",\"delta\":{"
                    + "\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":5}}"),
                event("{\"type\":\"message_stop\"}")
            )).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        });
        try {
            var options = ChatCompletionsOptions.builder()
                .baseUrl(baseUrl(server)).apiKey("secret").model("model-1")
                .maxTokens(1024).build();
            var model = new AnthropicMessagesModel(options, WebClient.builder(),
                new TestProfile());

            var parts = model.streamParts(new Prompt(new UserMessage("Find"), options))
                .collectList().block();

            assertThat(parts).isNotNull();
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputStartPart.class::isInstance)
                .singleElement().satisfies(part -> assertThat(part)
                    .isEqualTo(new ProviderStreamPart.ToolInputStartPart(1, "tool-1", "search")));
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputDeltaPart.class::isInstance)
                .singleElement().satisfies(part -> assertThat(part)
                    .isEqualTo(new ProviderStreamPart.ToolInputDeltaPart(
                        1, "{\"q\":\"Halo\"}")));
            assertThat(parts).filteredOn(ProviderStreamPart.ToolInputEndPart.class::isInstance)
                .singleElement();
            var responses = parts.stream()
                .filter(ProviderStreamPart.ChatResponsePart.class::isInstance)
                .map(ProviderStreamPart.ChatResponsePart.class::cast)
                .map(ProviderStreamPart.ChatResponsePart::response)
                .toList();
            assertThat(responses).anySatisfy(response -> assertThat(
                response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningContent", "Inspect"));
            assertThat(responses).anySatisfy(response -> assertThat(
                response.getResult().getOutput().getMetadata())
                .containsEntry("reasoningSignature", "signed-1")
                .containsKey("reasoningBlocks"));
            assertThat(responses).anySatisfy(response -> {
                assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(8);
                assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(5);
            });
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(String path, ExchangeHandler handler) throws Exception {
        var server = HttpServer.create(
            new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(path, exchange -> {
            try (exchange) {
                handler.handle(exchange);
            }
        });
        server.start();
        return server;
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String event(String data) {
        return "data: " + data + "\n\n";
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    private static final class TestProfile implements AnthropicMessagesProfile {
        @Override
        public String providerType() {
            return "test";
        }

        @Override
        public String adapterType() {
            return "test-messages";
        }
    }
}
