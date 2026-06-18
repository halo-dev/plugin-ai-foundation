package run.halo.aifoundation.service.audit;

import java.util.Objects;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;

public class AuditedLanguageModel implements LanguageModel {

    private final LanguageModel delegate;
    private final ModelCallContext context;
    private final CallerPluginAuditRecorder auditRecorder;

    public AuditedLanguageModel(LanguageModel delegate, ModelCallContext context,
        CallerPluginAuditRecorder auditRecorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.auditRecorder = Objects.requireNonNull(auditRecorder,
            "auditRecorder must not be null");
    }

    @Override
    public Mono<GenerateTextResult> generateText(String prompt) {
        auditRecorder.recordModelInvocation(context, "language.generateText");
        return delegate.generateText(prompt);
    }

    @Override
    public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
        auditRecorder.recordModelInvocation(context, "language.generateText");
        return LanguageModelMiddlewares.applyRequestMiddleware(delegate, request);
    }

    @Override
    public StreamTextResult streamText(GenerateTextRequest request) {
        auditRecorder.recordModelInvocation(context, "language.streamText");
        return LanguageModelMiddlewares.applyRequestStreamMiddleware(delegate, request);
    }

    @Override
    public LanguageModelCapabilities capabilities() {
        return delegate.capabilities();
    }
}
