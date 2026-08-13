package run.halo.aifoundation.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.chat.PrepareStepCallback;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddleware;
import run.halo.aifoundation.chat.middleware.LanguageModelMiddlewares;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.lifecycle.GenerationErrorEvent;
import run.halo.aifoundation.lifecycle.GenerationFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationLifecycle;
import run.halo.aifoundation.lifecycle.GenerationStartEvent;
import run.halo.aifoundation.lifecycle.GenerationStepFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationStepStartEvent;
import run.halo.aifoundation.lifecycle.GenerationToolApprovalRequestEvent;
import run.halo.aifoundation.lifecycle.GenerationToolCallFinishEvent;
import run.halo.aifoundation.lifecycle.GenerationToolCallStartEvent;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

/**
 * Immutable reusable agent that composes one effective request and delegates execution to a
 * provider-neutral {@link LanguageModel}.
 *
 * @param <O> typed per-call options
 */
public final class Agent<O> {
    /** Maximum model steps used when the definition does not provide a stop condition. */
    public static final int DEFAULT_MAX_STEPS = 20;

    private final AgentOptions<O> definition;

    private Agent(AgentOptions<O> definition) {
        this.definition = snapshot(Objects.requireNonNull(definition,
            "definition must not be null"));
        if (this.definition.getModel() == null) {
            throw new IllegalArgumentException("agent model must not be null");
        }
    }

    /**
     * Creates a typed agent from a complete immutable definition.
     */
    public static <O> Agent<O> create(AgentOptions<O> definition) {
        return new Agent<>(definition);
    }

    /**
     * Creates a no-options agent with the default bounded step policy.
     */
    public static Agent<Void> create(LanguageModel model, String instructions) {
        return create(AgentOptions.forModel(model)
            .instructions(instructions)
            .build());
    }

    /**
     * Returns a defensive snapshot of this agent's definition.
     */
    public AgentOptions<O> options() {
        return snapshot(definition);
    }

    /**
     * Generates a normalized terminal result through the configured model.
     */
    public Mono<GenerateTextResult> generate(AgentCall<O> call) {
        return prepare(call).flatMap(prepared ->
            prepared.getModel().generateText(prepared.getRequest()));
    }

    /**
     * Streams through the configured model while sharing one preparation and provider execution
     * across all result projections.
     */
    public StreamTextResult stream(AgentCall<O> call) {
        return LanguageModelMiddlewares.defer(prepare(call)
            .map(prepared -> prepared.getModel().streamText(prepared.getRequest())));
    }

    private Mono<PreparedAgentCall> prepare(AgentCall<O> source) {
        return Mono.defer(() -> {
            var call = snapshot(Objects.requireNonNull(source, "call must not be null"));
            validateInput(call);
            validateOptions(call.getOptions());
            checkCancellation(call);
            var builder = requestBuilder(call);
            var context = new AgentCallPrepareContext<>(call, call.getOptions(),
                definition.getModel(), builder);
            Mono<PreparedAgentCall> prepared;
            try {
                prepared = definition.getPrepareCall() == null
                    ? Mono.just(context.prepared())
                    : definition.getPrepareCall().prepare(context);
            } catch (Throwable error) {
                return Mono.error(preparationFailure(error));
            }
            if (prepared == null) {
                return Mono.error(new AgentCallException(AgentCallPhase.PREPARATION,
                    "Agent call preparation returned null"));
            }
            return prepared
                .switchIfEmpty(Mono.error(new AgentCallException(AgentCallPhase.PREPARATION,
                    "Agent call preparation returned no prepared call")))
                .map(this::validatedPreparedCall)
                .onErrorMap(this::preparationFailure);
        });
    }

    private AgentCallException preparationFailure(Throwable error) {
        if (error instanceof AgentCallException agentError) {
            return agentError;
        }
        if (error instanceof AiGenerationCancelledException cancelled) {
            throw cancelled;
        }
        return new AgentCallException(AgentCallPhase.PREPARATION,
            "Agent call preparation failed: " + safeMessage(error), error);
    }

    private PreparedAgentCall validatedPreparedCall(PreparedAgentCall prepared) {
        if (prepared == null || prepared.getModel() == null || prepared.getRequest() == null) {
            throw new AgentCallException(AgentCallPhase.PREPARATION,
                "Agent call preparation produced incomplete state");
        }
        var callToken = prepared.getRequest().getCancellationToken();
        if (callToken != null) {
            callToken.throwIfCancellationRequested();
        }
        return new PreparedAgentCall(prepared.getModel(), copyRequest(prepared.getRequest()));
    }

