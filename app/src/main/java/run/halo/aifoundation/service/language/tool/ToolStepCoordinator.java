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

    public Mono<ToolStepResolution> resolve(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, LanguageModelToolExecutor.ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory, boolean toolExecutionAllowed) {
        if (!toolExecutionAllowed) {
            var execution = toolExecutor.stepLimitReached(toolCalls);
            return Mono.just(new ToolStepResolution(toolCalls, List.of(), execution,
                execution.warnings(), false));
        }

        return toolExecutor.evaluateApproval(toolCalls, request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, approvalIdFactory)
            .flatMap(approval -> {
                var execution = approval.approvalRequests().isEmpty()
                    && approval.errors().isEmpty()
                    && !approval.hasPendingExternalCalls()
                    ? toolExecutor.execute(approval.executableCalls(), request, stepIndex,
                    executionMessages, stepProviderMetadata, lifecycle)
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

    public record ToolStepResolution(
        List<ToolCall> toolCalls,
        List<ToolApprovalRequest> approvalRequests,
        ToolExecutionBatch execution,
        List<GenerationWarning> warnings,
        boolean canContinue
    ) {
    }
}
