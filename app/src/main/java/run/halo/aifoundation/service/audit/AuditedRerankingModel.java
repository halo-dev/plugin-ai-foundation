package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankingModel;

public class AuditedRerankingModel implements RerankingModel {

    private final RerankingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;

    public AuditedRerankingModel(RerankingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
    }

    @Override
    public Mono<RerankResponse> rerank(String query, List<String> documents) {
        auditRecorder.recordModelInvocation(context, "rerank.rerank");
        return delegate.rerank(query, documents);
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        auditRecorder.recordModelInvocation(context, "rerank.rerank");
        return delegate.rerank(request);
    }
}
