package run.halo.aifoundation.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import run.halo.aifoundation.provider.contract.ProviderContractSource;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;

@ProviderContractSource(
    provider = "openai",
    officialDocumentation = "https://developers.openai.com/api/reference/resources/responses/methods/create",
    retrievedAt = "2026-08-24"
)
class OpenAiResponsesModelTest {

    @Test
    @SuppressWarnings("unchecked")
    void requestUsesResponsesItemsReasoningToolsAndTextFormat() {
        var callback = org.mockito.Mockito.mock(ToolCallback.class);
        org.mockito.Mockito.when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder()
            .name("weather")
            .description("Get weather")
            .inputSchema("{\"type\":\"object\",\"properties\":{}}")
            .build());
        var options = options("http://localhost/v1").mutate()
            .reasoningEffort("high")
            .responseFormat(ChatCompletionsOptions.ResponseFormat.builder()
                .type(ChatCompletionsOptions.ResponseFormat.Type.JSON_SCHEMA)
                .name("answer")
                .jsonSchema("{\"type\":\"object\",\"properties\":{}}")
                .strict(true)
                .build())
            .toolCallbacks(List.of(callback))
            .toolStrict(Map.of("weather", true))
            .build();
        var model = new OpenAiResponsesModel(options, WebClient.builder());
        var assistant = AssistantMessage.builder().content("").toolCalls(List.of(
            new AssistantMessage.ToolCall("call_1", "function", "weather", "{}"))).build();
        var toolResponse = ToolResponseMessage.builder().responses(List.of(
            new ToolResponseMessage.ToolResponse("call_1", "weather", "sunny"))).build();
        var prompt = new Prompt(List.of(new SystemMessage("Be concise"),
            new UserMessage("Weather?"), assistant, toolResponse), options);

        var requestOptions = (ChatCompletionsOptions) ReflectionTestUtils.invokeMethod(model,
            "request", prompt);
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", requestOptions, false);

