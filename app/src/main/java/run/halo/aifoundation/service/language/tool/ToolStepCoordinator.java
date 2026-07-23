package run.halo.aifoundation.service.language.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return normalize(request).flatMap(normalized -> resolveNormalized(request, normalized));
    }

    public Mono<ToolNormalizationBatch> normalize(ToolStepRequest request) {
        return toolExecutor.normalizeInputs(request.toolCalls(), request.generationRequest(),
            request.stepIndex(), request.executionMessages(), request.stepProviderMetadata(),
            request.lifecycle(), request.streamedInputCallIds());
    }

    public Mono<ToolStepResolution> resolveNormalized(ToolStepRequest request,
        ToolNormalizationBatch normalized) {
        if (!request.toolExecutionAllowed()) {
            var execution = withInputErrors(toolExecutor.stepLimitReached(normalized.toolCalls()),
                normalized);
            var warnings = mergeWarnings(normalized.warnings(), execution.warnings());
            return toolExecutor.notifyInputAvailable(normalized.toolCalls(),
                    request.generationRequest(), request.stepIndex(), request.executionMessages(),
                    request.stepProviderMetadata())
                .thenReturn(new ToolStepResolution(normalized.toolCalls(), List.of(), execution,
                    normalized.inputErrorCallIds(), warnings, false));
        }

        return toolExecutor.evaluateApproval(normalized.toolCalls(), request.generationRequest(),
                request.stepIndex(), request.executionMessages(), request.stepProviderMetadata(),
                request.lifecycle(), request.approvalIdFactory())
            .flatMap(approval -> executeApprovedTools(request, approval)
                .map(execution -> resolution(approval,
                    withInputErrors(execution, normalized), normalized)));
    }

    private Mono<ToolExecutionBatch> executeApprovedTools(ToolStepRequest request,
        ToolApprovalBatch approval) {
        if (!canExecuteApprovedTools(approval)) {
            return Mono.just(new ToolExecutionBatch(List.of(), approval.errors(),
                approval.warnings()));
        }
        return toolExecutor.execute(approval.executableCalls(), request.generationRequest(),
            request.stepIndex(), request.executionMessages(), request.stepProviderMetadata(),
            request.lifecycle());
    }

    private boolean canExecuteApprovedTools(ToolApprovalBatch approval) {
        return approval.approvalRequests().isEmpty()
            && approval.errors().isEmpty()
            && !approval.hasPendingExternalCalls();
    }

    private ToolExecutionBatch withInputErrors(ToolExecutionBatch execution,
        ToolNormalizationBatch normalized) {
        if (normalized.inputErrors().isEmpty()) {
            return execution;
        }
        var errors = new ArrayList<>(normalized.inputErrors());
        errors.addAll(execution.errors());
        return new ToolExecutionBatch(execution.results(), List.copyOf(errors),
            execution.warnings());
    }

    private ToolStepResolution resolution(ToolApprovalBatch approval, ToolExecutionBatch execution,
        ToolNormalizationBatch normalized) {
        var warnings = new ArrayList<GenerationWarning>();
        warnings.addAll(normalized.warnings());
        warnings.addAll(approval.warnings());
        warnings.addAll(execution.warnings().stream()
            .filter(warning -> !approval.warnings().contains(warning))
            .toList());
        var hasToolResponse = !execution.results().isEmpty()
            || !execution.errors().isEmpty();
        var canContinue = approval.approvalRequests().isEmpty()
            && !approval.hasPendingExternalCalls()
            && hasToolResponse;
        return new ToolStepResolution(approval.toolCalls(), approval.approvalRequests(),
            execution, normalized.inputErrorCallIds(), List.copyOf(warnings), canContinue);
    }

    private List<GenerationWarning> mergeWarnings(List<GenerationWarning> left,
        List<GenerationWarning> right) {
        var warnings = new ArrayList<GenerationWarning>();
        warnings.addAll(left);
        warnings.addAll(right);
        return List.copyOf(warnings);
    }

    public record ToolStepRequest(
        List<ToolCall> toolCalls,
        GenerateTextRequest generationRequest,
        int stepIndex,
        List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata,
        LanguageModelToolExecutor.ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory,
        Set<String> streamedInputCallIds,
        boolean toolExecutionAllowed
    ) {
    }

    public record ToolStepResolution(
        List<ToolCall> toolCalls,
        List<ToolApprovalRequest> approvalRequests,
        ToolExecutionBatch execution,
        Set<String> inputErrorCallIds,
        List<GenerationWarning> warnings,
        boolean canContinue
    ) {
    }
}
