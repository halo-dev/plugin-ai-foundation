package run.halo.aifoundation.service.language.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationWarning;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairContext;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolResult;

public final class LanguageModelToolExecutor {
    public static final String WARNING_EXTERNAL_TOOL_PENDING = "external-tool-pending";
    public static final String WARNING_TOOL_CALL_REPAIRED = "tool-call-repaired";
    public static final String WARNING_TOOL_CALL_REPAIR_FAILED = "tool-call-repair-failed";

    private final JsonSchemaValidator schemaValidator;
    private final CancellationChecker cancellationChecker;
    private final ToolTimeout toolTimeout;

    public LanguageModelToolExecutor(JsonSchemaValidator schemaValidator,
        CancellationChecker cancellationChecker, ToolTimeout toolTimeout) {
        this.schemaValidator = schemaValidator;
        this.cancellationChecker = cancellationChecker;
        this.toolTimeout = toolTimeout;
    }

    public Mono<ToolExecutionBatch> execute(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle) {
        if (toolCalls.isEmpty()) {
            return Mono.just(new ToolExecutionBatch(List.of(), List.of(), List.of()));
        }
        var toolsByName = toolsByName(request);
        return executeNext(toolCalls, 0, request, stepIndex, executionMessages, stepProviderMetadata,
            lifecycle, toolsByName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private Mono<ToolExecutionBatch> executeNext(List<ToolCall> toolCalls, int index,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Map<String, ToolDefinition> toolsByName, ArrayList<ToolResult> results,
        ArrayList<ToolError> errors, ArrayList<GenerationWarning> warnings) {
        if (index >= toolCalls.size() || !errors.isEmpty()) {
            return Mono.just(toolExecutionBatch(results, errors, warnings));
        }
        var toolCall = toolCalls.get(index);
        var resolvedCall = toolCall;
        try {
            var tool = toolsByName.get(toolCall.getToolName());
            if (tool == null) {
                errors.add(unknownToolError(toolCall));
                return Mono.just(toolExecutionBatch(results, errors, warnings));
            }
            if (tool.getExecutor() == null) {
                warnings.add(externalToolPendingWarning(tool));
                return Mono.just(toolExecutionBatch(results, errors, warnings));
            }
            cancellationChecker.check(request);
            return repairIfNeeded(toolCall, tool, request, stepIndex, executionMessages,
                    stepProviderMetadata)
                .flatMap(repair -> {
                    var currentCall = repair.toolCall();
                    warnings.addAll(repair.warnings());
                    if (repair.error() != null) {
                        errors.add(repair.error());
                        return Mono.just(toolExecutionBatch(results, errors, warnings));
                    }
                    return executeOne(currentCall, tool, request, stepIndex, executionMessages,
                            stepProviderMetadata, lifecycle)
                        .flatMap(outcome -> {
                            if (outcome.error() != null) {
                                errors.add(outcome.error());
                                return Mono.just(toolExecutionBatch(results, errors, warnings));
                            }
                            results.add(outcome.result());
                            return executeNext(toolCalls, index + 1, request, stepIndex,
                                executionMessages, stepProviderMetadata, lifecycle, toolsByName,
                                results, errors, warnings);
                        });
                });
        } catch (RuntimeException e) {
            errors.add(toolError(resolvedCall, e));
            return Mono.just(toolExecutionBatch(results, errors, warnings));
        }
    }

    private Mono<ToolExecutionOutcome> executeOne(ToolCall resolvedCall, ToolDefinition tool,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle) {
        var context = executionContext(resolvedCall, request, stepIndex, executionMessages,
            stepProviderMetadata);
        var started = Instant.now();
        return lifecycle.toolCallStart(stepIndex, resolvedCall, context.getProviderMetadata())
            .then(Mono.defer(() -> toolTimeout.apply(tool.getExecutor().execute(context), request)))
            .doOnNext(value -> {
                cancellationChecker.check(request);
                validateOutput(resolvedCall, tool, value);
            })
            .map(value -> ToolResult.builder()
                .toolCallId(resolvedCall.getToolCallId())
                .toolName(resolvedCall.getToolName())
                .result(value)
                .build())
            .flatMap(result -> lifecycle.toolCallFinish(stepIndex, result, null, started,
                    context.getProviderMetadata())
                .thenReturn(new ToolExecutionOutcome(result, null)))
            .onErrorResume(RuntimeException.class, e -> {
                var error = toolError(resolvedCall, e);
                return lifecycle.toolCallFinish(stepIndex, null, error, started,
                        context.getProviderMetadata())
                    .thenReturn(new ToolExecutionOutcome(null, error));
            });
    }

    public Mono<ToolApprovalBatch> evaluateApproval(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory) {
        if (toolCalls.isEmpty()) {
            return Mono.just(new ToolApprovalBatch(List.of(), List.of(), List.of(), List.of(), List.of(),
                false));
        }
        return evaluateApprovalNext(toolCalls, 0, request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, approvalIdFactory, toolsByName(request),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), false);
    }

    private Mono<ToolApprovalBatch> evaluateApprovalNext(List<ToolCall> toolCalls, int index,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory, Map<String, ToolDefinition> toolsByName,
        ArrayList<ToolCall> executable, ArrayList<ToolCall> resolvedCalls,
        ArrayList<ToolApprovalRequest> approvals, ArrayList<ToolError> errors,
        ArrayList<GenerationWarning> warnings, boolean hasPendingExternalCalls) {
        if (index >= toolCalls.size() || !approvals.isEmpty() || !errors.isEmpty()
            || hasPendingExternalCalls) {
            return Mono.just(finalizeApproval(resolvedCalls, executable, approvals, errors, warnings,
                hasPendingExternalCalls));
        }
        var toolCall = toolCalls.get(index);
        var resolvedCall = toolCall;
        try {
            var tool = toolsByName.get(toolCall.getToolName());
            if (tool == null) {
                resolvedCalls.add(toolCall);
                errors.add(unknownToolError(toolCall));
                return Mono.just(finalizeApproval(resolvedCalls, executable, approvals, errors,
                    warnings, false));
            }
            if (tool.getExecutor() == null) {
                executable.clear();
                resolvedCalls.clear();
                resolvedCalls.add(toolCall);
                warnings.add(externalToolPendingWarning(tool));
                return Mono.just(finalizeApproval(resolvedCalls, executable, approvals, errors,
                    warnings, true));
            }
            cancellationChecker.check(request);
            return repairIfNeeded(toolCall, tool, request, stepIndex, executionMessages,
                    stepProviderMetadata)
                .flatMap(repair -> handleApprovalRepair(toolCalls, index, request, stepIndex,
                    executionMessages, stepProviderMetadata, lifecycle, approvalIdFactory,
                    toolsByName, executable, resolvedCalls, approvals, errors, warnings, tool,
                    repair));
        } catch (RuntimeException e) {
            resolvedCalls.add(resolvedCall);
            errors.add(toolError(resolvedCall, e));
            return Mono.just(finalizeApproval(resolvedCalls, executable, approvals, errors, warnings,
                false));
        }
    }

    private Mono<ToolApprovalBatch> handleApprovalRepair(List<ToolCall> toolCalls, int index,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory, Map<String, ToolDefinition> toolsByName,
        ArrayList<ToolCall> executable, ArrayList<ToolCall> resolvedCalls,
        ArrayList<ToolApprovalRequest> approvals, ArrayList<ToolError> errors,
        ArrayList<GenerationWarning> warnings, ToolDefinition tool, RepairAttempt repair) {
        var resolvedCall = repair.toolCall();
        warnings.addAll(repair.warnings());
        if (repair.error() != null) {
            resolvedCalls.add(resolvedCall);
            errors.add(repair.error());
            return Mono.just(finalizeApproval(resolvedCalls, executable, approvals, errors, warnings,
                false));
        }
        resolvedCalls.add(resolvedCall);
        var context = executionContext(resolvedCall, request, stepIndex, executionMessages,
            stepProviderMetadata);
        var policy = tool.getApprovalPolicy();
        if (policy != null && policy.requiresApproval(context)) {
            var approval = ToolApprovalRequest.from(resolvedCall,
                approvalIdFactory.apply(resolvedCall), stepIndex, context.getProviderMetadata());
            return lifecycle.toolApprovalRequest(stepIndex, approval)
                .then(Mono.fromSupplier(() -> {
                    approvals.add(approval);
                    return finalizeApproval(resolvedCalls, executable, approvals, errors, warnings,
                        false);
                }));
        }
        executable.add(resolvedCall);
        return evaluateApprovalNext(toolCalls, index + 1, request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, approvalIdFactory, toolsByName, executable,
            resolvedCalls, approvals, errors, warnings, false);
    }

    public ToolExecutionBatch stepLimitReached(List<ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return new ToolExecutionBatch(List.of(), List.of(), List.of());
        }
        return new ToolExecutionBatch(List.of(), List.of(), List.of(GenerationWarning.builder()
            .code("stop-condition-reached")
            .message("Tool calls were not executed because the generation step limit was reached")
            .build()));
    }