    private void validateInput(AgentCall<O> call) {
        var hasPrompt = call.getPrompt() != null && !call.getPrompt().isBlank();
        var hasMessages = call.getMessages() != null && !call.getMessages().isEmpty();
        if (hasPrompt == hasMessages) {
            throw new AgentCallException(AgentCallPhase.VALIDATION,
                "Agent call must contain either a prompt or messages, but not both");
        }
    }

    private void validateOptions(O options) {
        if (definition.getCallValidator() == null) {
            return;
        }
        try {
            definition.getCallValidator().validate(options);
        } catch (AgentCallException error) {
            throw error;
        } catch (Throwable error) {
            throw new AgentCallException(AgentCallPhase.VALIDATION,
                "Agent call options are invalid: " + safeMessage(error), error);
        }
    }

    private void checkCancellation(AgentCall<O> call) {
        if (call.getCancellationToken() != null) {
            call.getCancellationToken().throwIfCancellationRequested();
        }
    }

    private GenerateTextRequest.GenerateTextRequestBuilder requestBuilder(AgentCall<O> call) {
        var lifecycle = new ArrayList<>(definition.getLifecycle());
        lifecycle.addAll(call.getLifecycle());
        var middleware = new ArrayList<>(definition.getMiddleware());
        middleware.addAll(call.getMiddleware());
        var builder = GenerateTextRequest.builder()
            .system(definition.getInstructions())
            .maxOutputTokens(definition.getMaxOutputTokens())
            .temperature(definition.getTemperature())
            .topP(definition.getTopP())
            .topK(definition.getTopK())
            .minP(definition.getMinP())
            .presencePenalty(definition.getPresencePenalty())
            .frequencyPenalty(definition.getFrequencyPenalty())
            .repetitionPenalty(definition.getRepetitionPenalty())
            .logprobs(definition.getLogprobs())
            .topLogprobs(definition.getTopLogprobs())
            .parallelToolCalls(definition.getParallelToolCalls())
            .stopSequences(definition.getStopSequences())
            .seed(definition.getSeed())
            .maxRetries(definition.getMaxRetries())
            .reasoning(copy(definition.getReasoning()))
            .headers(merge(definition.getHeaders(), call.getHeaders()))
            .metadata(merge(definition.getMetadata(), call.getMetadata()))
            .context(merge(definition.getContext(), call.getContext()))
            .output(copy(definition.getOutput()))
            .tools(copyTools(definition.getTools()))
            .toolChoice(copy(definition.getToolChoice()))
            .stopWhen(definition.getStopWhen() != null
                ? definition.getStopWhen()
                : StopCondition.stepCountIs(DEFAULT_MAX_STEPS))
            .prepareStep(withActiveTools(definition.getActiveTools(),
                definition.getPrepareStep()))
            .lifecycle(composite(lifecycle))
            .toolCallRepair(definition.getToolCallRepair())
            .cancellationToken(call.getCancellationToken())
            .timeouts(merge(definition.getTimeouts(), call.getTimeouts()));
        if (!middleware.isEmpty()) {
            builder.middleware(middleware.toArray(LanguageModelMiddleware[]::new));
        }
        if (call.getPrompt() != null && !call.getPrompt().isBlank()) {
            builder.prompt(call.getPrompt());
        } else {
            builder.messages(List.copyOf(call.getMessages()));
        }
        return builder;
    }

    private PrepareStepCallback withActiveTools(List<String> activeTools,
        PrepareStepCallback delegate) {
        if (activeTools == null && delegate == null) {
            return null;
        }
        var initialActiveTools = activeTools == null ? null : List.copyOf(activeTools);
        return context -> {
            var prepared = delegate != null ? delegate.prepare(context) : null;
            if (prepared != null && prepared.getActiveTools() != null) {
                return prepared;
            }
            if (initialActiveTools == null) {
                return prepared;
            }
            return copy(prepared, initialActiveTools);
        };
    }

