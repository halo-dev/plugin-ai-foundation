package run.halo.aifoundation.image.middleware;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.exception.ImageGenerationException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.image.ImageGenerationResults;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.model.ModelInfo;
import run.halo.aifoundation.model.ProviderInfo;

class ImageGenerationMiddlewaresTest {

    @Test
    void generateImageAppliesModelAndRequestMiddlewareInOrder() {
        var events = new ArrayList<String>();
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model,
            appendPrompt("model", events, "|model"));
        var request = GenerateImageRequest.builder()
            .prompt("draw")
            .middleware(appendPrompt("request", events, "|request"))
            .build();

        StepVerifier.create(wrapped.generateImage(request))
            .assertNext(result -> assertThat(result.getImage().getBase64())
                .isEqualTo("draw|model|request"))
            .verifyComplete();

        assertThat(events).containsExactly("model", "request");
        assertThat(model.capturedRequest.getMiddleware()).isNull();
    }

    @Test
    void applyRequestMiddlewareHonorsRequestMiddlewareWithoutExplicitWrap() {
        var events = new ArrayList<String>();
        var model = new RecordingImageGenerationModel();
        var request = GenerateImageRequest.builder()
            .prompt("draw")
            .middleware(appendPrompt("request", events, "|request"))
            .build();

        StepVerifier.create(ImageGenerationMiddlewares.applyRequestMiddleware(model, request))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("draw|request"))
            .verifyComplete();

        assertThat(events).containsExactly("request");
        assertThat(model.capturedRequest.getMiddleware()).isNull();
    }

    @Test
    void shortCircuitReturnsResultWithoutInvokingProvider() {
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model, shortCircuit("cached"));

        StepVerifier.create(wrapped.generateImage("draw"))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("cached"))
            .verifyComplete();

        assertThat(model.calls).hasValue(0);
    }

    @Test
    void shortCircuitReturnsErrorWithoutInvokingProvider() {
        var model = new RecordingImageGenerationModel();
        var failure = new IllegalStateException("blocked");
        var wrapped = ImageGenerationMiddlewares.wrap(model, new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return Mono.error(failure);
            }
        });

        StepVerifier.create(wrapped.generateImage("draw"))
            .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
            .verify();

        assertThat(model.calls).hasValue(0);
    }

    @Test
    void invalidShortCircuitResultFails() {
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model, new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return Mono.just(GenerateImageResult.builder().images(List.of()).build());
            }
        });

        StepVerifier.create(wrapped.generateImage("draw"))
            .expectError(ImageGenerationException.class)
            .verify();

        assertThat(model.calls).hasValue(0);
    }

    @Test
    void emptyShortCircuitResultFails() {
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model, new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return Mono.empty();
            }
        });

        StepVerifier.create(wrapped.generateImage("draw"))
            .expectError(ImageGenerationException.class)
            .verify();

        assertThat(model.calls).hasValue(0);
    }

    @Test
    void invalidRequestShapeFailsBeforeShortCircuitSuccess() {
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model, shortCircuit("cached"));

        StepVerifier.create(wrapped.generateImage(GenerateImageRequest.builder().prompt("").build()))
            .expectErrorMessage("Image generation prompt is required")
            .verify();

        assertThat(model.calls).hasValue(0);
    }

    @Test
    void helperMiddlewareAndWarningHelpersComposeResults() {
        var model = new RecordingImageGenerationModel();
        var defaults = GenerateImageRequest.builder()
            .size(1024)
            .responseFormat(ImageResponseFormat.BASE64)
            .providerOptions(Map.of("openai", Map.of("quality", "high")))
            .build();
        var warning = ImageGenerationWarning.builder()
            .code("test-warning")
            .message("added")
            .build();
        var wrapped = ImageGenerationMiddlewares.wrap(model,
            ImageGenerationMiddlewares.defaultSettings(defaults),
            ImageGenerationMiddlewares.mapRequest(request -> GenerateImageRequest.builder()
                .prompt(request.getPrompt() + "|mapped")
                .size(request.getSize())
                .responseFormat(request.getResponseFormat())
                .providerOptions(request.getProviderOptions())
                .build()),
            ImageGenerationMiddlewares.mapResult(result ->
                ImageGenerationResults.withWarnings(result, warning)));

        StepVerifier.create(wrapped.generateImage("draw"))
            .assertNext(result -> {
                assertThat(result.getImage().getBase64()).isEqualTo("draw|mapped");
                assertThat(result.getWarnings()).extracting(ImageGenerationWarning::getCode)
                    .containsExactly("test-warning");
            })
            .verifyComplete();

        assertThat(model.capturedRequest.getSize()).isEqualTo("1024x1024");
        assertThat(model.capturedRequest.getResponseFormat()).isEqualTo(ImageResponseFormat.BASE64);
        assertThat(model.capturedRequest.getProviderOptions())
            .containsEntry("openai", Map.of("quality", "high"));
    }

    @Test
    void contextExposesStableModelAndProviderInfo() {
        var model = new RecordingImageGenerationModel();
        var wrapped = ImageGenerationMiddlewares.wrap(model, new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageRequest> transformRequest(ImageGenerationContext context) {
                assertThat(context.capabilities().getImageGeneration().getTextToImage()).isTrue();
                assertThat(context.modelInfo().getName()).isEqualTo("image-model");
                assertThat(context.providerInfo().getName()).isEqualTo("openai-provider");
                assertThat(context.providerInfo().getProviderType()).isEqualTo("openai");
                return Mono.just(context.request());
            }
        });

        StepVerifier.create(wrapped.generateImage("draw"))
            .expectNextCount(1)
            .verifyComplete();
    }

    private static ImageGenerationMiddleware appendPrompt(String name, List<String> events,
        String suffix) {
        return new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageRequest> transformRequest(ImageGenerationContext context) {
                return Mono.fromSupplier(() -> {
                    events.add(name);
                    return GenerateImageRequest.builder()
                        .prompt(context.request().getPrompt() + suffix)
                        .build();
                });
            }
        };
    }

    private static ImageGenerationMiddleware shortCircuit(String base64) {
        return new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return Mono.just(result(base64));
            }
        };
    }

    private static GenerateImageResult result(String base64) {
        return GenerateImageResult.builder()
            .images(List.of(GeneratedFile.base64(base64, "image/png")))
            .build();
    }

    private static final class RecordingImageGenerationModel implements ImageGenerationModel {
        private final AtomicInteger calls = new AtomicInteger();
        private GenerateImageRequest capturedRequest;

        @Override
        public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
            calls.incrementAndGet();
            capturedRequest = request;
            return Mono.just(result(request.getPrompt()));
        }

        @Override
        public ModelCapabilities capabilities() {
            return ModelCapabilities.imageGeneration(ImageGenerationCapability.builder()
                .textToImage(true)
                .build());
        }

        @Override
        public ModelInfo modelInfo() {
            return ModelInfo.builder()
                .name("image-model")
                .providerName("openai-provider")
                .modelId("gpt-image-1")
                .enabled(true)
                .build();
        }

        @Override
        public ProviderInfo providerInfo() {
            return ProviderInfo.builder()
                .name("openai-provider")
                .providerType("openai")
                .enabled(true)
                .build();
        }
    }
}
