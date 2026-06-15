package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.embedding.EmbeddingModel;
import run.halo.aifoundation.embedding.EmbeddingRequest;
import run.halo.aifoundation.embedding.EmbeddingResponse;

public class AuditedEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;

    public AuditedEmbeddingModel(EmbeddingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
    }

    @Override
    public Mono<EmbeddingResponse> embed(List<String> inputs) {
        auditRecorder.recordModelInvocation(context, "embedding.embed");
        return delegate.embed(inputs);
    }

    @Override
    public Mono<EmbeddingResponse> embed(EmbeddingRequest request) {
        auditRecorder.recordModelInvocation(context, "embedding.embed");
        return delegate.embed(request);
    }

    @Override
    public Mono<float[]> embedQuery(String text) {
        auditRecorder.recordModelInvocation(context, "embedding.embedQuery");
        return delegate.embedQuery(text);
    }

    @Override
    public int maxEmbeddingsPerCall() {
        return delegate.maxEmbeddingsPerCall();
    }

    @Override
    public boolean supportsParallelCalls() {
        return delegate.supportsParallelCalls();
    }
}
