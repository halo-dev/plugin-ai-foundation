package run.halo.aifoundation.provider.support.image;

import run.halo.aifoundation.provider.siliconflow.SiliconFlowImageGenerationClient;

import run.halo.aifoundation.provider.dashscope.DashScopeImageGenerationClient;
import run.halo.aifoundation.provider.minimax.MiniMaxImageGenerationClient;
import run.halo.aifoundation.provider.openrouter.OpenRouterImageGenerationClient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.provider.doubao.DouBaoImageGenerationClient;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.DataContent;

class ProviderSpecificImageGenerationClientsTest {

    @Test
    void dashScopeGenerateImage_usesNativeEndpoint() {
        var capturedRequest = new AtomicReference<ClientRequest>();
        var webClientBuilder = WebClient.builder().exchangeFunction(request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"output\":{\"choices\":[]}}")
                .build());
        });
        var options = new ImageGenerationClientOptions("dashscope",
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "sk-test", "qwen-image",
            Map.of());
        var client = new DashScopeImageGenerationClient(options, webClientBuilder);

        client.generateImage(GenerateImageRequest.builder().prompt("Draw Halo").build()).block();

        assertThat(capturedRequest.get().url().toString()).isEqualTo(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/"
                + "multimodal-generation/generation");
    }

    @Test
    void openRouterRequestBody_mapsReferencesAndOptions() {
        var client = new OpenRouterImageGenerationClient(options("openrouter", "openrouter-image"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw Halo")
            .images(List.of(
                DataContent.url("https://example.com/reference.png", "image/png"),
                DataContent.data(new byte[] {1, 2, 3}, "image/png")
            ))
            .n(2)
            .size("2K")
            .aspectRatio("1:1")
            .seed(42)
            .build();

        var body = client.requestBody(request);

        assertThat(body)
            .containsEntry("model", "openrouter-image")
            .containsEntry("prompt", "Draw Halo")
            .containsEntry("n", 2)
            .containsEntry("size", "2K")
            .containsEntry("aspect_ratio", "1:1")
            .containsEntry("seed", 42);
        var references = listOfMaps(body.get("input_references"));
        assertThat(references).hasSize(2);
        assertThat(map(references.get(0).get("image_url")))
            .containsEntry("url", "https://example.com/reference.png");
        assertThat((String) map(references.get(1).get("image_url")).get("url"))
            .startsWith("data:image/png;base64,");
    }

    @Test
    void openRouterResponse_mapsFilesAndUsage() {
        var client = new OpenRouterImageGenerationClient(options("openrouter", "openrouter-image"),
            WebClient.builder());
        var json = """
            {
              "id": "img-openrouter",
              "model": "openrouter-image",
              "data": [
                {"b64_json": "abc123", "media_type": "image/webp"},
                {"b64_json": "def456", "media_type": "image/svg+xml"}
              ],
              "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 2,
                "total_tokens": 3,
                "generated_images": 2
              }
            }
            """;

        var result = client.imageResponse(json, GenerateImageRequest.builder()
            .prompt("Draw")
            .build());

        assertThat(result.getImages()).hasSize(2);
        assertThat(result.getImages().get(0).getBase64()).isEqualTo("abc123");
        assertThat(result.getImages().get(0).getMediaType()).isEqualTo("image/webp");
        assertThat(result.getImages().get(1).getBase64()).isEqualTo("def456");
        assertThat(result.getImages().get(1).getMediaType()).isEqualTo("image/svg+xml");
        assertThat(result.getUsage().getInputTokens()).isEqualTo(1);
        assertThat(result.getUsage().getOutputTokens()).isEqualTo(2);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(3);
        assertThat(result.getUsage().getImageCount()).isEqualTo(2);
        assertThat(result.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getId()).isEqualTo("img-openrouter"));
    }

    @Test
    void dashScopeRequestBody_mapsImagesTextAndParameters() {
        var client = new DashScopeImageGenerationClient(options("dashscope", "qwen-image"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Make it cinematic")
            .images(List.of(
                DataContent.url("https://example.com/base.png", "image/png"),
                DataContent.data(new byte[] {4, 5, 6}, "image/png")
            ))
            .n(1)
            .size("1024x1024")
            .seed(7)
            .negativePrompt("low quality")
            .build();

        var body = client.requestBody(request);

        assertThat(body).containsEntry("model", "qwen-image");
        var input = map(body.get("input"));
        var messages = listOfMaps(input.get("messages"));
        var content = listOfMaps(messages.get(0).get("content"));
        assertThat(content).hasSize(3);
        assertThat(content.get(0)).containsEntry("image", "https://example.com/base.png");
        assertThat((String) content.get(1).get("image"))
            .startsWith("data:image/png;base64,");
        assertThat(content.get(2)).containsEntry("text", "Make it cinematic");
        assertThat(map(body.get("parameters")))
            .containsEntry("n", 1)
            .containsEntry("size", "1024*1024")
            .containsEntry("seed", 7)
            .containsEntry("negative_prompt", "low quality");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dashScopeRequestBody_appliesDocumentedNativeOptions() {
        var client = new DashScopeImageGenerationClient(options("dashscope", "qwen-image"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw Halo")
            .n(2)
            .build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request, Map.of(
                "prompt_extend", false,
                "prompt_extend_mode", "agent",
                "enable_thinking", true,
                "watermark", false));

        assertThat(map(body.get("parameters")))
            .containsEntry("prompt_extend", false)
            .containsEntry("prompt_extend_mode", "agent")
            .containsEntry("enable_thinking", true)
            .containsEntry("watermark", false)
            .containsEntry("n", 2);
    }

    @Test
    void dashScopeRequestBody_rejectsUnknownNativeOptions() {
        var client = new DashScopeImageGenerationClient(options("dashscope", "qwen-image"),
            WebClient.builder());
        var request = GenerateImageRequest.builder().prompt("Draw Halo").build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                ReflectionTestUtils.invokeMethod(client, "requestBody", request,
                    Map.of("future_option", true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported DashScope image option(s): future_option");
    }

    @Test
    void dashScopeResponse_mapsChoicesImages() {
        var client = new DashScopeImageGenerationClient(options("dashscope", "qwen-image"),
            WebClient.builder());
        var json = """
            {
              "request_id": "dash-request",
              "model": "qwen-image",
              "output": {
                "choices": [{
                  "message": {
                    "content": [{"image": "https://example.com/dashscope.png"}]
                  }
                }]
              },
              "usage": {
                "input_tokens": 5,
                "total_tokens": 8,
                "image_count": 1
              }
            }
            """;

        var result = client.imageResponse(json, GenerateImageRequest.builder()
            .prompt("Draw")
            .build());

        assertThat(result.getImage().getUrl()).isEqualTo("https://example.com/dashscope.png");
        assertThat(result.getImage().getMediaType()).isEqualTo("image/png");
        assertThat(result.getUsage().getInputTokens()).isEqualTo(5);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(8);
        assertThat(result.getUsage().getImageCount()).isEqualTo(1);
        assertThat(result.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getId()).isEqualTo("dash-request"));
    }

    @Test
    void miniMaxRequestBody_mapsDimensionsReferencesAndFormat() {
        var client = new MiniMaxImageGenerationClient(options("minimax", "image-01"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw a mascot")
            .images(List.of(DataContent.url("https://example.com/subject.png", "image/png")))
            .size("1024x768")
            .responseFormat(ImageResponseFormat.BASE64)
            .seed(99)
            .n(2)
            .build();

        Map<String, Object> body = ReflectionTestUtils.invokeMethod(client, "requestBody", request);

        assertThat(body)
            .containsEntry("model", "image-01")
            .containsEntry("prompt", "Draw a mascot")
            .containsEntry("width", 1024)
            .containsEntry("height", 768)
            .containsEntry("response_format", "base64")
            .containsEntry("seed", 99)
            .containsEntry("n", 2);
        var references = listOfMaps(body.get("subject_reference"));
        assertThat(references).singleElement()
            .satisfies(reference -> assertThat(reference)
                .containsEntry("type", "character")
                .containsEntry("image_file", "https://example.com/subject.png"));
    }

    @Test
    void miniMaxResponse_mapsUrlsAndBase64() {
        var client = new MiniMaxImageGenerationClient(options("minimax", "image-01"),
            WebClient.builder());
        var json = """
            {
              "id": "minimax-response",
              "data": {
                "image_urls": ["https://example.com/minimax.png"],
                "image_base64": ["abc123"]
              },
              "metadata": {"success_count": 2}
            }
            """;

        GenerateImageResult result = ReflectionTestUtils.invokeMethod(client, "imageResponse",
            json, GenerateImageRequest.builder().prompt("Draw").build());

        assertThat(result.getImages()).hasSize(2);
        assertThat(result.getImages().get(0).getUrl()).isEqualTo("https://example.com/minimax.png");
        assertThat(result.getImages().get(1).getBase64()).isEqualTo("abc123");
        assertThat(result.getUsage().getImageCount()).isEqualTo(2);
        assertThat(map(result.getUsage().getRaw())).containsEntry("success_count", 2);
        assertThat(result.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getModel()).isEqualTo("image-01"));
    }

    @Test
    void modelArkRequestBody_mapsImagesAndResponseFormat() {
        var client = new DouBaoImageGenerationClient(options("doubao", "seedream"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Edit the first image")
            .images(List.of(
                DataContent.url("https://example.com/base.png", "image/png"),
                DataContent.data(new byte[] {7, 8, 9}, "image/png")
            ))
            .size("1024x1024")
            .seed(12)
            .responseFormat(ImageResponseFormat.BASE64)
            .build();

        @SuppressWarnings("unchecked")
        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request);

        assertThat(body)
            .containsEntry("model", "seedream")
            .containsEntry("prompt", "Edit the first image")
            .containsEntry("size", "1024x1024")
            .containsEntry("seed", 12)
            .containsEntry("response_format", "b64_json")
            .containsEntry("stream", false);
        assertThat((List<?>) body.get("image")).hasSize(2);
        assertThat((String) ((List<?>) body.get("image")).get(1))
            .startsWith("data:image/png;base64,");
    }

    @Test
    @SuppressWarnings("unchecked")
    void modelArkRequestBody_appliesNativeOptionsWithoutOverridingPortableFields() {
        var client = new DouBaoImageGenerationClient(options("doubao", "configured-model"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw Halo")
            .size("1024x1024")
            .build();

        var body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(client,
            "requestBody", request, Map.of(
                "watermark", false,
                "optimize_prompt_options", Map.of("mode", "standard"),
                "output_format", "webp",
                "model", "ignored-model",
                "size", "ignored-size"));

        assertThat(body)
            .containsEntry("watermark", false)
            .containsEntry("optimize_prompt_options", Map.of("mode", "standard"))
            .containsEntry("output_format", "webp")
            .containsEntry("model", "configured-model")
            .containsEntry("size", "1024x1024");
    }

    @Test
    void modelArkResponse_mapsDataUsageAndSize() {
        var client = new DouBaoImageGenerationClient(options("doubao", "seedream"),
            WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Draw")
            .build();
        var json = """
            {
              "id": "ark-response",
              "model": "seedream",
              "data": [
                {"url": "https://example.com/ark.jpg", "size": "1024x1024"},
                {"b64_json": "abc123", "size": "1024x1024"}
              ],
              "usage": {
                "generated_images": 2,
                "output_tokens": 4,
                "total_tokens": 4
              }
            }
            """;

        var result = (run.halo.aifoundation.image.GenerateImageResult)
            ReflectionTestUtils.invokeMethod(client, "imageResponse", json, request);

        assertThat(result.getImages()).hasSize(2);
        assertThat(result.getImages().get(0).getUrl()).isEqualTo("https://example.com/ark.jpg");
        assertThat(result.getImages().get(0).getMediaType()).isEqualTo("image/png");
        assertThat(result.getImages().get(0).getMetadata()).containsEntry("size", "1024x1024");
        assertThat(result.getImages().get(1).getBase64()).isEqualTo("abc123");
        assertThat(result.getUsage().getOutputTokens()).isEqualTo(4);
        assertThat(result.getUsage().getTotalTokens()).isEqualTo(4);
        assertThat(result.getUsage().getImageCount()).isEqualTo(2);
        assertThat(result.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getId()).isEqualTo("ark-response"));
    }

    @Test
    void siliconFlowRequestBody_mapsImageFields() {
        var client = new SiliconFlowImageGenerationClient(
            options("siliconflow", "Qwen/Qwen-Image-Edit"), WebClient.builder());
        var request = GenerateImageRequest.builder()
            .prompt("Create a variant")
            .images(List.of(DataContent.url("https://example.com/base.png", "image/png")))
            .n(2)
            .size("1024x1024")
            .seed(13)
            .build();

        var body = client.requestBody(request);

        assertThat(body)
            .containsEntry("model", "Qwen/Qwen-Image-Edit")
            .containsEntry("prompt", "Create a variant")
            .containsEntry("image", "https://example.com/base.png")
            .containsEntry("image_size", "1024x1024")
            .containsEntry("batch_size", 2)
            .containsEntry("seed", 13);
    }

    @Test
    void siliconFlowResponse_mapsImagesAndTimings() {
        var client = new SiliconFlowImageGenerationClient(options("siliconflow", "kolors"),
            WebClient.builder());
        var json = """
            {
              "id": "sf-response",
              "images": [{"url": "https://example.com/siliconflow.png"}],
              "timings": {"inference": 123},
              "seed": 4
            }
            """;

        var result = client.imageResponse(json, GenerateImageRequest.builder()
            .prompt("Draw")
            .build());

        assertThat(result.getImage().getUrl()).isEqualTo("https://example.com/siliconflow.png");
        assertThat(result.getImage().getMediaType()).isEqualTo("image/png");
        assertThat(result.getUsage().getImageCount()).isEqualTo(1);
        assertThat(map(map(result.getUsage().getRaw()).get("timings")))
            .containsEntry("inference", 123);
        assertThat(map(result.getUsage().getRaw())).containsEntry("seed", 4L);
        assertThat(result.getResponses()).singleElement()
            .satisfies(metadata -> assertThat(metadata.getId()).isNull());
    }

    private ImageGenerationClientOptions options(String providerType, String model) {
        return new ImageGenerationClientOptions(providerType, "http://localhost/v1", "sk-test",
            model, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
