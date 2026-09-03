package run.halo.aifoundation.service.audit;

import java.util.Objects;
import java.util.Map;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.service.usage.NormalizedUsage;
import run.halo.aifoundation.service.usage.UsageCallDescriptor;
import run.halo.aifoundation.service.usage.UsageCallSession;
import run.halo.aifoundation.service.usage.UsageOperation;
import run.halo.aifoundation.service.usage.UsageStatisticsService;
import run.halo.aifoundation.service.usage.UsageTelemetry;
import run.halo.aifoundation.schema.OutputType;

public class AuditedLanguageModel implements LanguageModel {

    private static final String GENERATE_TEXT = UsageOperation.LANGUAGE_GENERATE_TEXT.value();
    private static final String STREAM_TEXT = UsageOperation.LANGUAGE_STREAM_TEXT.value();

    private final LanguageModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageStatisticsService usageStatistics;

    public AuditedLanguageModel(LanguageModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageStatisticsService usageStatistics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
        this.usageStatistics = Objects.requireNonNull(usageStatistics,
            "usageStatistics must not be null");
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        auditRecorder.recordModelInvocation(context, GENERATE_TEXT);
        return recordMono(GENERATE_TEXT, null, () -> delegate.generateText(prompt));
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        auditRecorder.recordModelInvocation(context, GENERATE_TEXT);
        return recordMono(GENERATE_TEXT, request.getMetadata(),
            () -> LanguageModelMiddlewares.applyRequestMiddleware(delegate, request));
    }

    @Override
    public StreamTextResult streamText(GenerateTextRequest request) {
        auditRecorder.recordModelInvocation(context, STREAM_TEXT);
        var result = LanguageModelMiddlewares.applyRequestStreamMiddleware(delegate, request);
        var descriptor = usageStatistics.describeCall(context, STREAM_TEXT, true,
            request.getMetadata());
        var lazy = new LazySession(usageStatistics, descriptor);
        var outputType = request.getOutput() == null ? null : request.getOutput().getType();
        var partialOutput = outputType == OutputType.OBJECT || outputType == OutputType.JSON
            ? recordFlux(result.partialOutputStream(), lazy) : result.partialOutputStream();
        var elements = outputType == OutputType.ARRAY
            ? recordFlux(result.elementStream(), lazy) : result.elementStream();
        return new StreamTextResult(
            recordFlux(result.fullStream(), lazy),
            recordFlux(result.textStream(), lazy),
            partialOutput,
            elements,
            recordProjection(result.output(), lazy),
            recordResult(result.result(), lazy)
        );
    }

    @Override
    public LanguageModelCapabilities capabilities() {
        return delegate.capabilities();
    }

    private Mono<GenerateTextResult> recordMono(String operation, Map<String, Object> metadata,
        Supplier<Mono<GenerateTextResult>> invocation) {
        var descriptor = usageStatistics.describeCall(context, operation, false, metadata);
        return UsageCallRecorder.record(usageStatistics, descriptor, invocation, 0,
            AuditedLanguageModel::succeed);
    }

    private static Mono<GenerateTextResult> recordResult(Mono<GenerateTextResult> source,
        LazySession lazy) {
        return Mono.defer(() -> {
            var session = lazy.start();
            return source.doOnSuccess(result ->
                    UsageTelemetry.safely(() -> succeed(session, result)))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> session.fail(error, NormalizedUsage.missing(), 0)))
                .doFinally(signal -> lazy.finish(session, signal))
                .contextWrite(value -> value.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));
        });
    }

    private static <T> Mono<T> recordProjection(Mono<T> source, LazySession lazy) {
        return Mono.defer(() -> {
            var session = lazy.start();
            return source.doOnSuccess(ignored -> UsageTelemetry.safely(
                    () -> succeedProjection(session)))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> session.fail(error, NormalizedUsage.missing(), 0)))
                .doFinally(signal -> lazy.finish(session, signal))
                .contextWrite(value -> value.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));
        });
    }

    private static <T> Flux<T> recordFlux(Flux<T> source, LazySession lazy) {
        return Flux.defer(() -> {
            var session = lazy.start();
            return source.doOnComplete(() -> UsageTelemetry.safely(
                    () -> succeedProjection(session)))
                .doOnError(error -> UsageTelemetry.safely(
                    () -> session.fail(error, NormalizedUsage.missing(), 0)))
                .doFinally(signal -> lazy.finish(session, signal))
                .contextWrite(value -> value.put(UsageCallSession.REACTOR_CONTEXT_KEY, session));
        });
    }

    private static void succeed(UsageCallSession session, GenerateTextResult result) {
        if (result == null) {
            session.succeed(NormalizedUsage.missing(), null, 0);
            return;
        }
        var responseModel = result.getResponse() == null ? null : result.getResponse().getModel();
        var steps = result.getSteps() == null ? 1 : result.getSteps().size();
        session.succeed(NormalizedUsage.from(result.getTotalUsage()), responseModel, steps);
    }

    private static void succeedProjection(UsageCallSession session) {
        if (session.hasExecutions()) {
            session.succeed(NormalizedUsage.missing(), null, 0);
        }
    }

    private static final class LazySession {
        private final UsageStatisticsService service;
        private final UsageCallDescriptor descriptor;
        private final AtomicInteger subscribers = new AtomicInteger();
        private UsageCallSession session;

        private LazySession(UsageStatisticsService service, UsageCallDescriptor descriptor) {
            this.service = service;
            this.descriptor = descriptor;
        }

        private synchronized UsageCallSession start() {
            if (session == null) {
                session = service.beginCall(descriptor);
            }
            subscribers.incrementAndGet();
            return session;
        }

        private void finish(UsageCallSession current, SignalType signal) {
            var remaining = subscribers.decrementAndGet();
            if (signal == SignalType.CANCEL && remaining == 0) {
                UsageTelemetry.safely(current::cancel);
            }
        }
    }
}
