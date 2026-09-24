package run.halo.aifoundation.service.audit;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.service.observation.NormalizedUsage;
import run.halo.aifoundation.service.observation.UsageCallDescriptor;
import run.halo.aifoundation.service.observation.UsageCallSession;
import run.halo.aifoundation.service.observation.UsageObservation;
import run.halo.aifoundation.service.observation.UsageOperation;
import run.halo.aifoundation.service.observation.UsageTelemetry;

public class AuditedLanguageModel implements LanguageModel {

    private static final String GENERATE_TEXT = UsageOperation.LANGUAGE_GENERATE_TEXT.value();
    private static final String STREAM_TEXT = UsageOperation.LANGUAGE_STREAM_TEXT.value();

    private final LanguageModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;
    private final UsageObservation usageStatistics;

    public AuditedLanguageModel(LanguageModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder, UsageObservation usageStatistics) {
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
        return recordMono(GENERATE_TEXT, request == null ? null : request.getMetadata(),
            () -> LanguageModelMiddlewares.applyRequestMiddleware(delegate, request));
    }

    @Override
    public StreamTextResult streamText(GenerateTextRequest request) {
        auditRecorder.recordModelInvocation(context, STREAM_TEXT);
        var result = LanguageModelMiddlewares.applyRequestStreamMiddleware(delegate, request);
        var descriptor = run.halo.aifoundation.service.observation.UsageTelemetry.safely(
            () -> usageStatistics.describeCall(context, STREAM_TEXT, true,
            request == null ? null : request.getMetadata()), null);
        var lazy = new LazySession(usageStatistics, descriptor);
        var outputType = request.getOutput() == null ? null : request.getOutput().getType();
        var partialOutput = result.partialOutputStream();
        var elements = result.elementStream();
        var output = result.output();
        if (outputType != null) {
            switch (outputType) {
                case OBJECT, JSON -> {
                    partialOutput = recordFlux(partialOutput, lazy);
                    output = recordProjection(output, lazy);
                }
                case ARRAY -> {
                    elements = recordFlux(elements, lazy);
                    output = recordProjection(output, lazy);
                }
                case TEXT -> { }
                default -> output = recordProjection(output, lazy);
            }
        }
        return new StreamTextResult(
            recordFlux(result.fullStream(), lazy),
            recordFlux(result.textStream(), lazy),
            partialOutput,
            elements,
            output,
            recordResult(result.result(), lazy)
        );
    }

    @Override
    public LanguageModelCapabilities capabilities() {
        return delegate.capabilities();
    }

    private Mono<GenerateTextResult> recordMono(String operation, Map<String, Object> metadata,
        Supplier<Mono<GenerateTextResult>> invocation) {
        var descriptor = run.halo.aifoundation.service.observation.UsageTelemetry.safely(
            () -> usageStatistics.describeCall(context, operation, false, metadata), null);
        return UsageCallRecorder.record(usageStatistics, descriptor, invocation, 0,
            AuditedLanguageModel::succeed);
    }

    private static Mono<GenerateTextResult> recordResult(Mono<GenerateTextResult> source,
        LazySession lazy) {
        return Mono.defer(() -> {
            var session = UsageTelemetry.safely(lazy::start, null);
            if (session == null) {
                return source;
            }
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
            var session = UsageTelemetry.safely(lazy::start, null);
            if (session == null) {
                return source;
            }
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
            var session = UsageTelemetry.safely(lazy::start, null);
            if (session == null) {
                return source;
            }
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
        session.succeedProjection();
    }

    private static final class LazySession {
        private final UsageObservation service;
        private final UsageCallDescriptor descriptor;
        private final AtomicInteger subscribers = new AtomicInteger();
        private UsageCallSession session;

        private LazySession(UsageObservation service, UsageCallDescriptor descriptor) {
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
            if (signal != SignalType.CANCEL) {
                return;
            }
            if (remaining != 0) {
                return;
            }
            UsageTelemetry.safely(current::cancel);
        }
    }
}
