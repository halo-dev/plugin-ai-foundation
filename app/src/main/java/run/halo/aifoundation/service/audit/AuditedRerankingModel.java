package run.halo.aifoundation.service.audit;

import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.rerank.RerankRequest;
import run.halo.aifoundation.rerank.RerankResponse;
import run.halo.aifoundation.rerank.RerankingModel;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageOperation;
import run.halo.aifoundation.service.usage.UsageStatisticsService;

public class AuditedRerankingModel implements RerankingModel {

    private static final String OPERATION = UsageOperation.RERANK.value();

    private final RerankingModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageStatisticsService usageStatistics;

    public AuditedRerankingModel(RerankingModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageStatisticsService usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<RerankResponse> rerank(String query, List<String> documents) {
        auditRecorder.recordModelInvocation(context, OPERATION);
        return record(null, () -> delegate.rerank(query, documents));
    }

    @Override
    public Mono<RerankResponse> rerank(RerankRequest request) {
        auditRecorder.recordModelInvocation(context, OPERATION);
        return record(request.getMetadata(), () -> delegate.rerank(request));
    }

    private Mono<RerankResponse> record(java.util.Map<String, Object> metadata,
        java.util.function.Supplier<Mono<RerankResponse>> invocation) {
        var descriptor = usageStatistics.describeCall(context, OPERATION, false, metadata);
        return UsageCallRecorder.record(usageStatistics, descriptor, invocation, 1,
            AuditedRerankingModel::succeed);
    }

    private static void succeed(UsageCallSession session, RerankResponse response) {
        if (response == null) {
            session.succeed(NormalizedUsage.missing(), null, 0);
            return;
        }
        var responseModel = response.getResponse() == null
            ? null : response.getResponse().getModel();
        session.succeed(NormalizedUsage.from(response.getUsage()), responseModel, 1);
    }
}
