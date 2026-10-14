package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageOperation;

public class AuditedRerankingModel implements RerankingModel {

    private final RerankingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageObservation usageStatistics;

    public AuditedRerankingModel(RerankingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageObservation usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<RerankResponse> rerank(String query, List<String> documents) {
        auditRecorder.recordModelInvocation(context, UsageOperation.RERANK);
        return UsageCallRecorder.record(usageStatistics, context, UsageOperation.RERANK, false,
            null, () -> delegate.rerank(query, documents), 1, AuditedRerankingModel::succeed);
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        auditRecorder.recordModelInvocation(context, UsageOperation.RERANK);
        return UsageCallRecorder.record(usageStatistics, context, UsageOperation.RERANK, false,
            request == null ? null : request.getMetadata(), () -> delegate.rerank(request), 1,
            AuditedRerankingModel::succeed);
    }

    private static void succeed(UsageCallSession session, RerankResponse response) {
        if (response == null) {
            session.succeed(null, 0);
            return;
        }
        var responseModel = response.getResponse() == null
            ? null : response.getResponse().getModel();
        session.succeed(responseModel, 1);
    }
}
