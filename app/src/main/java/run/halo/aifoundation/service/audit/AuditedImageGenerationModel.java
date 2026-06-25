package run.halo.aifoundation.service.audit;

import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;

public class AuditedImageGenerationModel implements ImageGenerationModel {

    private final ImageGenerationModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;

    public AuditedImageGenerationModel(ImageGenerationModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        auditRecorder.recordModelInvocation(context, "image.generateImage");
        return delegate.generateImage(request);
    }

    @Override
    public ModelCapabilities capabilities() {
        return delegate.capabilities();
    }
}
