package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.image.middleware.GenerateImageNext;
import run.halo.aifoundation.image.middleware.ImageGenerationContext;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddleware;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddlewares;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ModelType;

class AuditedImageGenerationModelTest {

    private final CallerPluginAuditRecorder auditRecorder = mock(CallerPluginAuditRecorder.class);
    private final RecordingImageGenerationModel delegate = new RecordingImageGenerationModel();
    private final ModelCallContext context = new ModelCallContext(
        ModelType.IMAGE_GENERATION,
        "default-image",
        "openai-provider",
        "openai",
        "gpt-image-1"
    );
    private final AuditedImageGenerationModel model = new AuditedImageGenerationModel(delegate,
        context, auditRecorder);

    @Test
    void generateImageRecordsModelInvocation() {
        StepVerifier.create(model.generateImage("draw"))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("draw"))
            .verifyComplete();

        verify(auditRecorder).recordModelInvocation(context, "image.generateImage");
        assertThat(delegate.calls).hasValue(1);
    }

    @Test
    void wrappedShortCircuitStillRecordsModelInvocation() {
        var wrapped = ImageGenerationMiddlewares.wrap(model, new ImageGenerationMiddleware() {
            @Override
            public Mono<GenerateImageResult> wrapGenerate(ImageGenerationContext context,
                GenerateImageNext next) {
                return Mono.just(result("cached"));
            }
        });

        StepVerifier.create(wrapped.generateImage("draw"))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("cached"))
            .verifyComplete();

        verify(auditRecorder).recordModelInvocation(context, "image.generateImage");
        assertThat(delegate.calls).hasValue(0);
    }

    @Test
    void exposesStableModelAndProviderInfo() {
        assertThat(model.modelInfo().getName()).isEqualTo("default-image");
        assertThat(model.modelInfo().getProviderName()).isEqualTo("openai-provider");
        assertThat(model.modelInfo().getModelId()).isEqualTo("gpt-image-1");
        assertThat(model.providerInfo().getName()).isEqualTo("openai-provider");
        assertThat(model.providerInfo().getProviderType()).isEqualTo("openai");
    }

    private static GenerateImageResult result(String base64) {
        return GenerateImageResult.builder()
            .images(List.of(GeneratedFile.base64(base64, "image/png")))
            .build();
    }

    private static final class RecordingImageGenerationModel implements ImageGenerationModel {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
            calls.incrementAndGet();
            return Mono.just(result(request.getPrompt()));
        }

        @Override
        public ModelCapabilities capabilities() {
            return ModelCapabilities.empty();
        }
    }
}
