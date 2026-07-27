package run.halo.aifoundation.service.language.tool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import run.halo.aifoundation.tool.ToolInputAvailableContext;
import run.halo.aifoundation.tool.ToolInputDeltaContext;
import run.halo.aifoundation.tool.ToolInputStartContext;
import run.halo.aifoundation.tool.ToolResult;

public final class LanguageModelToolExecutor {
    public static final String WARNING_EXTERNAL_TOOL_PENDING = "external-tool-pending";
    public static final String WARNING_TOOL_CALL_REPAIRED = "tool-call-repaired";
    public static final String WARNING_TOOL_CALL_REPAIR_FAILED = "tool-call-repair-failed";

    private final JsonSchemaValidator schemaValidator;
    private final CancellationChecker cancellationChecker;
    private final ToolTimeout toolTimeout;
    private final CallbackTimeout callbackTimeout;

    public LanguageModelToolExecutor(JsonSchemaValidator schemaValidator,
        CancellationChecker cancellationChecker, ToolTimeout toolTimeout,
        CallbackTimeout callbackTimeout) {
        this.schemaValidator = schemaValidator;
        this.cancellationChecker = cancellationChecker;
        this.toolTimeout = toolTimeout;
        this.callbackTimeout = callbackTimeout;
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
        if (index >= toolCalls.size()) {
            return Mono.just(accumulator.toBatch());
        }
        var toolCall = toolCalls.get(index);
        var resolvedCall = toolCall;
        try {
            var tool = context.tool(toolCall);
            if (tool == null) {
                accumulator.addError(unknownToolError(toolCall));
                return executeNext(toolCalls, index + 1, context, accumulator);
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
                        return executeNext(toolCalls, index + 1, context, accumulator);
                    }
                    return executeOne(currentCall, tool, context)
                        .flatMap(outcome -> {
                            if (outcome.error() != null) {
                                accumulator.addError(outcome.error());
                                return executeNext(toolCalls, index + 1, context, accumulator);
                            }
                            accumulator.addResult(outcome.result());
                            return executeNext(toolCalls, index + 1, context, accumulator);
                        });
                });
        } catch (RuntimeException e) {
            accumulator.addError(toolError(resolvedCall, e));
            return executeNext(toolCalls, index + 1, context, accumulator);
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

    public Mono<ToolNormalizationBatch> normalizeInputs(List<ToolCall> toolCalls,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Set<String> streamedInputCallIds) {
        if (toolCalls.isEmpty()) {
            return Mono.just(new ToolNormalizationBatch(List.of(), List.of(), List.of(), Set.of(),
                List.of()));
        }
        var context = ToolStepContext.normalization(request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, toolsByName(request),
            streamedInputCallIds != null ? Set.copyOf(streamedInputCallIds) : Set.of());
        return normalizeNext(toolCalls, 0, context, new NormalizationAccumulator());
    }

    private Mono<ToolNormalizationBatch> normalizeNext(List<ToolCall> toolCalls, int index,
        ToolStepContext context, NormalizationAccumulator accumulator) {
        if (index >= toolCalls.size()) {
            return Mono.just(accumulator.toBatch());
        }
        var toolCall = toolCalls.get(index);
        try {
            var tool = context.tool(toolCall);
            if (tool == null) {
                accumulator.addInvalidToolCall(toolCall, unknownToolError(toolCall));
                return normalizeNext(toolCalls, index + 1, context, accumulator);
            }
            cancellationChecker.check(context.request());
            return notifyInputStartUnlessStreamed(toolCall, tool, context)
                .then(Mono.defer(() -> repairIfNeeded(toolCall, tool, context)))
                .flatMap(repair -> {
                    accumulator.addWarnings(repair.warnings());
                    if (repair.error() != null) {
                        accumulator.addInvalidToolCall(toolCall, repair.error());
                    } else {
                        accumulator.addToolCall(repair.toolCall());
                    }
                    return normalizeNext(toolCalls, index + 1, context, accumulator);
                });
        } catch (RuntimeException e) {
            accumulator.addInvalidToolCall(toolCall, toolError(toolCall, e));
            return normalizeNext(toolCalls, index + 1, context, accumulator);
        }
    }

    private Mono<Void> notifyInputStartUnlessStreamed(ToolCall toolCall, ToolDefinition tool,
        ToolStepContext context) {
        if (context.streamedInputCallIds().contains(toolCall.getToolCallId())) {
            return Mono.empty();
        }
        return inputStart(toolCall.getToolCallId(), toolCall.getToolName(), tool,
            context.request(), context.stepIndex(), context.executionMessages(),
            context.providerMetadata(toolCall));
    }

    public Mono<ToolApprovalBatch> evaluateApproval(List<ToolCall> toolCalls,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory) {
        if (toolCalls.isEmpty()) {
            return Mono.just(new ToolApprovalBatch(List.of(), List.of(), List.of(), List.of(),
                List.of(), false));
        }
        var context = ToolStepContext.approval(request, stepIndex, executionMessages,
            stepProviderMetadata, lifecycle, toolsByName(request), approvalIdFactory);
        return notifyInputAvailable(toolCalls, request, stepIndex, executionMessages,
                stepProviderMetadata)
            .then(Mono.defer(() ->
                evaluateApprovalNext(toolCalls, 0, context, new ApprovalAccumulator())));
    }

    public Mono<Void> notifyInputAvailable(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata) {
        var context = ToolStepContext.normalization(request, stepIndex, executionMessages,
            stepProviderMetadata, null, toolsByName(request), Set.of());
        return reactor.core.publisher.Flux.fromIterable(toolCalls)
            .concatMap(toolCall -> {
                var tool = context.tool(toolCall);
                if (tool == null) {
                    return Mono.error(new IllegalStateException(
                        "Normalized tool is no longer active: " + toolCall.getToolName()));
                }
                return inputAvailable(toolCall, tool, context.request(), context.stepIndex(),
                    context.executionMessages(), context.providerMetadata(toolCall));
            })
            .then();
    }

    private Mono<ToolApprovalBatch> evaluateApprovalNext(List<ToolCall> toolCalls, int index,
        ToolStepContext context, ApprovalAccumulator accumulator) {
        if (index >= toolCalls.size() || accumulator.isTerminal()) {
            return Mono.just(finalizeApproval(accumulator));
        }
        var toolCall = toolCalls.get(index);
        try {
            var tool = context.tool(toolCall);
            if (tool == null) {
                accumulator.addError(unknownToolError(toolCall));
                return Mono.just(finalizeApproval(accumulator));
            }
            accumulator.addResolvedCall(toolCall);
            if (tool.getExecutor() == null) {
                accumulator.pendingExternal(toolCall, externalToolPendingWarning(tool));
                return Mono.just(finalizeApproval(accumulator));
            }
            var executionContext = executionContext(toolCall, context);
            var policy = tool.getApprovalPolicy();
            if (policy != null && policy.requiresApproval(executionContext)) {
                var approval = ToolApprovalRequest.from(toolCall,
                    context.approvalId(toolCall), context.stepIndex(),
                    executionContext.getProviderMetadata());
                return context.lifecycle().toolApprovalRequest(context.stepIndex(), approval)
                    .then(Mono.fromSupplier(() -> {
                        accumulator.addApproval(approval);
                        return finalizeApproval(accumulator);
                    }));
            }
            accumulator.addExecutable(toolCall);
            return evaluateApprovalNext(toolCalls, index + 1, context, accumulator);
        } catch (RuntimeException e) {
            accumulator.addError(toolError(toolCall, e));
            return Mono.just(finalizeApproval(accumulator));
        }
    }

    public Mono<Void> inputStart(String toolCallId, String toolName, ToolDefinition tool,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> providerMetadata) {
        if (tool == null || tool.getOnInputStart() == null) {
            return checkedEmpty(request);
        }
        var context = ToolInputStartContext.builder()
            .toolCallId(toolCallId)
            .toolName(toolName)
            .stepIndex(stepIndex)
            .messages(executionMessages)
            .requestContext(copyContext(request.getContext()))
            .providerMetadata(providerMetadata)
            .cancellationToken(request.getCancellationToken())
            .build();
        return invokeCallback(Mono.defer(() -> tool.getOnInputStart().onInputStart(context)),
            request);
    }

    public Mono<Void> inputDelta(String toolCallId, String toolName, String inputTextDelta,
        ToolDefinition tool, GenerateTextRequest request, int stepIndex,
        List<ModelMessage> executionMessages, Map<String, Object> providerMetadata) {
        if (tool == null || tool.getOnInputDelta() == null) {
            return checkedEmpty(request);
        }
        var context = ToolInputDeltaContext.builder()
            .toolCallId(toolCallId)
            .toolName(toolName)
            .inputTextDelta(inputTextDelta)
            .stepIndex(stepIndex)
            .messages(executionMessages)
            .requestContext(copyContext(request.getContext()))
            .providerMetadata(providerMetadata)
            .cancellationToken(request.getCancellationToken())
            .build();
        return invokeCallback(Mono.defer(() -> tool.getOnInputDelta().onInputDelta(context)),
            request);
    }

    private Mono<Void> inputAvailable(ToolCall toolCall, ToolDefinition tool,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> providerMetadata) {
        if (tool.getOnInputAvailable() == null) {
            return checkedEmpty(request);
        }
        var context = ToolInputAvailableContext.builder()
            .toolCallId(toolCall.getToolCallId())
            .toolName(toolCall.getToolName())
            .input(toolCall.getInput())
            .stepIndex(stepIndex)
            .messages(executionMessages)
            .requestContext(copyContext(request.getContext()))
            .providerMetadata(providerMetadata)
            .cancellationToken(request.getCancellationToken())
            .build();
        return invokeCallback(Mono.defer(() -> tool.getOnInputAvailable()
            .onInputAvailable(context)), request);
    }

    private Mono<Void> checkedEmpty(GenerateTextRequest request) {
        return Mono.fromRunnable(() -> cancellationChecker.check(request));
    }

    private Mono<Void> invokeCallback(Mono<Void> callback, GenerateTextRequest request) {
        return Mono.fromRunnable(() -> cancellationChecker.check(request))
            .then(callbackTimeout.apply(callback, request))
            .then(Mono.fromRunnable(() -> cancellationChecker.check(request)));
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
            .requestContext(copyContext(request.getContext()))
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
        var parseError = toolCall.getInputParseError();
        if (parseError != null) {
            return repair(toolCall, tool, context,
                new IllegalArgumentException(parseError.getMessage()));
        }
        try {
            validateInput(toolCall, tool);
            return Mono.just(new RepairAttempt(toolCall, null, List.of()));
        } catch (RuntimeException validationFailure) {
            return repair(toolCall, tool, context, validationFailure);
        }
    }

    private Mono<RepairAttempt> repair(ToolCall toolCall, ToolDefinition tool,
        ToolStepContext context, RuntimeException validationFailure) {
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
            .inputParseError(null)
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

    private static Map<String, Object> mergeProviderMetadata(Map<String, Object> left,
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
        Function<ToolCall, String> approvalIdFactory,
        Set<String> streamedInputCallIds
    ) {
        static ToolStepContext execution(GenerateTextRequest request, int stepIndex,
            List<ModelMessage> executionMessages, Map<String, Object> stepProviderMetadata,
            ToolLifecycle lifecycle, Map<String, ToolDefinition> toolsByName) {
            return new ToolStepContext(request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, toolsByName, null, Set.of());
        }

        static ToolStepContext approval(GenerateTextRequest request, int stepIndex,
            List<ModelMessage> executionMessages, Map<String, Object> stepProviderMetadata,
            ToolLifecycle lifecycle, Map<String, ToolDefinition> toolsByName,
            Function<ToolCall, String> approvalIdFactory) {
            return new ToolStepContext(request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, toolsByName, approvalIdFactory, Set.of());
        }

        static ToolStepContext normalization(GenerateTextRequest request, int stepIndex,
            List<ModelMessage> executionMessages, Map<String, Object> stepProviderMetadata,
            ToolLifecycle lifecycle, Map<String, ToolDefinition> toolsByName,
            Set<String> streamedInputCallIds) {
            return new ToolStepContext(request, stepIndex, executionMessages,
                stepProviderMetadata, lifecycle, toolsByName, null, streamedInputCallIds);
        }

        ToolDefinition tool(ToolCall toolCall) {
            return toolsByName.get(toolCall.getToolName());
        }

        String approvalId(ToolCall toolCall) {
            return approvalIdFactory.apply(toolCall);
        }

        Map<String, Object> providerMetadata(ToolCall toolCall) {
            return mergeProviderMetadata(stepProviderMetadata, toolCall.getProviderMetadata());
        }
    }

    private static final class ExecutionAccumulator {
        private final ArrayList<ToolResult> results = new ArrayList<>();
        private final ArrayList<ToolError> errors = new ArrayList<>();
        private final ArrayList<GenerationWarning> warnings = new ArrayList<>();

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

    private static final class NormalizationAccumulator {
        private final ArrayList<ToolCall> recordedToolCalls = new ArrayList<>();
        private final ArrayList<ToolCall> executableToolCalls = new ArrayList<>();
        private final ArrayList<ToolError> inputErrors = new ArrayList<>();
        private final HashSet<String> inputErrorCallIds = new HashSet<>();
        private final ArrayList<GenerationWarning> warnings = new ArrayList<>();

        void addToolCall(ToolCall toolCall) {
            recordedToolCalls.add(toolCall);
            executableToolCalls.add(toolCall);
        }

        void addInvalidToolCall(ToolCall toolCall, ToolError error) {
            recordedToolCalls.add(toolCall);
            inputErrors.add(error);
            inputErrorCallIds.add(error.getToolCallId());
        }

        void addWarnings(List<GenerationWarning> warnings) {
            this.warnings.addAll(warnings);
        }

        ToolNormalizationBatch toBatch() {
            return new ToolNormalizationBatch(List.copyOf(recordedToolCalls),
                List.copyOf(executableToolCalls), List.copyOf(inputErrors),
                Set.copyOf(inputErrorCallIds), List.copyOf(warnings));
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

    @FunctionalInterface
    public interface CallbackTimeout {
        Mono<Void> apply(Mono<Void> mono, GenerateTextRequest request);
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
