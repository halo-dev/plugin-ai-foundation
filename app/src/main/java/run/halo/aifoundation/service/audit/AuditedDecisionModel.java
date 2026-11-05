package run.halo.aifoundation.service.audit;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.DecisionCapability;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageOperation;

@RequiredArgsConstructor
public class AuditedDecisionModel implements DecisionModel {
    private final DecisionModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageObservation usageStatistics;

    @Override
    public DecisionCapability capabilities() {
        return delegate.capabilities();
    }

    @Override
    public Mono<DecisionResponse> decide(DecisionRequest request) {
        auditRecorder.recordModelInvocation(context, UsageOperation.DECISION);
        return UsageCallRecorder.record(usageStatistics, context, UsageOperation.DECISION, false,
            request == null ? null : request.getMetadata(), () -> delegate.decide(request), 1,
            (session, response) -> session.succeed(response.getModel(), 1));
    }
}
