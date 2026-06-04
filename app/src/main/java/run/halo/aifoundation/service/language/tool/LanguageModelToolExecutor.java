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
        var context = ToolStepContext.execution(request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, toolsByName(request));
        return executeNext(toolCalls, 0, context, new ExecutionAccumulator());
    }

    private Mono<ToolExecutionBatch> executeNext(List<ToolCall> toolCalls, int index,
        ToolStepContext context, ExecutionAccumulator accumulator) {
        if (index >= toolCalls.size() || accumulator.hasErrors()) {
            return Mono.just(accumulator.toBatch());
        }
        var toolCall = toolCalls.get(index);
        var resolvedCall = toolCall;
        try {
            var tool = context.tool(toolCall);
            if (tool == null) {
                accumulator.addError(unknownToolError(toolCall));
                return Mono.just(accumulator.toBatch());
            }
            if (tool.getExecutor() == null) {
                accumulator.addWarning(externalToolPendingWarning(tool));
                return Mono.just(accumulator.toBatch());
            }
            cancellationChecker.check(context.request());
            return repairIfNeeded(toolCall, tool, context)
                .flatMap(repair -> {
                    var currentCall = repair.toolCall();
                    accumulator.addWarnings(repair.warnings());
                    if (repair.error() != null) {
                        accumulator.addError(repair.error());
                        return Mono.just(accumulator.toBatch());
                    }
                    return executeOne(currentCall, tool, context)
                        .flatMap(outcome -> {
                            if (outcome.error() != null) {
                                accumulator.addError(outcome.error());
                                return Mono.just(accumulator.toBatch());
                            }
                            accumulator.addResult(outcome.result());
                            return executeNext(toolCalls, index + 1, context, accumulator);
                        });
                });
        } catch (RuntimeException e) {
            accumulator.addError(toolError(resolvedCall, e));
            return Mono.just(accumulator.toBatch());
        }
    }

    private Mono<ToolExecutionOutcome> executeOne(ToolCall resolvedCall, ToolDefinition tool,
        ToolStepContext stepContext) {
        var context = executionContext(resolvedCall, stepContext);
        var started = Instant.now();
        return stepContext.lifecycle()
            .toolCallStart(stepContext.stepIndex(), resolvedCall, context.getProviderMetadata())
            .then(Mono.defer(() -> toolTimeout.apply(tool.getExecutor().execute(context),
                stepContext.request())))
            .doOnNext(value -> {
                cancellationChecker.check(stepContext.request());
                validateOutput(resolvedCall, tool, value);
            })
            .map(value -> ToolResult.builder()
                .toolCallId(resolvedCall.getToolCallId())
                .toolName(resolvedCall.getToolName())
                .result(value)
                .build())
            .flatMap(result -> stepContext.lifecycle()
                .toolCallFinish(stepContext.stepIndex(), result, null, started,
                    context.getProviderMetadata())
                .thenReturn(new ToolExecutionOutcome(result, null)))
            .onErrorResume(RuntimeException.class, e -> {
                var error = toolError(resolvedCall, e);
                return stepContext.lifecycle().toolCallFinish(stepContext.stepIndex(), null, error, started,
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
        var context = ToolStepContext.approval(request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, toolsByName(request), approvalIdFactory);
        return evaluateApprovalNext(toolCalls, 0, context, new ApprovalAccumulator());
    }

    private Mono<ToolApprovalBatch> evaluateApprovalNext(List<ToolCall> toolCalls, int index,
        ToolStepContext context, ApprovalAccumulator accumulator) {
        if (index >= toolCalls.size() || accumulator.isTerminal()) {
            return Mono.just(finalizeApproval(accumulator));
        }
        var toolCall = toolCalls.get(index);
        var resolvedCall = toolCall;
        try {
            var tool = context.tool(toolCall);
            if (tool == null) {
                accumulator.addResolvedCall(toolCall);
                accumulator.addError(unknownToolError(toolCall));
                return Mono.just(finalizeApproval(accumulator));
            }
            if (tool.getExecutor() == null) {
                accumulator.pendingExternal(toolCall, externalToolPendingWarning(tool));
                return Mono.just(finalizeApproval(accumulator));
            }
            cancellationChecker.check(context.request());
            return repairIfNeeded(toolCall, tool, context)
                .flatMap(repair -> handleApprovalRepair(toolCalls, index, context, accumulator,
                    tool, repair));
        } catch (RuntimeException e) {
            accumulator.addResolvedCall(resolvedCall);
            accumulator.addError(toolError(resolvedCall, e));
            return Mono.just(finalizeApproval(accumulator));
        }
    }

    private Mono<ToolApprovalBatch> handleApprovalRepair(List<ToolCall> toolCalls, int index,
        ToolStepContext context, ApprovalAccumulator accumulator, ToolDefinition tool,
        RepairAttempt repair) {
        var resolvedCall = repair.toolCall();
        accumulator.addWarnings(repair.warnings());
        if (repair.error() != null) {
            accumulator.addResolvedCall(resolvedCall);
            accumulator.addError(repair.error());
            return Mono.just(finalizeApproval(accumulator));
        }
        accumulator.addResolvedCall(resolvedCall);
        var executionContext = executionContext(resolvedCall, context);
        var policy = tool.getApprovalPolicy();
        if (policy != null && policy.requiresApproval(executionContext)) {
            var approval = ToolApprovalRequest.from(resolvedCall,
                context.approvalId(resolvedCall), context.stepIndex(),
                executionContext.getProviderMetadata());
            return context.lifecycle().toolApprovalRequest(context.stepIndex(), approval)
                .then(Mono.fromSupplier(() -> {
                    accumulator.addApproval(approval);
                    return finalizeApproval(accumulator);
                }));
        }
        accumulator.addExecutable(resolvedCall);
        return evaluateApprovalNext(toolCalls, index + 1, context, accumulator);
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

    private ToolApprovalBatch finalizeApproval(ApprovalAccumulator accumulator) {
        if (!accumulator.approvals.isEmpty()) {
            var approvalCallIds = accumulator.approvals.stream()
                .map(ToolApprovalRequest::getToolCallId)
                .collect(Collectors.toCollection(HashSet::new));
            accumulator.resolvedCalls.removeIf(toolCall ->
                !approvalCallIds.contains(toolCall.getToolCallId()));
            accumulator.executable.clear();
        }
        return accumulator.toBatch();
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

    private ToolExecutionContext executionContext(ToolCall toolCall, ToolStepContext context) {
        return executionContext(toolCall, context.request(), context.stepIndex(),
            context.executionMessages(), context.stepProviderMetadata());
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
        ToolStepContext context) {
        try {
            validateInput(toolCall, tool);
            return Mono.just(new RepairAttempt(toolCall, null, List.of()));
        } catch (RuntimeException validationFailure) {
            var repairCallback = context.request().getToolCallRepair();
            if (repairCallback == null) {
                return Mono.just(new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
                    List.of()));
            }
            return repairCallback.repair(ToolCallRepairContext.builder()
                    .toolCall(toolCall)
                    .tool(tool)
                    .validationError(safeErrorMessage(validationFailure))
                    .validationPath(validationPath(toolCall))
                    .stepIndex(context.stepIndex())
                    .messages(List.copyOf(context.executionMessages()))
                    .requestContext(copyContext(context.request().getContext()))
                    .providerMetadata(mergeProviderMetadata(context.stepProviderMetadata(),
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

    private record ToolStepContext(
        GenerateTextRequest request,
        int stepIndex,
        List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata,
        ToolLifecycle lifecycle,
        Map<String, ToolDefinition> toolsByName,
        Function<ToolCall, String> approvalIdFactory
    ) {
        static ToolStepContext execution(GenerateTextRequest request, int stepIndex,
            List<ModelMessage> executionMessages, Map<String, Object> stepProviderMetadata,
            ToolLifecycle lifecycle, Map<String, ToolDefinition> toolsByName) {
            return new ToolStepContext(request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, toolsByName, null);
        }

        static ToolStepContext approval(GenerateTextRequest request, int stepIndex,
            List<ModelMessage> executionMessages, Map<String, Object> stepProviderMetadata,
            ToolLifecycle lifecycle, Map<String, ToolDefinition> toolsByName,
            Function<ToolCall, String> approvalIdFactory) {
            return new ToolStepContext(request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, toolsByName, approvalIdFactory);
        }

        ToolDefinition tool(ToolCall toolCall) {
            return toolsByName.get(toolCall.getToolName());
        }

        String approvalId(ToolCall toolCall) {
            return approvalIdFactory.apply(toolCall);
        }
    }

    private static final class ExecutionAccumulator {
        private final ArrayList<ToolResult> results = new ArrayList<>();
        private final ArrayList<ToolError> errors = new ArrayList<>();
        private final ArrayList<GenerationWarning> warnings = new ArrayList<>();

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        void addResult(ToolResult result) {
            results.add(result);
        }

        void addError(ToolError error) {
            errors.add(error);
        }

        void addWarning(GenerationWarning warning) {
            warnings.add(warning);
        }

        void addWarnings(List<GenerationWarning> warnings) {
            this.warnings.addAll(warnings);
        }

        ToolExecutionBatch toBatch() {
            return new ToolExecutionBatch(List.copyOf(results), List.copyOf(errors),
                List.copyOf(warnings));
        }
    }

    private static final class ApprovalAccumulator {
        private final ArrayList<ToolCall> executable = new ArrayList<>();
        private final ArrayList<ToolCall> resolvedCalls = new ArrayList<>();
        private final ArrayList<ToolApprovalRequest> approvals = new ArrayList<>();
        private final ArrayList<ToolError> errors = new ArrayList<>();
        private final ArrayList<GenerationWarning> warnings = new ArrayList<>();
        private boolean hasPendingExternalCalls;

        boolean isTerminal() {
            return !approvals.isEmpty() || !errors.isEmpty() || hasPendingExternalCalls;
        }

        void addExecutable(ToolCall toolCall) {
            executable.add(toolCall);
        }

        void addResolvedCall(ToolCall toolCall) {
            resolvedCalls.add(toolCall);
        }

        void addApproval(ToolApprovalRequest approval) {
            approvals.add(approval);
        }

        void addError(ToolError error) {
            errors.add(error);
        }

        void addWarnings(List<GenerationWarning> warnings) {
            this.warnings.addAll(warnings);
        }

        void pendingExternal(ToolCall toolCall, GenerationWarning warning) {
            executable.clear();
            resolvedCalls.clear();
            resolvedCalls.add(toolCall);
            warnings.add(warning);
            hasPendingExternalCalls = true;
        }

        ToolApprovalBatch toBatch() {
            return new ToolApprovalBatch(List.copyOf(resolvedCalls), List.copyOf(executable),
                List.copyOf(approvals), List.copyOf(errors), List.copyOf(warnings),
                hasPendingExternalCalls);
        }
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