    private ToolExecutionBatch toolExecutionBatch(ArrayList<ToolResult> results,
        ArrayList<ToolError> errors, ArrayList<GenerationWarning> warnings) {
        return new ToolExecutionBatch(List.copyOf(results), List.copyOf(errors),
            List.copyOf(warnings));
    }

    private ToolApprovalBatch finalizeApproval(ArrayList<ToolCall> resolvedCalls,
        ArrayList<ToolCall> executable, ArrayList<ToolApprovalRequest> approvals,
        ArrayList<ToolError> errors, ArrayList<GenerationWarning> warnings,
        boolean hasPendingExternalCalls) {
        if (!approvals.isEmpty()) {
            var approvalCallIds = approvals.stream()
                .map(ToolApprovalRequest::getToolCallId)
                .collect(Collectors.toCollection(HashSet::new));
            resolvedCalls.removeIf(toolCall -> !approvalCallIds.contains(toolCall.getToolCallId()));
            executable.clear();
        }
        return new ToolApprovalBatch(List.copyOf(resolvedCalls), List.copyOf(executable),
            List.copyOf(approvals), List.copyOf(errors), List.copyOf(warnings),
            hasPendingExternalCalls);
    }

    private Map<String, ToolDefinition> toolsByName(GenerateTextRequest request) {
        return request.getTools() == null ? Map.of()
            : request.getTools().stream()
                .collect(Collectors.toMap(ToolDefinition::getName, Function.identity()));
    }

