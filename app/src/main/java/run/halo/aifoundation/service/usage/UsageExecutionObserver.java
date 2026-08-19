package run.halo.aifoundation.service.usage;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

@Component
public class UsageExecutionObserver {

    private static final String TIMEOUT_DEADLINE_CONTEXT_KEY =
        UsageExecutionObserver.class.getName() + ".timeoutDeadline";

    public static Context withTimeoutDeadline(Context context, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return context;
        }
        return context.put(TIMEOUT_DEADLINE_CONTEXT_KEY,
            System.nanoTime() + timeout.toNanos());
    }

    public <T> Mono<T> observe(UsageUnitKind kind, int unitIndex, Supplier<Mono<T>> invocation,
        Function<T, NormalizedUsage> usage, Function<T, String> responseModel) {
        return Mono.deferContextual(context -> {
            var session = UsageCallSession.from(context);
            if (session == null) {
                return invocation.get();
            }
            var scope = session.beginExecution(kind, unitIndex);
            return Mono.defer(invocation)
                .doOnSuccess(value -> UsageTelemetry.safely(() -> {
                    if (value == null) {
                        scope.succeed(NormalizedUsage.missing(), null);
                        return;
                    }
                    scope.succeed(usage.apply(value), responseModel.apply(value));
                }))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> scope.fail(error, NormalizedUsage.fromFailure(error), null)))
                .doOnCancel(() -> UsageTelemetry.safely(
                    () -> cancelOrTimeout(scope, context, NormalizedUsage.missing(), null)));
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
            var scope = session.beginExecution(kind, unitIndex);
            var lastUsage = new AtomicReference<>(NormalizedUsage.missing());
            var lastModel = new AtomicReference<String>();
            return Flux.defer(invocation)
                .doOnNext(value -> UsageTelemetry.safely(() -> {
                    var observedUsage = usage.apply(value);
                    if (observedUsage != null && observedUsage.quality() != UsageQuality.MISSING) {
                        lastUsage.set(observedUsage);
                    }
                    var observedModel = responseModel.apply(value);
                    if (observedModel != null && !observedModel.isBlank()) {
                        lastModel.set(observedModel);
                    }
                }))
                .doOnComplete(() -> UsageTelemetry.safely(
                    () -> scope.succeed(lastUsage.get(), lastModel.get())))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> scope.fail(error, lastUsage.get(), lastModel.get())))
                .doOnCancel(() -> UsageTelemetry.safely(() ->
                    cancelOrTimeout(scope, context, lastUsage.get(), lastModel.get())));
        });
    }

    private static void cancelOrTimeout(UsageExecutionScope scope, ContextView context,
        NormalizedUsage usage, String responseModelId) {
        var deadline = context.getOrDefault(TIMEOUT_DEADLINE_CONTEXT_KEY, Long.MAX_VALUE);
        if (System.nanoTime() >= deadline) {
            scope.fail(new TimeoutException("The logical call reached its total timeout"), usage,
                responseModelId);
            return;
        }
        scope.cancel(usage, responseModelId);
    }
}
