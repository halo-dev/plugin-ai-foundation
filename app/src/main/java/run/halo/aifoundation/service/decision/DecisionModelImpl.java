package run.halo.aifoundation.service.decision;

import java.util.List;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.DecisionCapability;
import run.halo.aifoundation.decision.DecisionModel;
import run.halo.aifoundation.decision.DecisionRequest;
import run.halo.aifoundation.decision.DecisionResponse;
import run.halo.aifoundation.exception.DecisionCancelledException;
import run.halo.aifoundation.exception.DecisionTimeoutException;
import run.halo.aifoundation.provider.support.ProviderDecisionClient;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageExecutionObserver;
import run.halo.aifoundation.service.observation.UsageUnitKind;

public class DecisionModelImpl implements DecisionModel {
    private final ProviderDecisionClient client;
    private final UsageExecutionObserver observer;
    private final DecisionCapability capabilities;

    public DecisionModelImpl(ProviderDecisionClient client, UsageExecutionObserver observer,
        DecisionCapability capabilities) {
        this.client = client;
        this.observer = observer;
        this.capabilities = copyCapabilities(capabilities);
    }

    @Override
    public DecisionCapability capabilities() {
        return copyCapabilities(capabilities);
    }

    private static DecisionCapability copyCapabilities(DecisionCapability capabilities) {
        return capabilities == null ? null : new DecisionCapability(
            capabilities.getQuestionTypes() == null ? null
                : List.copyOf(capabilities.getQuestionTypes()),
            capabilities.getStructuredState(), capabilities.getMaxChoiceOptions(),
            capabilities.getMaxScoreLevels(), capabilities.getChoiceProbabilities(),
            capabilities.getChoiceConfidence(), capabilities.getScoreProbabilities(),
            capabilities.getScoreConfidence(), capabilities.getScoreLegend());
    }

    @Override
    public Mono<DecisionResponse> decide(DecisionRequest request) {
        return Mono.defer(() -> {
            DecisionValidation.request(request);
            DecisionValidation.capabilities(request, capabilities);
            checkCancellation(request);
            var call = observer.observe(UsageUnitKind.DECISION, 0,
                    () -> client.decide(request)
                        .switchIfEmpty(Mono.error(new IllegalStateException("Empty decision response")))
                        .doOnNext(response -> DecisionValidation.response(request, response)),
                    response -> NormalizedUsage.from(response.getUsage()), DecisionResponse::getModel)
                .doOnNext(response -> checkCancellation(request));
            var timeout = request.getTimeouts() == null ? null
                : request.getTimeouts().getTotalTimeout();
            return timeout == null ? call : call.timeout(timeout)
                .onErrorMap(TimeoutException.class, error -> new DecisionTimeoutException(timeout, error));
        });
    }

    private static void checkCancellation(DecisionRequest request) {
        if (request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new DecisionCancelledException();
        }
    }
}
