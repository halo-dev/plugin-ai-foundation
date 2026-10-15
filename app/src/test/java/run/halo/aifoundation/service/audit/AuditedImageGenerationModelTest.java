package run.halo.aifoundation.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

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
    private final UsageStatisticsService statistics = mock(UsageStatisticsService.class);
    private final AuditedImageGenerationModel model = new AuditedImageGenerationModel(delegate,
        context, auditRecorder, statistics);

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void assemblesDelegateOnceAndExecutesEachColdSubscription(boolean enabled) {
        var descriptor = mock(UsageCallDescriptor.class);
        when(statistics.describeCall(context, "image.generateImage", false, null))
            .thenReturn(enabled ? descriptor : null);
        when(statistics.beginCall(descriptor)).thenReturn(mock(UsageCallSession.class));

        var publisher = model.generateImage("draw");

        assertThat(delegate.calls).hasValue(1);
        assertThat(delegate.executions).hasValue(0);
        verify(statistics, never()).beginCall(any());
        publisher.block();
        publisher.block();
        assertThat(delegate.calls).hasValue(1);
        assertThat(delegate.executions).hasValue(2);
        verify(statistics, times(enabled ? 2 : 0)).beginCall(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void synchronousDelegateFailureDoesNotCreateSession(boolean enabled) {
        var failingDelegate = mock(ImageGenerationModel.class);
        var request = GenerateImageRequest.builder().prompt("draw").build();
        var failure = new IllegalStateException("invalid request");
        when(failingDelegate.generateImage(request)).thenThrow(failure);
        when(statistics.describeCall(context, "image.generateImage", false, null))
            .thenReturn(enabled ? mock(UsageCallDescriptor.class) : null);
        var audited = new AuditedImageGenerationModel(failingDelegate, context, auditRecorder,
            statistics);

        assertThatThrownBy(() -> audited.generateImage(request)).isSameAs(failure);

        verify(statistics, never()).beginCall(any());
    }

    @Test
    void generateImageRecordsModelInvocation() {
        when(statistics.describeCall(context, "image.generateImage", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(UsageCallSession.class));

        StepVerifier.create(model.generateImage("draw"))
            .assertNext(result -> assertThat(result.getImage().getBase64()).isEqualTo("draw"))
            .verifyComplete();

        verify(auditRecorder).recordModelInvocation(context, "image.generateImage");
        assertThat(delegate.calls).hasValue(1);
    }

    @Test
    void wrappedShortCircuitStillRecordsModelInvocation() {
        when(statistics.describeCall(context, "image.generateImage", false, null))
            .thenReturn(mock(UsageCallDescriptor.class));
        when(statistics.beginCall(org.mockito.ArgumentMatchers.any()))
            .thenReturn(mock(UsageCallSession.class));
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
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
            calls.incrementAndGet();
            return Mono.fromSupplier(() -> {
                executions.incrementAndGet();
                return result(request.getPrompt());
            });
        }

        @Override
        public ModelCapabilities capabilities() {
            return ModelCapabilities.empty();
        }
    }
}
