package run.halo.aifoundation.agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.PrepareStepCallback;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolCallRepairCallback;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Immutable definition options for a reusable agent.
 *
 * @param <O> typed per-call options
 */
@Value
public class AgentOptions<O> {
    String id;
    LanguageModel model;
    String instructions;
    List<ToolDefinition> tools;
    List<String> activeTools;
    ToolChoice toolChoice;
    OutputSpec output;
    StopCondition stopWhen;
    PrepareStepCallback prepareStep;
    ToolCallRepairCallback toolCallRepair;
    ReasoningOptions reasoning;
    Integer maxOutputTokens;
    Double temperature;
    Double topP;
    Integer topK;
    Double minP;
    Double presencePenalty;
    Double frequencyPenalty;
    Double repetitionPenalty;
    Boolean logprobs;
    Integer topLogprobs;
    Boolean parallelToolCalls;
    List<String> stopSequences;
    Integer seed;
    Integer maxRetries;
    Map<String, String> headers;
    Map<String, Object> metadata;
    Map<String, Object> context;
    List<LanguageModelMiddleware> middleware;
    List<GenerationLifecycle> lifecycle;
    GenerationTimeouts timeouts;
    AgentCallValidator<O> callValidator;
    AgentCallPrepare<O> prepareCall;

    @Builder(toBuilder = true)
    private AgentOptions(String id, LanguageModel model, String instructions,
        List<ToolDefinition> tools, List<String> activeTools, ToolChoice toolChoice,
        OutputSpec output, StopCondition stopWhen, PrepareStepCallback prepareStep,
        ToolCallRepairCallback toolCallRepair, ReasoningOptions reasoning,
        Integer maxOutputTokens, Double temperature, Double topP, Integer topK, Double minP,
        Double presencePenalty, Double frequencyPenalty, Double repetitionPenalty,
        Boolean logprobs, Integer topLogprobs, Boolean parallelToolCalls,
        List<String> stopSequences, Integer seed, Integer maxRetries, Map<String, String> headers,
        Map<String, Object> metadata, Map<String, Object> context,
        List<LanguageModelMiddleware> middleware, List<GenerationLifecycle> lifecycle,
        GenerationTimeouts timeouts, AgentCallValidator<O> callValidator,
        AgentCallPrepare<O> prepareCall) {
        this.id = id;
        this.model = model;
        this.instructions = instructions;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        // Keep null distinct from an explicit empty list: null means all request tools are
        // available, while an empty list intentionally disables every tool.
        this.activeTools = activeTools == null ? null : List.copyOf(activeTools);
        this.toolChoice = toolChoice;
        this.output = output;
        this.stopWhen = stopWhen;
        this.prepareStep = prepareStep;
        this.toolCallRepair = toolCallRepair;
        this.reasoning = reasoning;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.topK = topK;
        this.minP = minP;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.repetitionPenalty = repetitionPenalty;
        this.logprobs = logprobs;
        this.topLogprobs = topLogprobs;
        this.parallelToolCalls = parallelToolCalls;
        this.stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
        this.seed = seed;
        this.maxRetries = maxRetries;
        this.headers = immutableMap(headers);
        this.metadata = immutableMap(metadata);
        this.context = immutableMap(context);
        this.middleware = middleware == null ? List.of() : List.copyOf(middleware);
        this.lifecycle = lifecycle == null ? List.of() : List.copyOf(lifecycle);
        this.timeouts = timeouts;
        this.callValidator = callValidator;
        this.prepareCall = prepareCall;
    }

    /**
     * Creates a definition builder with the required model already selected.
     */
    public static AgentOptionsBuilder<Void> forModel(LanguageModel model) {
        return AgentOptions.<Void>builder().model(model);
    }

    /**
     * Creates a typed definition builder with the required model already selected.
     */
    public static <O> AgentOptionsBuilder<O> forModel(LanguageModel model, Class<O> optionsType) {
        if (optionsType == null) {
            throw new IllegalArgumentException("optionsType must not be null");
        }
        return AgentOptions.<O>builder().model(model);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
