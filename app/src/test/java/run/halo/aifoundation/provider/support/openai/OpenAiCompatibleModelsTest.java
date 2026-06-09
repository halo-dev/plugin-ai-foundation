package run.halo.aifoundation.provider.support.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleChatOptions;
import run.halo.aifoundation.provider.support.openai.OpenAiCompatibleEmbeddingOptions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.reactive.function.client.WebClient;

class OpenAiCompatibleModelsTest {

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
}
