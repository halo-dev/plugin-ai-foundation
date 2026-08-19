package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageOperation;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

public class AuditedEmbeddingModel implements EmbeddingModel {

    private static final String EMBED = UsageOperation.EMBEDDING_EMBED.value();
    private static final String EMBED_QUERY = UsageOperation.EMBEDDING_EMBED_QUERY.value();

    private final EmbeddingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageStatisticsService usageStatistics;

    public AuditedEmbeddingModel(EmbeddingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageStatisticsService usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<EmbeddingResponse> embed(List<String> inputs) {
        auditRecorder.recordModelInvocation(context, EMBED);
        return record(EMBED, null, () -> delegate.embed(inputs));
    }

    @Override
    public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
        auditRecorder.recordModelInvocation(context, EMBED);
        return record(EMBED, request.getMetadata(), () -> delegate.embed(request));
    }

    @Override
    public Mono<float[]> embedQuery(String text) {
        auditRecorder.recordModelInvocation(context, EMBED_QUERY);
        var descriptor = usageStatistics.describeCall(context, EMBED_QUERY, false, null);
        return UsageCallRecorder.record(usageStatistics, descriptor,
            () -> delegate.embedQuery(text), 1, (session, result) -> session.succeed(
                NormalizedUsage.missing(), null, result == null ? 0 : 1));
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return delegate.maxEmbeddingsPerCall();
    }

    @Override
    public boolean supportsParallelCalls() {
        return delegate.supportsParallelCalls();
    }

    private Mono<EmbeddingResponse> record(String operation, java.util.Map<String, Object> metadata,
        java.util.function.Supplier<Mono<EmbeddingResponse>> invocation) {
        var descriptor = usageStatistics.describeCall(context, operation, false, metadata);
        return UsageCallRecorder.record(usageStatistics, descriptor, invocation, 1,
            AuditedEmbeddingModel::succeed);
    }

    private static void succeed(UsageCallSession session, EmbeddingResponse response) {
        if (response == null) {
            session.succeed(NormalizedUsage.missing(), null, 0);
            return;
        }
        var responseModel = response.getResponse() == null
            ? null : response.getResponse().getModel();
        session.succeed(NormalizedUsage.from(response.getUsage()), responseModel, 1);
    }
}
