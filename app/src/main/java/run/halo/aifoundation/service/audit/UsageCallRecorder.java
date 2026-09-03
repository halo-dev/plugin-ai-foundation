package run.halo.aifoundation.service.audit;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageCallDescriptor;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.usage.UsageTelemetry;

final class UsageCallRecorder {

    private UsageCallRecorder() {
    }

    static <T> Mono<T> record(UsageStatisticsService statistics, UsageCallDescriptor descriptor,
        Supplier<Mono<T>> invocation, int failureStepCount,
        BiConsumer<UsageCallSession, T> success) {
        return Mono.defer(() -> {
            var session = statistics.beginCall(descriptor);
            return Mono.defer(invocation)
                .doOnSuccess(result -> UsageTelemetry.safely(
                    () -> success.accept(session, result)))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> session.fail(error, NormalizedUsage.missing(), failureStepCount)))
                .doOnCancel(() -> UsageTelemetry.safely(session::cancel))
                .contextWrite(context ->
                    context.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));
        });
    }
}
