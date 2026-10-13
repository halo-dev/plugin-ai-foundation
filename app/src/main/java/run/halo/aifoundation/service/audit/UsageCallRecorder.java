package run.halo.aifoundation.service.audit;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageTelemetry;

final class UsageCallRecorder {

    private UsageCallRecorder() {
    }

    static <T> Mono<T> record(UsageObservation statistics, ModelCallContext context,
        String operation, boolean streaming, Map<String, Object> metadata,
        Supplier<Mono<T>> invocation, int failureStepCount,
        BiConsumer<UsageCallSession, T> success) {
        // Describe eagerly at assembly time so the caller snapshot reflects the invoking plugin.
        var descriptor = UsageTelemetry.safely(
            () -> statistics.describeCall(context, operation, streaming, metadata), null);
        if (descriptor == null) {
            return Mono.defer(invocation);
        }
        return Mono.defer(() -> {
            var session = UsageTelemetry.safely(() -> statistics.beginCall(descriptor), null);
            if (session == null) {
                return Mono.defer(invocation);
            }
            return Mono.defer(invocation)
                .doOnSuccess(result -> UsageTelemetry.safely(
                    () -> success.accept(session, result)))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> session.fail(error, NormalizedUsage.missing(), failureStepCount)))
                .doOnCancel(() -> UsageTelemetry.safely(session::cancel))
                .contextWrite(contextView ->
                    contextView.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));
        });
    }
}
