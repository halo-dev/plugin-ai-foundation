package run.halo.aifoundation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.GenerateTextRequest;
import run.halo.aifoundation.GenerateTextResult;
import run.halo.aifoundation.GenerationErrorEvent;
import run.halo.aifoundation.GenerationFinishEvent;
import run.halo.aifoundation.GenerationLifecycle;
import run.halo.aifoundation.GenerationRequestMetadata;
import run.halo.aifoundation.GenerationStartEvent;
import run.halo.aifoundation.GenerationStep;
import run.halo.aifoundation.GenerationStepFinishEvent;
import run.halo.aifoundation.GenerationStepStartEvent;
import run.halo.aifoundation.GenerationToolCallFinishEvent;
import run.halo.aifoundation.GenerationToolCallStartEvent;
import run.halo.aifoundation.GenerationWarning;
import run.halo.aifoundation.ModelMessage;
import run.halo.aifoundation.StopCondition;
import run.halo.aifoundation.ToolCall;
import run.halo.aifoundation.ToolError;
import run.halo.aifoundation.ToolResult;

final class LanguageModelGenerationRun implements LanguageModelToolExecutor.ToolLifecycle {
    private final GenerateTextRequest request;
    private final String providerType;
    private final StopCondition initialStopWhen;
    private final ArrayList<GenerationWarning> lifecycleWarnings = new ArrayList<>();
    private boolean started;
    private boolean finished;
    private boolean errorNotified;

    LanguageModelGenerationRun(GenerateTextRequest request, String providerType,
        StopCondition initialStopWhen) {
        this.request = request;
        this.providerType = providerType;
        this.initialStopWhen = initialStopWhen;
    }

    List<GenerationWarning> warnings() {
        return List.copyOf(lifecycleWarnings);
    }

    void start() {
        if (started) {
            return;
        }
        started = true;
        invoke("onStart", lifecycle -> lifecycle.onStart(GenerationStartEvent.builder()
            .request(request)
            .requestMetadata(GenerationRequestMetadata.builder()
                .metadata(Map.of("providerType", providerType))
                .build())
            .metadata(metadata(request))
            .context(context(request))
            .tools(nullSafe(request != null ? request.getTools() : null))
            .toolChoice(request != null ? request.getToolChoice() : null)
            .stopWhen(initialStopWhen)
            .timeouts(request != null ? request.getTimeouts() : null)
            .build()));
    }

    void stepStart(int stepIndex, GenerateTextRequest stepRequest, List<ModelMessage> messages,
        List<GenerationStep> previousSteps, StopCondition stopWhen) {
        invoke("onStepStart", lifecycle -> lifecycle.onStepStart(GenerationStepStartEvent.builder()
            .stepIndex(stepIndex)
            .request(stepRequest)
            .messages(List.copyOf(nullSafe(messages)))
            .previousSteps(List.copyOf(nullSafe(previousSteps)))
            .tools(nullSafe(stepRequest != null ? stepRequest.getTools() : null))
            .toolChoice(stepRequest != null ? stepRequest.getToolChoice() : null)
            .stopWhen(stopWhen)
            .providerOptions(stepRequest != null ? stepRequest.getProviderOptions() : null)
            .timeouts(stepRequest != null ? stepRequest.getTimeouts() : null)
            .metadata(metadata(stepRequest))
            .context(context(stepRequest))
            .build()));
    }

    @Override
    public void toolCallStart(int stepIndex, ToolCall toolCall,
        Map<String, Object> providerMetadata) {
        invoke("onToolCallStart", lifecycle -> lifecycle.onToolCallStart(
            GenerationToolCallStartEvent.builder()
                .stepIndex(stepIndex)
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .input(toolCall.getInput())
                .providerMetadata(providerMetadata)
                .metadata(metadata(request))
                .context(context(request))
                .build()));
    }

    @Override
    public void toolCallFinish(int stepIndex, ToolResult result, ToolError error,
        Instant startedAt, Map<String, Object> providerMetadata) {
        var duration = startedAt != null ? Duration.between(startedAt, Instant.now()) : null;
        invoke("onToolCallFinish", lifecycle -> lifecycle.onToolCallFinish(
            GenerationToolCallFinishEvent.builder()
                .stepIndex(stepIndex)
                .toolCallId(result != null ? result.getToolCallId() : error.getToolCallId())
                .toolName(result != null ? result.getToolName() : error.getToolName())
                .result(result)
                .error(error)
                .duration(duration)
                .providerMetadata(providerMetadata)
                .metadata(metadata(request))
                .context(context(request))
                .build()));
    }

    void stepFinish(int stepIndex, GenerationStep step, List<GenerationStep> steps) {
        invoke("onStepFinish", lifecycle -> lifecycle.onStepFinish(
            GenerationStepFinishEvent.builder()
                .stepIndex(stepIndex)
                .step(step)
                .steps(List.copyOf(nullSafe(steps)))
                .metadata(metadata(request))
                .context(context(request))
                .build()));
    }

    void finish(GenerateTextResult result) {
        if (finished) {
            return;
        }
        finished = true;
        invoke("onFinish", lifecycle -> lifecycle.onFinish(GenerationFinishEvent.builder()
            .result(result)
            .metadata(metadata(request))
            .context(context(request))
            .build()));
    }

    void error(Throwable error, Integer stepIndex, List<GenerationStep> steps) {
        if (errorNotified || finished) {
            return;
        }
        errorNotified = true;
        invoke("onError", lifecycle -> lifecycle.onError(GenerationErrorEvent.builder()
            .error(error)
            .stepIndex(stepIndex)
            .steps(List.copyOf(nullSafe(steps)))
            .metadata(metadata(request))
            .context(context(request))
            .build()));
    }

    private void invoke(String callbackName,
        Function<GenerationLifecycle, Mono<Void>> callback) {
        var lifecycle = request != null ? request.getLifecycle() : null;
        if (lifecycle == null) {
            return;
        }
        try {
            var mono = callback.apply(lifecycle);
            if (mono != null) {
                mono.block();
            }
        } catch (RuntimeException e) {
            lifecycleWarnings.add(GenerationWarning.builder()
                .code("lifecycle-callback-failed")
                .message(callbackName + " callback failed: " + safeErrorMessage(e))
                .providerMetadata(Map.of("providerType", providerType))
                .build());
        }
    }

    private Map<String, Object> metadata(GenerateTextRequest request) {
        return request != null && request.getMetadata() != null
            ? Map.copyOf(request.getMetadata())
            : Map.of();
    }

    private Map<String, Object> context(GenerateTextRequest request) {
        return request != null && request.getContext() != null
            ? Map.copyOf(request.getContext())
            : Map.of();
    }

    private String safeErrorMessage(Throwable e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
