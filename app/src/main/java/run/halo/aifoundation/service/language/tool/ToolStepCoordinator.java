package run.halo.aifoundation.service.language.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolCall;

public final class ToolStepCoordinator {
    private final LanguageModelToolExecutor toolExecutor;

    public ToolStepCoordinator(LanguageModelToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    public Mono<ToolStepResolution> resolve(ToolStepRequest request) {
        if (!request.toolExecutionAllowed()) {
            var execution = toolExecutor.stepLimitReached(request.toolCalls());
            return Mono.just(new ToolStepResolution(request.toolCalls(), List.of(), execution,
                execution.warnings(), false));
        }

        return toolExecutor.evaluateApproval(request.toolCalls(), request.generationRequest(),
                request.stepIndex(), request.executionMessages(), request.stepProviderMetadata(),
                request.lifecycle(), request.approvalIdFactory())
            .flatMap(approval -> {
                var execution = approval.approvalRequests().isEmpty()
                    && approval.errors().isEmpty()
                    && !approval.hasPendingExternalCalls()
                    ? toolExecutor.execute(approval.executableCalls(), request.generationRequest(),
                    request.stepIndex(), request.executionMessages(), request.stepProviderMetadata(),
                    request.lifecycle())
                    : Mono.just(new ToolExecutionBatch(List.of(), approval.errors(),
                        approval.warnings()));
                return execution.map(batch -> resolution(approval, batch));
            });
    }

    private ToolStepResolution resolution(ToolApprovalBatch approval, ToolExecutionBatch execution) {
        var warnings = new ArrayList<GenerationWarning>();
        warnings.addAll(approval.warnings());
        warnings.addAll(execution.warnings().stream()
            .filter(warning -> !approval.warnings().contains(warning))
            .toList());
        var canContinue = !approval.toolCalls().isEmpty()
            && approval.approvalRequests().isEmpty()
            && !approval.hasPendingExternalCalls()
            && execution.errors().isEmpty()
            && !execution.results().isEmpty();
        return new ToolStepResolution(approval.toolCalls(), approval.approvalRequests(),
            execution, List.copyOf(warnings), canContinue);
    }

    public record ToolStepRequest(
        List<ToolCall> toolCalls,
        GenerateTextRequest generationRequest,
        int stepIndex,
        List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata,
        LanguageModelToolExecutor.ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory,
        boolean toolExecutionAllowed
    ) {
    }

    public record ToolStepResolution(
        List<ToolCall> toolCalls,
        List<ToolApprovalRequest> approvalRequests,
        ToolExecutionBatch execution,
        List<GenerationWarning> warnings,
        boolean canContinue
    ) {
    }
}