        assertThat(body).containsEntry("reasoning", Map.of("effort", "high"));
        assertThat(body).containsEntry("store", false);
        assertThat((List<String>) body.get("include"))
            .contains("reasoning.encrypted_content");
        assertThat((Map<String, Object>) body.get("text")).containsKey("format");
        assertThat((List<Map<String, Object>>) body.get("tools")).singleElement()
            .satisfies(tool -> assertThat(tool)
                .containsEntry("name", "weather")
                .containsEntry("strict", true));
        assertThat((List<Map<String, Object>>) body.get("input"))
            .extracting(item -> item.get("type"))
            .contains("function_call", "function_call_output");
        assertThat(body).doesNotContainKeys("previous_response_id", "conversation");
    }

    @Test
    @SuppressWarnings("unchecked")
    void responsesMergesHostedToolsAndPreservesFileNames() {
        var hostedTool = Map.<String, Object>of("type", "web_search");
        var options = options("http://localhost/v1").mutate()
            .extraBody(Map.of("builtinTools", List.of(hostedTool)))
            .build();
        var model = new OpenAiResponsesModel(options, WebClient.builder());
        var media = org.springframework.ai.content.Media.builder()
            .mimeType(org.springframework.util.MimeType.valueOf("application/pdf"))
            .name("manual.pdf")
            .data(new org.springframework.core.io.ByteArrayResource(new byte[] {1, 2}))
            .build();
        var prompt = new Prompt(UserMessage.builder().text("Read").media(List.of(media)).build(),
            options);
        var requestOptions = (ChatCompletionsOptions) ReflectionTestUtils.invokeMethod(model,
            "request", prompt);
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", requestOptions, false);

        assertThat((List<Map<String, Object>>) body.get("tools")).containsExactly(hostedTool);
        var input = (List<Map<String, Object>>) body.get("input");
        var content = (List<Map<String, Object>>) input.getFirst().get("content");
        assertThat(content.getLast()).containsEntry("type", "input_file")
            .containsEntry("filename", "manual.pdf")
            .containsKey("file_data");
    }

    @Test
    @SuppressWarnings("unchecked")
    void extraBodyCannotReplaceCanonicalInvocationFields() {
        var options = options("http://localhost/v1").mutate()
            .extraBody(Map.of(
                "model", "injected-model",
                "input", List.of(Map.of("role", "user", "content", "injected")),
                "stream", false))
            .toolContext("openai-responses.messages", List.of(new UserMessage("Actual message")))
            .build();
        var model = new OpenAiResponsesModel(options, WebClient.builder());

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", options, true);

        assertThat(body).containsEntry("model", "gpt-test").containsEntry("stream", true);
        assertThat((List<Map<String, Object>>) body.get("input"))
            .singleElement()
            .satisfies(input -> assertThat(input).containsEntry("role", "user"));
    }

    @Test
    void normalizesNonStreamingAndFragmentedStreamingResponses() {
        var requestBody = new AtomicReference<String>();
        DisposableServer server = HttpServer.create().host("127.0.0.1").port(0)
            .route(routes -> routes.post("/v1/responses", (request, response) ->
                request.receive().aggregate().asString().flatMap(body -> {
                    requestBody.set(body);
                    if (body.contains("\"stream\":true")) {
                        response.header("Content-Type", "text/event-stream");
                        return response.sendString(reactor.core.publisher.Flux.just(
                            "data: {\"type\":\"response.output_text.delta\",",
                            "\"item_id\":\"message_1\",\"delta\":\"Hel\"}\n\n",
                            "data: {\"type\":\"response.output_text.delta\",",
                            "\"item_id\":\"message_1\",\"delta\":\"lo\"}\n\n",
                            "data: {\"type\":\"response.completed\",\"response\":{",
                            "\"id\":\"resp_1\",\"model\":\"gpt-test\",\"status\":",
                            "\"completed\",\"output\":[],\"usage\":{\"input_tokens\":1,",
                            "\"output_tokens\":2,\"total_tokens\":3}}}\n\n"
                        )).then();
                    }
                    response.header("Content-Type", "application/json");
                    return response.sendString(reactor.core.publisher.Mono.just("""
                        {"id":"resp_1","model":"gpt-test","status":"completed",
                         "output":[
                          {"id":"rs_1","type":"reasoning","summary":[],
                           "content":[{"type":"reasoning_text","text":"check"}]},
                          {"type":"message","content":[{
                           "type":"output_text","text":"Hello","annotations":[]}]}],
                         "usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}
                        """)).then();
                })))
            .bindNow();
        try {
            var model = new OpenAiResponsesModel(options("http://127.0.0.1:" + server.port()
                + "/v1"), WebClient.builder());

            var response = model.call(new Prompt("Hello"));
            var output = response.getResult().getOutput();
            assertThat(output.getText()).isEqualTo("Hello");
            assertThat(output.getMetadata()).containsEntry("reasoningContent", "check")
                .containsKey("responsesReasoningItems");
            assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(3);

            var replayPrompt = new Prompt(List.of(output), options("http://127.0.0.1:"
                + server.port() + "/v1"));
            var replayOptions = (ChatCompletionsOptions) ReflectionTestUtils.invokeMethod(model,
                "request", replayPrompt);
            var replayBody = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
                "requestBody", replayOptions, false);
            assertThat((List<Map<String, Object>>) replayBody.get("input"))
                .extracting(item -> item.get("type"))
                .startsWith("reasoning");

            var parts = model.streamParts(new Prompt("Hello")).collectList().block();
            assertThat(parts.stream()
                .filter(ProviderStreamPart.ChatResponsePart.class::isInstance)
                .map(ProviderStreamPart.ChatResponsePart.class::cast)
                .map(part -> part.response().getResult())
                .filter(java.util.Objects::nonNull)
                .map(result -> result.getOutput().getText())
                .filter(text -> text != null && !text.isEmpty()))
                .contains("Hel", "lo");
            assertThat(requestBody.get()).contains("\"stream\":true", "\"input\"");
        } finally {
            server.disposeNow();
        }
    }

    private ChatCompletionsOptions options(String baseUrl) {
        return ChatCompletionsOptions.builder()
            .baseUrl(baseUrl)
            .apiKey("sk-test")
            .model("gpt-test")
            .timeout(Duration.ofSeconds(5))
            .build();
    }
}
