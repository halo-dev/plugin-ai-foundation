package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddleware;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddlewareAware;
import run.halo.aifoundation.image.middleware.ImageGenerationMiddlewares;
import run.halo.aifoundation.model.ModelInfo;
import run.halo.aifoundation.model.ProviderInfo;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageOperation;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

public class AuditedImageGenerationModel implements ImageGenerationModel,
    ImageGenerationMiddlewareAware {

    private static final String OPERATION = UsageOperation.IMAGE_GENERATE_IMAGE.value();

    private final ImageGenerationModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageStatisticsService usageStatistics;

    public AuditedImageGenerationModel(ImageGenerationModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageStatisticsService usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        auditRecorder.recordModelInvocation(context, OPERATION);
        var descriptor = usageStatistics.describeCall(context, OPERATION, false,
            request.getMetadata());
        return UsageCallRecorder.record(usageStatistics, descriptor,
            () -> delegate.generateImage(request), 1, AuditedImageGenerationModel::succeed);
    }

    @Override
    public ModelCapabilities capabilities() {
        return delegate.capabilities();
    }

    @Override
    public ModelInfo modelInfo() {
        return ModelInfo.builder()
            .name(context.modelName())
            .providerName(context.providerName())
            .modelId(context.modelId())
            .enabled(true)
            .build();
    }

    @Override
    public ProviderInfo providerInfo() {
        return ProviderInfo.builder()
            .name(context.providerName())
            .providerType(context.providerType())
            .enabled(true)
            .build();
    }

    private static void succeed(UsageCallSession session, GenerateImageResult result) {
        if (result == null) {
            session.succeed(NormalizedUsage.missing(), null, 0);
            return;
        }
        var responseModel = result.getResponses() == null || result.getResponses().isEmpty()
            ? null : result.getResponses().getLast().getModel();
        session.succeed(NormalizedUsage.from(result.getUsage()), responseModel, 1);
    }

    @Override
    public ImageGenerationModel wrapImageGenerationMiddleware(
        List<ImageGenerationMiddleware> middleware) {
        var wrapped = ImageGenerationMiddlewares.wrap(delegate, middleware);
        return new AuditedImageGenerationModel(wrapped, context, auditRecorder, usageStatistics);
    }
}