    private PreparedStep copy(PreparedStep source, List<String> activeTools) {
        if (source == null) {
            return PreparedStep.builder().activeTools(activeTools).build();
        }
        return PreparedStep.builder()
            .messages(source.getMessages() == null ? null : List.copyOf(source.getMessages()))
            .toolChoice(copy(source.getToolChoice()))
            .activeTools(activeTools)
            .maxOutputTokens(source.getMaxOutputTokens())
            .temperature(source.getTemperature())
            .topP(source.getTopP())
            .topK(source.getTopK())
            .minP(source.getMinP())
            .presencePenalty(source.getPresencePenalty())
            .frequencyPenalty(source.getFrequencyPenalty())
            .repetitionPenalty(source.getRepetitionPenalty())
            .logprobs(source.getLogprobs())
            .topLogprobs(source.getTopLogprobs())
            .parallelToolCalls(source.getParallelToolCalls())
            .stopSequences(source.getStopSequences() == null
                ? null : List.copyOf(source.getStopSequences()))
            .seed(source.getSeed())
            .maxRetries(source.getMaxRetries())
            .stopWhen(source.getStopWhen())
            .build();
    }

    private GenerationLifecycle composite(List<GenerationLifecycle> lifecycle) {
        if (lifecycle == null || lifecycle.isEmpty()) {
            return null;
        }
        var entries = List.copyOf(lifecycle);
        return new GenerationLifecycle() {
            @Override
            public Mono<Void> onStart(GenerationStartEvent event) {
                return invoke(entries, value -> value.onStart(event));
            }

            @Override
            public Mono<Void> onStepStart(GenerationStepStartEvent event) {
                return invoke(entries, value -> value.onStepStart(event));
            }

            @Override
            public Mono<Void> onToolCallStart(GenerationToolCallStartEvent event) {
                return invoke(entries, value -> value.onToolCallStart(event));
            }

            @Override
            public Mono<Void> onToolCallFinish(GenerationToolCallFinishEvent event) {
                return invoke(entries, value -> value.onToolCallFinish(event));
            }

            @Override
            public Mono<Void> onToolApprovalRequest(GenerationToolApprovalRequestEvent event) {
                return invoke(entries, value -> value.onToolApprovalRequest(event));
            }

            @Override
            public Mono<Void> onStepFinish(GenerationStepFinishEvent event) {
                return invoke(entries, value -> value.onStepFinish(event));
            }

            @Override
            public Mono<Void> onFinish(GenerationFinishEvent event) {
                return invoke(entries, value -> value.onFinish(event));
            }

            @Override
            public Mono<Void> onError(GenerationErrorEvent event) {
                return invoke(entries, value -> value.onError(event));
            }
        };
    }

    private Mono<Void> invoke(List<GenerationLifecycle> lifecycle,
        Function<GenerationLifecycle, Mono<Void>> callback) {
        return Flux.fromIterable(lifecycle)
            .concatMap(value -> Mono.defer(() -> callback.apply(value)))
            .then();
    }

    private GenerateTextRequest copyRequest(GenerateTextRequest source) {
        var builder = GenerateTextRequest.builder()
            .system(source.getSystem())
            .prompt(source.getPrompt())
            .messages(source.getMessages() == null ? null : List.copyOf(source.getMessages()))
            .maxOutputTokens(source.getMaxOutputTokens())
            .temperature(source.getTemperature())
            .topP(source.getTopP())
            .topK(source.getTopK())
            .minP(source.getMinP())
            .presencePenalty(source.getPresencePenalty())
            .frequencyPenalty(source.getFrequencyPenalty())
            .repetitionPenalty(source.getRepetitionPenalty())
            .logprobs(source.getLogprobs())
            .topLogprobs(source.getTopLogprobs())
            .parallelToolCalls(source.getParallelToolCalls())
            .stopSequences(source.getStopSequences() == null
                ? null : List.copyOf(source.getStopSequences()))
            .seed(source.getSeed())
            .maxRetries(source.getMaxRetries())
            .reasoning(copy(source.getReasoning()))
            .headers(immutableMap(source.getHeaders()))
            .metadata(immutableMap(source.getMetadata()))
            .context(immutableMap(source.getContext()))
            .output(copy(source.getOutput()))
            .tools(copyTools(source.getTools()))
            .toolChoice(copy(source.getToolChoice()))
            .stopWhen(source.getStopWhen())
            .prepareStep(source.getPrepareStep())
            .lifecycle(source.getLifecycle())
            .toolCallRepair(source.getToolCallRepair())
            .cancellationToken(source.getCancellationToken())
            .timeouts(source.getTimeouts());
        if (source.getMiddleware() != null && !source.getMiddleware().isEmpty()) {
            builder.middleware(source.getMiddleware().toArray(LanguageModelMiddleware[]::new));
        }
        return builder.build();
    }