    private ToolExecutionContext executionContext(ToolCall toolCall, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata) {
        return ToolExecutionContext.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .stepIndex(stepIndex)
            .messages(List.copyOf(executionMessages))
            .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                toolCall.getProviderMetadata()))
            .cancellationToken(request.getCancellationToken())
            .build();
    }

    private ToolError unknownToolError(ToolCall toolCall) {
        return ToolError.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .errorText("Unknown tool: " + toolCall.getToolName())
            .build();
    }

    private ToolError toolError(ToolCall toolCall, RuntimeException e) {
        return ToolError.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .errorText(safeErrorMessage(e))
            .build();
    }

    private void validateInput(ToolCall toolCall, ToolDefinition tool) {
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            schemaValidator.validate(toolCall.getInput(), tool.getInputSchema(),
                validationPath(toolCall));
        }
    }

    private void validateOutput(ToolCall toolCall, ToolDefinition tool, Object value) {
        if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
            schemaValidator.validate(value, tool.getOutputSchema(),
                "$." + toolCall.getToolName() + ".output");
        }
    }

    private Mono<RepairAttempt> repairIfNeeded(ToolCall toolCall, ToolDefinition tool,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata) {
        try {
            validateInput(toolCall, tool);
            return Mono.just(new RepairAttempt(toolCall, null, List.of()));
        } catch (RuntimeException validationFailure) {
            var repairCallback = request.getToolCallRepair();
            if (repairCallback == null) {
                return Mono.just(new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
                    List.of()));
            }
            return repairCallback.repair(ToolCallRepairContext.builder()
                    .toolCall(toolCall)
                    .tool(tool)
                    .validationError(safeErrorMessage(validationFailure))
                    .validationPath(validationPath(toolCall))
                    .stepIndex(stepIndex)
                    .messages(List.copyOf(executionMessages))
                    .requestContext(copyContext(request.getContext()))
                    .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                        toolCall.getProviderMetadata()))
                    .build())
                .map(result -> repairedAttempt(toolCall, tool, validationFailure, result))
                .onErrorResume(RuntimeException.class, repairFailure ->
                    Mono.just(failedRepairAttempt(toolCall, validationFailure, repairFailure)));
        }
    }

    private RepairAttempt repairedAttempt(ToolCall toolCall, ToolDefinition tool,
        RuntimeException validationFailure,
        run.halo.aifoundation.tool.ToolCallRepairResult result) {
        var warnings = new ArrayList<GenerationWarning>();
        var repaired = result != null ? result.getToolCall() : null;
        if (repaired == null) {
            warnings.add(repairFailedWarning(toolCall, validationFailure));
            return new RepairAttempt(toolCall, toolError(toolCall, validationFailure), warnings);
        }
        try {
            var normalized = normalizeRepairedCall(toolCall, repaired);
            validateInput(normalized, tool);
            warnings.add(repairedWarning(toolCall, normalized));
            return new RepairAttempt(normalized, null, warnings);
        } catch (RuntimeException repairFailure) {
            return failedRepairAttempt(toolCall, validationFailure, repairFailure);
        }
    }

    private RepairAttempt failedRepairAttempt(ToolCall toolCall, RuntimeException validationFailure,
        Throwable repairFailure) {
        return new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
            List.of(repairFailedWarning(toolCall, repairFailure)));
    }

    private Map<String, Object> copyContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    private ToolCall normalizeRepairedCall(ToolCall original, ToolCall repaired) {
        return ToolCall.builder()
            .toolCallId(original.getToolCallId())
            .toolName(original.getToolName())
            .input(repaired.getInput() != null ? repaired.getInput() : Map.of())
            .rawInput(repaired.getRawInput() != null ? repaired.getRawInput() : original.getRawInput())
            .providerMetadata(mergeProviderMetadata(original.getProviderMetadata(),
                repaired.getProviderMetadata()))
            .build();
    }

    private GenerationWarning repairedWarning(ToolCall original, ToolCall repaired) {
        return GenerationWarning.builder()
            .code(WARNING_TOOL_CALL_REPAIRED)
            .message("Tool call input was repaired before execution: " + original.getToolName())
            .providerMetadata(Map.of(
                "toolCallId", repaired.getToolCallId(),
                "toolName", repaired.getToolName()
            ))
            .build();
    }

    private GenerationWarning repairFailedWarning(ToolCall toolCall, Throwable e) {
        return GenerationWarning.builder()
            .code(WARNING_TOOL_CALL_REPAIR_FAILED)
            .message("Tool call input repair failed: " + safeErrorMessage(e))
            .providerMetadata(Map.of(
                "toolCallId", toolCall.getToolCallId(),
                "toolName", toolCall.getToolName()
            ))
            .build();
    }

    private String validationPath(ToolCall toolCall) {
        return "$." + toolCall.getToolName() + ".input";
    }

    private GenerationWarning externalToolPendingWarning(ToolDefinition tool) {
        return GenerationWarning.builder()
            .code(WARNING_EXTERNAL_TOOL_PENDING)
            .message("Tool is pending external execution: " + tool.getName())
            .build();
    }

    private String safeErrorMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private Map<String, Object> mergeProviderMetadata(Map<String, Object> left,
        Map<String, Object> right) {
        var merged = new LinkedHashMap<String, Object>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return merged;
    }

    @FunctionalInterface
    public interface JsonSchemaValidator {
        void validate(Object value, Map<String, Object> schema, String path);
    }

    @FunctionalInterface
    public interface CancellationChecker {
        void check(GenerateTextRequest request);
    }

    @FunctionalInterface
    public interface ToolTimeout {
        Mono<Object> apply(Mono<Object> mono, GenerateTextRequest request);
    }

    public interface ToolLifecycle {
        Mono<Void> toolCallStart(int stepIndex, ToolCall toolCall, Map<String, Object> metadata);

        Mono<Void> toolCallFinish(int stepIndex, ToolResult result, ToolError error, Instant startedAt,
            Map<String, Object> metadata);

        Mono<Void> toolApprovalRequest(int stepIndex, ToolApprovalRequest request);
    }

    private record ToolExecutionOutcome(
        ToolResult result,
        ToolError error
    ) {
    }

    private record RepairAttempt(
        ToolCall toolCall,
        ToolError error,
        List<GenerationWarning> warnings
    ) {
    }
}
