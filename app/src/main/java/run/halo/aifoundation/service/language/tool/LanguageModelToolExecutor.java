package run.halo.aifoundation.service.language.tool;

import java.time.Instant;
import java.util.ArrayList;
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
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolError;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolResult;

public final class LanguageModelToolExecutor {
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
                warnings.add(GenerationWarning.builder()
                    .code("tool-not-executed")
                    .message("Tool has no executor: " + tool.getName())
                    .build());
                break;
            }
            try {
                cancellationChecker.check(request);
                if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
                    schemaValidator.validate(toolCall.getInput(), tool.getInputSchema(),
                        "$." + toolCall.getToolName() + ".input");
                }
                var context = ToolExecutionContext.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .input(toolCall.getInput())
                    .stepIndex(stepIndex)
                    .messages(List.copyOf(executionMessages))
                    .providerMetadata(mergeProviderMetadata(stepProviderMetadata,
                        toolCall.getProviderMetadata()))
                    .build();
                lifecycle.toolCallStart(stepIndex, toolCall, context.getProviderMetadata());
                var started = Instant.now();
                Object value;
                try {
                    value = toolTimeout.apply(tool.getExecutor().execute(context), request).block();
                    cancellationChecker.check(request);
                } catch (RuntimeException e) {
                    var error = toolError(toolCall, e);
                    lifecycle.toolCallFinish(stepIndex, null, error, started,
                        context.getProviderMetadata());
                    errors.add(error);
                    break;
                }
                if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
                    schemaValidator.validate(value, tool.getOutputSchema(),
                        "$." + toolCall.getToolName() + ".output");
                }
                var result = ToolResult.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .result(value)
                    .build();
                lifecycle.toolCallFinish(stepIndex, result, null, started,
                    context.getProviderMetadata());
                results.add(result);
            } catch (RuntimeException e) {
                var error = toolError(toolCall, e);
                errors.add(error);
                break;
            }
        }
        return new ToolExecutionBatch(results, errors, warnings);
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
        void toolCallStart(int stepIndex, ToolCall toolCall, Map<String, Object> metadata);

        void toolCallFinish(int stepIndex, ToolResult result, ToolError error, Instant startedAt,
            Map<String, Object> metadata);
    }
}
