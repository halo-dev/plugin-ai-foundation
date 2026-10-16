package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponse;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageOperation;

public class AuditedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageObservation usageStatistics;

    public AuditedEmbeddingModel(EmbeddingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageObservation usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<EmbeddingResponse> embed(List<String> inputs) {
        auditRecorder.recordModelInvocation(context, UsageOperation.EMBEDDING_EMBED);
        return record(UsageOperation.EMBEDDING_EMBED, null, () -> delegate.embed(inputs),
            AuditedEmbeddingModel::succeed);
    }

    @Override
    public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
        auditRecorder.recordModelInvocation(context, UsageOperation.EMBEDDING_EMBED);
        return record(UsageOperation.EMBEDDING_EMBED,
            request == null ? null : request.getMetadata(), () -> delegate.embed(request),
            AuditedEmbeddingModel::succeed);
    }

    @Override
    public Mono<float[]> embedQuery(String text) {
        auditRecorder.recordModelInvocation(context, UsageOperation.EMBEDDING_EMBED_QUERY);
        return record(UsageOperation.EMBEDDING_EMBED_QUERY, null, () -> delegate.embedQuery(text),
            (session, result) -> session.succeed(
                null, result == null ? 0 : 1));
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return delegate.maxEmbeddingsPerCall();
    }

    @Override
    public boolean supportsParallelCalls() {
        return delegate.supportsParallelCalls();
    }

    private <T> Mono<T> record(String operation, Map<String, Object> metadata,
        Supplier<Mono<T>> invocation, BiConsumer<UsageCallSession, T> success) {
        return UsageCallRecorder.record(usageStatistics, context, operation, false, metadata,
            invocation, 1, success);
    }

    private static void succeed(UsageCallSession session, EmbeddingResponse response) {
        if (response == null) {
            session.succeed(null, 0);
            return;
        }
        var responseModel = response.getResponse() == null
            ? null : response.getResponse().getModel();
        session.succeed(responseModel, 1);
    }
}
