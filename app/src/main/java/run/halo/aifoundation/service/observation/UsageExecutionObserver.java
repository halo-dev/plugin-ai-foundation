package run.halo.aifoundation.service.observation;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class UsageExecutionObserver {

    public <T> Mono<T> observe(UsageUnitKind kind, int unitIndex, Supplier<Mono<T>> invocation,
        Function<T, NormalizedUsage> usage, Function<T, String> responseModel) {
        return Mono.deferContextual(context -> {
            var session = UsageCallSession.from(context);
            if (session == null) {
                return invocation.get();
            }
            var scope = UsageTelemetry.safely(() -> session.beginExecution(kind, unitIndex), null);
            if (scope == null) {
                return invocation.get();
            }
            return Mono.defer(invocation)
                .doOnSuccess(value -> UsageTelemetry.safely(() -> {
                    if (value == null) {
                        scope.succeed(NormalizedUsage.missing(), null);
                        return;
                    }
                    scope.succeed(UsageTelemetry.safely(() -> usage.apply(value), NormalizedUsage.missing()),
                        UsageTelemetry.safely(() -> responseModel.apply(value), null));
                }))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> scope.fail(error, NormalizedUsage.fromFailure(error), null)))
                .doOnCancel(() -> UsageTelemetry.safely(
                    () -> scope.cancel(NormalizedUsage.missing(), null)));
        });
    }

    public <T> Flux<T> observeFlux(UsageUnitKind kind, int unitIndex,
        Supplier<Flux<T>> invocation, Function<T, NormalizedUsage> usage,
        Function<T, String> responseModel) {
        return Flux.deferContextual(context -> {
            var session = UsageCallSession.from(context);
            if (session == null) {
                return invocation.get();
            }
            var scope = UsageTelemetry.safely(() -> session.beginExecution(kind, unitIndex), null);
            if (scope == null) {
                return invocation.get();
            }
            var lastUsage = new AtomicReference<>(NormalizedUsage.missing());
            var lastModel = new AtomicReference<String>();
            var extractionFailed = new java.util.concurrent.atomic.AtomicBoolean();
            return Flux.defer(invocation)
                .doOnNext(value -> UsageTelemetry.safely(() -> {
                    var observedUsage = UsageTelemetry.safely(() -> usage.apply(value), null);
                    if (observedUsage == null) {
                        extractionFailed.set(true);
                    }
                    if (!NormalizedUsage.isMissing(observedUsage)) {
                        lastUsage.set(observedUsage);
                    }
                    var observedModel = responseModel.apply(value);
                    if (StringUtils.hasText(observedModel)) {
                        lastModel.set(observedModel);
                    }
                }))
                .doOnComplete(() -> UsageTelemetry.safely(
                    () -> scope.succeed(observedUsage(lastUsage.get(), extractionFailed.get()), lastModel.get())))
                .doOnError(error -> UsageTelemetry.safely(() -> {
                    var failureUsage = NormalizedUsage.fromFailure(error);
                    // An exception can carry the final snapshot even if no finish chunk arrived.
                    // Prefer it over earlier cumulative snapshots; never add the two.
                    if (NormalizedUsage.isMissing(failureUsage)) {
                        failureUsage = observedUsage(lastUsage.get(), extractionFailed.get());
                    }
                    scope.fail(error, failureUsage, lastModel.get());
                }))
                .doOnCancel(() -> UsageTelemetry.safely(() ->
                    scope.cancel(observedUsage(lastUsage.get(), extractionFailed.get()), lastModel.get())));
        });
    }

    private static NormalizedUsage observedUsage(NormalizedUsage usage, boolean extractionFailed) {
        if (!extractionFailed) {
            return usage;
        }
        if (NormalizedUsage.isMissing(usage)) {
            return usage;
        }
        return new NormalizedUsage(usage.inputTokens(), usage.outputTokens(),
            usage.cacheReadInputTokens(), usage.cacheCreationInputTokens(),
            usage.reasoningOutputTokens(), usage.providerTotalTokens(),
            usage.accountedTotalTokens(), UsageQuality.PARTIAL);
    }

}
