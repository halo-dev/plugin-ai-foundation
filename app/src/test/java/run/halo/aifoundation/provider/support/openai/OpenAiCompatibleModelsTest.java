package run.halo.aifoundation.provider.support.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleEmbeddingOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.service.language.stream.FinalOnlyProviderStreamingChatModel;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;
import run.halo.aifoundation.service.language.stream.ProviderStreamingChatModels;

class OpenAiCompatibleModelsTest {

    @Test
    void chatRequestBody_serializesMappedCustomFieldsAndDeepSeekThinking() {
        var options = chatOptions().mutate()
            .maxTokens(null)
            .extraBody(Map.of(
                "output_limit", 256,
                "thinking", Map.of("type", "disabled")
            ))
            .build();
        var model = new OpenAiCompatibleChatModel(options, WebClient.builder());
        var prompt = new Prompt(List.of(new UserMessage("answer quickly")), options);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, options, false);

        assertThat(body).containsEntry("output_limit", 256)
            .doesNotContainKey("max_tokens");
        assertThat((Map<String, Object>) body.get("thinking"))
            .containsEntry("type", "disabled");
    }

    @Test
    void chatRequestBody_replaysReasoningContentForToolContinuation() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var assistant = AssistantMessage.builder()
            .content("")
            .properties(Map.of("reasoningContent", "tool reasoning"))
            .toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "weather",
                    "{\"city\":\"Hangzhou\"}"),
                new AssistantMessage.ToolCall("call-2", "function", "search",
                    "{\"query\":\"Halo\"}")
            ))
            .build();
        var prompt = new Prompt(List.of(assistant), chatOptions());

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, chatOptions(), false);
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) body.get("messages");

        assertThat(messages.getFirst())
            .containsEntry("reasoning_content", "tool reasoning");
        @SuppressWarnings("unchecked")
        var toolCalls = (List<Map<String, Object>>) messages.getFirst().get("tool_calls");
        assertThat(toolCalls).hasSize(2);
    }

    @Test
    void chatRequestBody_mapsUserMediaToOpenAiContentParts() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var image = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(new byte[] {1, 2, 3})
            .build();
        var audio = Media.builder()
            .mimeType(MimeTypeUtils.parseMimeType("audio/mp3"))
            .data(new byte[] {4, 5, 6})
            .build();
        var prompt = new Prompt(List.of(UserMessage.builder()
            .text("describe")
            .media(image, audio)
            .build()), chatOptions());

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, chatOptions(), true);

        assertThat(body).containsEntry("model", "gpt-test");
        assertThat(body).containsEntry("stream", true);
        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) body.get("messages");
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertThat(content).hasSize(3);
        assertThat(content.get(0)).containsEntry("type", "text")
            .containsEntry("text", "describe");
        assertThat(content.get(1)).containsEntry("type", "image_url");
        @SuppressWarnings("unchecked")
        var imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertThat((String) imageUrl.get("url")).startsWith("data:image/png;base64,");
        assertThat(content.get(2)).containsEntry("type", "input_audio");
        @SuppressWarnings("unchecked")
        var inputAudio = (Map<String, Object>) content.get(2).get("input_audio");
        assertThat(inputAudio).containsEntry("format", "mp3");
        assertThat(inputAudio.get("data")).isEqualTo(Base64.getEncoder()
            .encodeToString(new byte[] {4, 5, 6}));
    }

    @Test
    void chatRequestBody_preservesAssistantUrlMediaContentParts() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var image = Media.builder()
            .mimeType(MimeTypeUtils.IMAGE_PNG)
            .data(URI.create("https://example.com/image.png"))
            .build();
        var prompt = new Prompt(List.of(AssistantMessage.builder()
            .content("Earlier image")
            .media(List.of(image))
            .build()), chatOptions());

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", prompt, chatOptions(), false);

        @SuppressWarnings("unchecked")
        var messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages.getFirst()).containsEntry("role", "assistant");
        @SuppressWarnings("unchecked")
        var content = (List<Map<String, Object>>) messages.getFirst().get("content");
        assertThat(content).hasSize(2);
        assertThat(content.get(0)).containsEntry("type", "text")
            .containsEntry("text", "Earlier image");
        assertThat(content.get(1)).containsEntry("type", "image_url");
        @SuppressWarnings("unchecked")
        var imageUrl = (Map<String, Object>) content.get(1).get("image_url");
        assertThat(imageUrl).containsEntry("url", "https://example.com/image.png");
    }

    @Test
    void chatResponse_mapsAudioAndAdditionalMetadata() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var audio = Base64.getEncoder().encodeToString(new byte[] {7, 8, 9});
        var json = """
            {
              "id": "chatcmpl-test",
              "model": "gpt-test",
              "service_tier": "default",
              "choices": [{
                "index": 0,
                "finish_reason": "stop",
                "message": {
                  "role": "assistant",
                  "audio": {
                    "id": "audio-test",
                    "data": "%s",
                    "transcript": "spoken text",
                    "expires_at": 123
                  }
                }
              }]
            }
            """.formatted(audio);

        var response = (ChatResponse) ReflectionTestUtils.invokeMethod(model,
            "chatResponse", json, chatOptions());

        assertThat(response.getMetadata().getId()).isEqualTo("chatcmpl-test");
        assertThat((Object) response.getMetadata().get("service_tier")).isEqualTo("default");
        var output = response.getResult().getOutput();
        assertThat(output.getText()).isEqualTo("spoken text");
        assertThat(output.getMedia()).singleElement().satisfies(media -> {
            assertThat(media.getId()).isEqualTo("audio-test");
            assertThat(media.getMimeType()).isEqualTo(MimeTypeUtils.parseMimeType("audio/wav"));
            assertThat((byte[]) media.getData()).containsExactly(7, 8, 9);
        });
        assertThat(output.getMetadata()).containsEntry("audioId", "audio-test")
            .containsEntry("audioExpiresAt", 123L);
    }

    @Test
    void chatResponseChunk_preservesWhitespaceOnlyContentDelta() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var json = """
            {
              "id": "chatcmpl-test",
              "model": "gpt-test",
              "choices": [{
                "index": 0,
                "delta": {
                  "role": "assistant",
                  "content": "\\n\\n"
                }
              }]
            }
            """;

        var response = (ChatResponse) ReflectionTestUtils.invokeMethod(model,
            "chatResponseChunk", json, null, chatOptions());

        assertThat(response).isNotNull();
        assertThat(response.getResult().getOutput().getText()).isEqualTo("\n\n");
    }

    @Test
    void standardDialect_preservesNativeToolInputLifecycleFromRawSse() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var parts = streamParts(model,
            chunk("""
                {"id":"response-1","choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call-1","type":"function","function":{
                    "name":"weather","arguments":"{\\\"city\\\""
                  }}
                ]}}]}
                """),
            chunk("""
                {"id":"response-1","choices":[{"delta":{"tool_calls":[
                  {"index":0,"function":{"arguments":":\\\"Hangzhou\\\"}"}}
                ]}}]}
                """),
            chunk("""
                {"id":"response-1","choices":[{"delta":{},"finish_reason":"tool_calls"}]}
                """),
            "data: [DONE]\n\n");

        assertThat(toolInputParts(parts)).containsExactly(
            new ProviderStreamPart.ToolInputStartPart(0, "call-1", "weather"),
            new ProviderStreamPart.ToolInputDeltaPart(0, "{\"city\""),
            new ProviderStreamPart.ToolInputDeltaPart(0, ":\"Hangzhou\"}"),
            new ProviderStreamPart.ToolInputEndPart(0));
        assertThat(parts).anyMatch(ProviderStreamPart.ChatResponsePart.class::isInstance);
    }

    @Test
    void standardDialect_treatsSingleCompleteProviderFragmentAsNativeDelta() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var parts = streamParts(model,
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call-1","function":{"name":"weather","arguments":"{}"}}
                ]},"finish_reason":"tool_calls"}]}
                """));

        assertThat(toolInputParts(parts)).containsExactly(
            new ProviderStreamPart.ToolInputStartPart(0, "call-1", "weather"),
            new ProviderStreamPart.ToolInputDeltaPart(0, "{}"),
            new ProviderStreamPart.ToolInputEndPart(0));
    }

    @Test
    void standardDialect_usesStableFallbackForLateIdAndNameAndIsolatesInterleavedCalls() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        var parts = streamParts(model,
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"function":{"arguments":"{\\\"city\\\""}},
                  {"index":1,"id":"call-2","function":{"name":"search","arguments":"{\\\"q\\\""}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":1,"function":{"arguments":":\\\"Halo\\\"}"}},
                  {"index":0,"id":"provider-late","function":{"name":"weather","arguments":":\\\"HZ\\\"}"}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
                """));
        var toolParts = toolInputParts(parts);
        var starts = toolParts.stream()
            .filter(ProviderStreamPart.ToolInputStartPart.class::isInstance)
            .map(ProviderStreamPart.ToolInputStartPart.class::cast)
            .toList();

        assertThat(starts).hasSize(2);
        assertThat(starts).anySatisfy(start -> {
            assertThat(start.index()).isZero();
            assertThat(start.toolCallId()).startsWith("call_").isNotEqualTo("provider-late");
            assertThat(start.toolName()).isEqualTo("weather");
        });
        assertThat(starts).anySatisfy(start -> {
            assertThat(start.index()).isEqualTo(1);
            assertThat(start.toolCallId()).isEqualTo("call-2");
            assertThat(start.toolName()).isEqualTo("search");
        });
        assertThat(toolParts.stream()
            .filter(ProviderStreamPart.ToolInputDeltaPart.class::isInstance)
            .map(ProviderStreamPart.ToolInputDeltaPart.class::cast)
            .map(ProviderStreamPart.ToolInputDeltaPart::index))
            .containsExactly(1, 1, 0, 0);
    }

    @Test
    void cumulativeDialect_emitsSuffixAndStopsAfterSnapshotRegression() {
        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder(),
            new CumulativeToolInputStreamDialect());
        var parts = streamParts(model,
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call-1","function":{"name":"weather","arguments":"{\\\"a"}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"function":{"arguments":"{\\\"ab"}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"function":{"arguments":"{\\\"x"}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
                """));

        assertThat(toolInputParts(parts).stream()
            .filter(ProviderStreamPart.ToolInputDeltaPart.class::isInstance)
            .map(ProviderStreamPart.ToolInputDeltaPart.class::cast)
            .map(ProviderStreamPart.ToolInputDeltaPart::inputTextDelta))
            .containsExactly("{\"a", "b");
        assertThat(toolInputParts(parts)).endsWith(new ProviderStreamPart.ToolInputEndPart(0));
    }

    @Test
    void finalOnlyAdapterDoesNotFabricateToolInputAndUnnamedStreamFails() {
        ChatModel delegate = org.mockito.Mockito.mock(ChatModel.class);
        org.mockito.Mockito.when(delegate.stream(org.mockito.ArgumentMatchers.any(Prompt.class)))
            .thenReturn(Flux.just(new ChatResponse(List.of())));
        var finalOnly = new FinalOnlyProviderStreamingChatModel(delegate);

        assertThat(finalOnly.streamParts(new Prompt("hello")).collectList().block())
            .singleElement()
            .isInstanceOf(ProviderStreamPart.ChatResponsePart.class);
        assertThat(ProviderStreamingChatModels.adapt(delegate))
            .isInstanceOf(FinalOnlyProviderStreamingChatModel.class);

        var model = new OpenAiCompatibleChatModel(chatOptions(), WebClient.builder());
        assertThat(ProviderStreamingChatModels.adapt(model)).isSameAs(model);
        assertThatThrownBy(() -> streamParts(model,
            chunk("""
                {"choices":[{"delta":{"tool_calls":[
                  {"index":0,"id":"call-1","function":{"arguments":"{}"}}
                ]}}]}
                """),
            chunk("""
                {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}
                """)))
            .hasMessageContaining(
                "OpenAI-compatible tool call at index 0 did not provide a name");
    }

    @Test
    void chatUrl_usesConfiguredEndpointPath() {
        var model = new OpenAiCompatibleChatModel(chatOptions().mutate()
            .endpointPath("compatible/chat")
            .build(), WebClient.builder());

        var url = (String) ReflectionTestUtils.invokeMethod(model, "chatCompletionsUrl",
            model.getOptions());

        assertThat(url).isEqualTo("http://localhost/v1/compatible/chat");
    }

    @Test
    void imageRequestBody_mapsOpenAiCompatibleImageOptions() {
        var model = new OpenAiCompatibleImageGenerationClient(imageOptions(), WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw Halo")
            .n(2)
            .size("1024x1024")
            .responseFormat(ImageResponseFormat.BASE64)
            .build();

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", request);

        assertThat(body)
            .containsEntry("model", "gpt-image-test")
            .containsEntry("prompt", "Draw Halo")
            .containsEntry("n", 2)
            .containsEntry("size", "1024x1024")
            .containsEntry("response_format", "b64_json");
    }

    @Test
    void imageResponse_mapsGeneratedFilesWarningsAndUsage() {
        var model = new OpenAiCompatibleImageGenerationClient(imageOptions(), WebClient.builder());
        var json = """
            {
              "id": "img-response",
              "model": "gpt-image-test",
              "data": [{
                "b64_json": "abc123",
                "revised_prompt": "Draw Halo CMS"
              }],
              "usage": {
                "input_tokens": 3,
                "output_tokens": 4,
                "total_tokens": 7
              }
            }
            """;

        var response = (GenerateImageResult) ReflectionTestUtils.invokeMethod(model,
            "imageResponse", json, GenerateImageRequest.builder().prompt("Draw").build());

        assertThat(response.getImage().getBase64()).isEqualTo("abc123");
        assertThat(response.getImage().getMetadata()).containsEntry("revisedPrompt",
            "Draw Halo CMS");
        assertThat(response.getWarnings()).singleElement()
            .satisfies(warning -> assertThat(warning.getCode()).isEqualTo("prompt-revised"));
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(7);
        assertThat(response.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getId()).isEqualTo("img-response"));
    }

    @Test
    void embeddingResponse_mapsBase64EncodedVectors() {
        var model = new OpenAiCompatibleEmbeddingModel(embeddingOptions(), WebClient.builder());
        var encoded = Base64.getEncoder().encodeToString(ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(1.25f)
            .putFloat(-2.5f)
            .array());
        var json = """
            {
              "model": "text-embedding-test",
              "data": [{"index": 0, "embedding": "%s"}],
              "usage": {"prompt_tokens": 2, "total_tokens": 2}
            }
            """.formatted(encoded);

        var response = (EmbeddingResponse) ReflectionTestUtils.invokeMethod(model,
            "embeddingResponse", json);

        assertThat(response.getMetadata().getModel()).isEqualTo("text-embedding-test");
        assertThat(response.getResult().getOutput()).containsExactly(1.25f, -2.5f);
        assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);
    }

    @Test
    void embeddingRequestBody_matchesOpenAiCompatibleEmbeddingOptions() {
        var model = new OpenAiCompatibleEmbeddingModel(embeddingOptions(), WebClient.builder());
        var request = new EmbeddingRequest(List.of("hello"), OpenAiCompatibleEmbeddingOptions.builder()
            .model("text-embedding-request")
            .dimensions(256)
            .user("user-1")
            .build());

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", request.getInstructions(), request.getOptions());

        assertThat(body).containsEntry("input", List.of("hello"))
            .containsEntry("model", "text-embedding-request")
            .containsEntry("dimensions", 256)
            .containsEntry("user", "user-1");
    }

    @Test
    void embeddingRequestBody_serializesMappedCustomDimensionsFieldOnly() {
        var model = new OpenAiCompatibleEmbeddingModel(embeddingOptions(), WebClient.builder());
        var options = OpenAiCompatibleEmbeddingOptions.builder()
            .model("text-embedding-request")
            .extraBody(Map.of("output_dimension", 384))
            .build();
        var request = new EmbeddingRequest(List.of("hello"), options);

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(model,
            "requestBody", request.getInstructions(), request.getOptions());

        assertThat(body).containsEntry("output_dimension", 384)
            .doesNotContainKey("dimensions");
    }

    @Test
    void embeddingUrl_usesConfiguredEndpointPath() {
        var options = OpenAiCompatibleEmbeddingOptions.builder()
            .baseUrl("http://localhost/v1")
            .apiKey("sk-test")
            .model("text-embedding-test")
            .endpointPath("/compatible/embeddings")
            .build();
        var model = new OpenAiCompatibleEmbeddingModel(options, WebClient.builder());

        var url = (String) ReflectionTestUtils.invokeMethod(model, "embeddingsUrl", options);

        assertThat(url).isEqualTo("http://localhost/v1/compatible/embeddings");
    }

    @Test
    void imageUrl_usesConfiguredEndpointPath() {
        var model = new OpenAiCompatibleImageGenerationClient(
            new OpenAiCompatibleImageOptions("openai", "http://localhost/v1",
                "compatible/images", "sk-test", "gpt-image-test", Map.of()),
            WebClient.builder());

        var url = (String) ReflectionTestUtils.invokeMethod(model, "imagesGenerationsUrl");

        assertThat(url).isEqualTo("http://localhost/v1/compatible/images");
    }

    private OpenAiCompatibleChatOptions chatOptions() {
        return OpenAiCompatibleChatOptions.builder()
            .baseUrl("http://localhost/v1")
            .apiKey("sk-test")
            .model("gpt-test")
            .build();
    }

    private OpenAiCompatibleEmbeddingOptions embeddingOptions() {
        return OpenAiCompatibleEmbeddingOptions.builder()
            .baseUrl("http://localhost/v1")
            .apiKey("sk-test")
            .model("text-embedding-test")
            .build();
    }

    private OpenAiCompatibleImageOptions imageOptions() {
        return new OpenAiCompatibleImageOptions("openai", "http://localhost/v1", null, "sk-test",
            "gpt-image-test", Map.of());
    }

    private String chunk(String json) {
        try {
            return "data: " + new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(json).toString() + "\n\n";
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Invalid test stream fixture", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ProviderStreamPart> streamParts(OpenAiCompatibleChatModel model,
        String... rawChunks) {
        var data = (Flux<String>) ReflectionTestUtils.invokeMethod(model, "sseDataLines",
            Flux.fromArray(rawChunks));
        var parts = (Flux<ProviderStreamPart>) ReflectionTestUtils.invokeMethod(model,
            "providerStreamParts", data.filter(chunk -> !"[DONE]".equals(chunk)),
            model.getOptions());
        return parts.collectList().block();
    }

    private List<ProviderStreamPart> toolInputParts(List<ProviderStreamPart> parts) {
        return parts.stream()
            .filter(part -> !(part instanceof ProviderStreamPart.ChatResponsePart))
            .toList();
    }
}