    private static <O> AgentOptions<O> snapshot(AgentOptions<O> source) {
        return source.toBuilder()
            .tools(copyTools(source.getTools()))
            .activeTools(copyNullableList(source.getActiveTools()))
            .toolChoice(copy(source.getToolChoice()))
            .output(copy(source.getOutput()))
            .reasoning(copy(source.getReasoning()))
            .stopSequences(copyList(source.getStopSequences()))
            .headers(immutableMap(source.getHeaders()))
            .metadata(immutableMap(source.getMetadata()))
            .context(immutableMap(source.getContext()))
            .middleware(copyList(source.getMiddleware()))
            .lifecycle(copyList(source.getLifecycle()))
            .build();
    }

    private static <O> AgentCall<O> snapshot(AgentCall<O> source) {
        return AgentCall.<O>builder()
            .prompt(source.getPrompt())
            .messages(source.getMessages())
            .options(source.getOptions())
            .metadata(source.getMetadata())
            .context(source.getContext())
            .headers(source.getHeaders())
            .cancellationToken(source.getCancellationToken())
            .timeouts(source.getTimeouts())
            .lifecycle(source.getLifecycle())
            .middleware(source.getMiddleware())
            .build();
    }

    private static List<ToolDefinition> copyTools(List<ToolDefinition> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream().map(Agent::copy).toList();
    }

    private static ToolDefinition copy(ToolDefinition source) {
        if (source == null) {
            return null;
        }
        return ToolDefinition.builder()
            .name(source.getName())
            .description(source.getDescription())
            .inputSchema(deepMap(source.getInputSchema()))
            .outputSchema(deepMap(source.getOutputSchema()))
            .inputExamples(source.getInputExamples() == null ? null
                : source.getInputExamples().stream().map(Agent::deepMap).toList())
            .strict(source.getStrict())
            .approvalPolicy(source.getApprovalPolicy())
            .onInputStart(source.getOnInputStart())
            .onInputDelta(source.getOnInputDelta())
            .onInputAvailable(source.getOnInputAvailable())
            .executor(source.getExecutor())
            .build();
    }

    private static ToolChoice copy(ToolChoice source) {
        return source == null ? null : ToolChoice.builder()
            .type(source.getType())
            .toolName(source.getToolName())
            .build();
    }

    private static ReasoningOptions copy(ReasoningOptions source) {
        return source == null ? null : ReasoningOptions.builder()
            .mode(source.getMode())
            .effort(source.getEffort())
            .build();
    }

    private static OutputSpec copy(OutputSpec source) {
        return source == null ? null : OutputSpec.builder()
            .type(source.getType())
            .name(source.getName())
            .description(source.getDescription())
            .schema(deepMap(source.getSchema()))
            .elementSchema(deepMap(source.getElementSchema()))
            .choices(copyList(source.getChoices()))
            .strict(source.getStrict())
            .outputClass(source.getOutputClass())
            .elementClass(source.getElementClass())
            .build();
    }

    private static GenerationTimeouts merge(GenerationTimeouts base, GenerationTimeouts call) {
        if (call == null) {
            return base;
        }
        if (base == null) {
            return call;
        }
        return GenerationTimeouts.builder()
            .totalTimeout(call.getTotalTimeout() != null
                ? call.getTotalTimeout() : base.getTotalTimeout())
            .stepTimeout(call.getStepTimeout() != null
                ? call.getStepTimeout() : base.getStepTimeout())
            .toolTimeout(call.getToolTimeout() != null
                ? call.getToolTimeout() : base.getToolTimeout())
            .build();
    }

    private static <K, V> Map<K, V> merge(Map<K, V> base, Map<K, V> call) {
        var merged = new LinkedHashMap<K, V>();
        if (base != null) {
            merged.putAll(base);
        }
        if (call != null) {
            merged.putAll(call);
        }
        return immutableMap(merged);
    }

    private static <T> List<T> copyList(List<T> source) {
        return source == null || source.isEmpty() ? List.of() : List.copyOf(source);
    }

    private static <T> List<T> copyNullableList(List<T> source) {
        return source == null ? null : List.copyOf(source);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return source == null ? null : Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> copy.put(key, deepValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object deepValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<Object, Object>();
            map.forEach((key, nested) -> copy.put(key, deepValue(nested)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream().map(Agent::deepValue).toList());
        }
        return value;
    }

    private String safeMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "unknown error" : error.getClass().getSimpleName();
        }
        return error.getMessage();
    }
}
