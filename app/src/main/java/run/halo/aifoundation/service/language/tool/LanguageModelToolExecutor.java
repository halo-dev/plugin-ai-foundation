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
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolApprovalRequest;
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

    public ToolExecutionBatch execute(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle) {
        if (toolCalls.isEmpty()) {
            return new ToolExecutionBatch(List.of(), List.of(), List.of());
        }
        var toolsByName = request.getTools() == null ? Map.<String, ToolDefinition>of()
            : request.getTools().stream()
                .collect(Collectors.toMap(ToolDefinition::getName, Function.identity()));
        var results = new ArrayList<ToolResult>();
        var errors = new ArrayList<ToolError>();
        var warnings = new ArrayList<GenerationWarning>();
        for (var toolCall : toolCalls) {
            var resolvedCall = toolCall;
            var tool = toolsByName.get(toolCall.getToolName());
            if (tool == null) {
                errors.add(ToolError.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .errorText("Unknown tool: " + toolCall.getToolName())
                    .build());
                break;
            }
            if (tool.getExecutor() == null) {
                warnings.add(externalToolPendingWarning(tool));
                break;
            }
            try {
                cancellationChecker.check(request);
                var repair = repairIfNeeded(toolCall, tool, request, stepIndex, executionMessages,
                    stepProviderMetadata);
                resolvedCall = repair.toolCall();
                warnings.addAll(repair.warnings());
                if (repair.error() != null) {
                    errors.add(repair.error());
                    break;
                }
                var context = ToolExecutionContext.builder()
                    .toolCallId(resolvedCall.getToolCallId())
                    .toolName(resolvedCall.getToolName())
                    .input(resolvedCall.getInput())
                    .stepIndex(stepIndex)
                    .messages(List.copyOf(executionMessages))
                    .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                        resolvedCall.getProviderMetadata()))
                    .cancellationToken(request.getCancellationToken())
                    .build();
                lifecycle.toolCallStart(stepIndex, resolvedCall, context.getProviderMetadata());
                var started = Instant.now();
                Object value;
                try {
                    value = toolTimeout.apply(tool.getExecutor().execute(context), request).block();
                    cancellationChecker.check(request);
                } catch (RuntimeException e) {
                    var error = toolError(resolvedCall, e);
                    lifecycle.toolCallFinish(stepIndex, null, error, started,
                        context.getProviderMetadata());
                    errors.add(error);
                    break;
                }
                if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
                    schemaValidator.validate(value, tool.getOutputSchema(),
                        "$." + resolvedCall.getToolName() + ".output");
                }
                var result = ToolResult.builder()
                    .toolCallId(resolvedCall.getToolCallId())
                    .toolName(resolvedCall.getToolName())
                    .result(value)
                    .build();
                lifecycle.toolCallFinish(stepIndex, result, null, started,
                    context.getProviderMetadata());
                results.add(result);
            } catch (RuntimeException e) {
                var error = toolError(resolvedCall, e);
                errors.add(error);
                break;
            }
        }
        return new ToolExecutionBatch(results, errors, warnings);
    }

    public ToolApprovalBatch evaluateApproval(List<ToolCall> toolCalls, GenerateTextRequest request,
        int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata, ToolLifecycle lifecycle,
        Function<ToolCall, String> approvalIdFactory) {
        if (toolCalls.isEmpty()) {
            return new ToolApprovalBatch(List.of(), List.of(), List.of(), List.of(), List.of(),
                false);
        }
        var toolsByName = request.getTools() == null ? Map.<String, ToolDefinition>of()
            : request.getTools().stream()
                .collect(Collectors.toMap(ToolDefinition::getName, Function.identity()));
        var executable = new ArrayList<ToolCall>();
        var resolvedCalls = new ArrayList<ToolCall>();
        var approvals = new ArrayList<ToolApprovalRequest>();
        var errors = new ArrayList<ToolError>();
        var warnings = new ArrayList<GenerationWarning>();
        var hasPendingExternalCalls = false;
        for (var toolCall : toolCalls) {
            var resolvedCall = toolCall;
            var tool = toolsByName.get(toolCall.getToolName());
            if (tool == null) {
                resolvedCalls.add(toolCall);
                errors.add(ToolError.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .errorText("Unknown tool: " + toolCall.getToolName())
                    .build());
                break;
            }
            if (tool.getExecutor() == null) {
                executable.clear();
                resolvedCalls.clear();
                resolvedCalls.add(toolCall);
                warnings.add(externalToolPendingWarning(tool));
                hasPendingExternalCalls = true;
                break;
            }
            try {
                cancellationChecker.check(request);
                var repair = repairIfNeeded(toolCall, tool, request, stepIndex, executionMessages,
                    stepProviderMetadata);
                resolvedCall = repair.toolCall();
                warnings.addAll(repair.warnings());
                if (repair.error() != null) {
                    resolvedCalls.add(resolvedCall);
                    errors.add(repair.error());
                    break;
                }
                resolvedCalls.add(resolvedCall);
                var context = ToolExecutionContext.builder()
                    .toolCallId(resolvedCall.getToolCallId())
                    .toolName(resolvedCall.getToolName())
                    .input(resolvedCall.getInput())
                    .stepIndex(stepIndex)
                    .messages(List.copyOf(executionMessages))
                    .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                        resolvedCall.getProviderMetadata()))
                    .cancellationToken(request.getCancellationToken())
                    .build();
                var policy = tool.getApprovalPolicy();
                if (policy != null && policy.requiresApproval(context)) {
                    var approval = ToolApprovalRequest.from(resolvedCall,
                        approvalIdFactory.apply(resolvedCall), stepIndex, context.getProviderMetadata());
                    lifecycle.toolApprovalRequest(stepIndex, approval);
                    approvals.add(approval);
                    break;
                } else {
                    executable.add(resolvedCall);
                }
            } catch (RuntimeException e) {
                resolvedCalls.add(resolvedCall);
                errors.add(toolError(resolvedCall, e));
                break;
            }
        }
        if (!approvals.isEmpty()) {
            var approvalCallIds = approvals.stream()
                .map(ToolApprovalRequest::getToolCallId)
                .collect(Collectors.toCollection(HashSet::new));
            resolvedCalls.removeIf(toolCall -> !approvalCallIds.contains(toolCall.getToolCallId()));
            executable.clear();
        }
        return new ToolApprovalBatch(resolvedCalls, executable, approvals, errors, warnings,
            hasPendingExternalCalls);
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

    private RepairAttempt repairIfNeeded(ToolCall toolCall, ToolDefinition tool,
        GenerateTextRequest request, int stepIndex, List<ModelMessage> executionMessages,
        Map<String, Object> stepProviderMetadata) {
        try {
            validateInput(toolCall, tool);
            return new RepairAttempt(toolCall, null, List.of());
        } catch (RuntimeException validationFailure) {
            var repairCallback = request.getToolCallRepair();
            if (repairCallback == null) {
                return new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
                    List.of());
            }
            var warnings = new ArrayList<GenerationWarning>();
            try {
                var result = repairCallback.repair(run.halo.aifoundation.tool.ToolCallRepairContext
                    .builder()
                    .toolCall(toolCall)
                    .tool(tool)
                    .validationError(safeErrorMessage(validationFailure))
                    .validationPath(validationPath(toolCall))
                    .stepIndex(stepIndex)
                    .messages(List.copyOf(executionMessages))
                    .requestContext(copyContext(request.getContext()))
                    .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                        toolCall.getProviderMetadata()))
                    .build()).block();
                var repaired = result != null ? result.getToolCall() : null;
                if (repaired == null) {
                    warnings.add(repairFailedWarning(toolCall, validationFailure));
                    return new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
                        warnings);
                }
                var normalized = normalizeRepairedCall(toolCall, repaired);
                validateInput(normalized, tool);
                warnings.add(repairedWarning(toolCall, normalized));
                return new RepairAttempt(normalized, null, warnings);
            } catch (RuntimeException repairFailure) {
                warnings.add(repairFailedWarning(toolCall, repairFailure));
                return new RepairAttempt(toolCall, toolError(toolCall, validationFailure),
                    warnings);
            }
        }
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
        void toolCallStart(int stepIndex, ToolCall toolCall, Map<String, Object> metadata);

        void toolCallFinish(int stepIndex, ToolResult result, ToolError error, Instant startedAt,
            Map<String, Object> metadata);

        void toolApprovalRequest(int stepIndex, ToolApprovalRequest request);
    }

    private record RepairAttempt(
        ToolCall toolCall,
        ToolError error,
        List<GenerationWarning> warnings
    ) {
    }
}
