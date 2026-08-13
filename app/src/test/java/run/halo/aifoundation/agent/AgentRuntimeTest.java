package run.halo.aifoundation.agent;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.chat.LanguageModelCapabilities;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.chat.StepContext;
import run.halo.aifoundation.chat.StopCondition;
import run.halo.aifoundation.chat.StreamTextResult;
import run.halo.aifoundation.control.CancellationSource;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.tool.ToolCallFailureKind;
import run.halo.aifoundation.tool.ToolCallRepairContext;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

class AgentRuntimeTest {

    @Test
    void noOptionsAgentComposesFreshBoundedRequest() {
        var model = new RecordingModel("base");
        var agent = Agent.create(model, "Be concise");

        StepVerifier.create(agent.generate(AgentCall.prompt("Hello")))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("base"))
            .verifyComplete();

        var request = model.requests.getFirst();
        assertThat(request.getSystem()).isEqualTo("Be concise");
        assertThat(request.getPrompt()).isEqualTo("Hello");
        assertThat(request.getStopWhen()).isNotNull();
        assertThat(request.getStopWhen().shouldContinue(StepContext.builder()
            .stepIndex(18)
            .build())).isTrue();
        assertThat(request.getStopWhen().shouldContinue(StepContext.builder()
            .stepIndex(19)
            .build())).isFalse();
    }

    @Test
    void typedValidationRunsBeforeOneTimePreparationAndCanReplaceModel() {
        record CallOptions(String profile) {
        }
        var base = new RecordingModel("base");
        var selected = new RecordingModel("selected");
        var order = new CopyOnWriteArrayList<String>();
        var agent = Agent.create(AgentOptions.forModel(base, CallOptions.class)
            .instructions("base instructions")
            .callValidator(options -> {
                order.add("validate");
                if (options == null || options.profile().isBlank()) {
                    throw new IllegalArgumentException("profile is required");
                }
            })
            .prepareCall(context -> {
                order.add("prepare");
                context.getRequestBuilder().system("profile=" + context.getOptions().profile());
                return Mono.just(context.prepared(selected));
            })
            .build());

        StepVerifier.create(agent.generate(AgentCall.prompt("Hello", new CallOptions("fast"))))
            .assertNext(result -> assertThat(result.getText()).isEqualTo("selected"))
            .verifyComplete();

        assertThat(order).containsExactly("validate", "prepare");
        assertThat(base.requests).isEmpty();
        assertThat(selected.requests.getFirst().getSystem()).isEqualTo("profile=fast");
    }

    @Test
    void validationAndPreparationFailuresArePhaseSpecificAndPreProvider() {
        var model = new RecordingModel("unused");
        var preparations = new AtomicInteger();
        var agent = Agent.create(AgentOptions.forModel(model, String.class)
            .callValidator(options -> {
                if (!"valid".equals(options)) {
                    throw new IllegalArgumentException("invalid profile");
                }
            })
            .prepareCall(context -> {
                preparations.incrementAndGet();
                return Mono.error(new IllegalStateException("lookup failed"));
            })
            .build());

        StepVerifier.create(agent.generate(AgentCall.prompt("Hello", "invalid")))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(AgentCallException.class);
                assertThat(((AgentCallException) error).getPhase())
                    .isEqualTo(AgentCallPhase.VALIDATION);
            })
            .verify();
        assertThat(preparations).hasValue(0);

        StepVerifier.create(agent.generate(AgentCall.prompt("Hello", "valid")))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(AgentCallException.class);
                assertThat(((AgentCallException) error).getPhase())
                    .isEqualTo(AgentCallPhase.PREPARATION);
            })
            .verify();
        assertThat(model.requests).isEmpty();
    }

    @Test
    void definitionAndCallControlsComposeWithDocumentedPrecedence() {
        var model = new RecordingModel("ok");
        var definitionMiddleware = new NoopMiddleware();
        var callMiddleware = new NoopMiddleware();
        var definitionContext = new LinkedHashMap<String, Object>();
        definitionContext.put("shared", "definition");
        definitionContext.put("nullable", null);
        var agent = Agent.create(AgentOptions.forModel(model)
            .headers(Map.of("shared", "definition", "definition", "yes"))
            .metadata(Map.of("definition", true))
            .context(definitionContext)
            .middleware(List.of(definitionMiddleware))
            .timeouts(run.halo.aifoundation.chat.GenerationTimeouts.builder()
                .stepTimeout(Duration.ofSeconds(20))
                .toolTimeout(Duration.ofSeconds(10))
                .build())
            .prepareCall(context -> {
                context.getRequestBuilder().temperature(0.25);
                return Mono.just(context.prepared());
            })
            .build());
        var call = AgentCall.<Void>builder()
            .prompt("Hello")
            .headers(Map.of("shared", "call"))
            .metadata(Map.of("call", true))
            .context(Collections.singletonMap("nullable", null))
            .middleware(List.of(callMiddleware))
            .timeouts(run.halo.aifoundation.chat.GenerationTimeouts.builder()
                .totalTimeout(Duration.ofMinutes(1))
                .build())
            .build();

        agent.generate(call).block();

        var request = model.requests.getFirst();
        assertThat(request.getHeaders())
            .containsEntry("shared", "call")
            .containsEntry("definition", "yes");
        assertThat(request.getMetadata()).containsKeys("definition", "call");
        assertThat(request.getContext()).containsEntry("nullable", null);
        assertThat(request.getTemperature()).isEqualTo(0.25);
        assertThat(request.getMiddleware())
            .containsExactly(definitionMiddleware, callMiddleware);
        assertThat(request.getTimeouts().getTotalTimeout()).isEqualTo(Duration.ofMinutes(1));
        assertThat(request.getTimeouts().getStepTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(request.getTimeouts().getToolTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void definitionAndReturnedOptionsAreDefensiveSnapshots() {
        var model = new RecordingModel("ok");
        var tool = ToolDefinition.builder()
            .name("weather")
            .inputSchema(Map.of("type", "object"))
            .build();
        var tools = new ArrayList<>(List.of(tool));
        var agent = Agent.create(AgentOptions.forModel(model)
            .tools(tools)
            .activeTools(List.of("weather"))
            .build());

        tools.clear();
        tool.setName("changed");
        agent.options().getTools().getFirst().setName("returned-snapshot-change");
        agent.generate(AgentCall.prompt("Hello")).block();

        assertThat(model.requests.getFirst().getTools())
            .extracting(ToolDefinition::getName)
            .containsExactly("weather");
        assertThat(model.requests.getFirst().getPrepareStep().prepare(
            StepContext.builder().stepIndex(0).build()).getActiveTools())
            .containsExactly("weather");
    }

    @Test
    void activeToolsDistinguishesUnspecifiedFromExplicitlyEmpty() {
        var model = new RecordingModel("ok");
        var unspecified = Agent.create(AgentOptions.forModel(model)
            .prepareStep(context -> null)
            .build());
        var disabled = Agent.create(AgentOptions.forModel(model)
            .activeTools(List.of())
            .build());

        unspecified.generate(AgentCall.prompt("all tools remain available")).block();
        disabled.generate(AgentCall.prompt("no tools are available")).block();

        var unspecifiedRequest = model.requests.get(0);
        var disabledRequest = model.requests.get(1);
        assertThat(unspecified.options().getActiveTools()).isNull();
        assertThat(unspecifiedRequest.getPrepareStep().prepare(
            StepContext.builder().stepIndex(0).build())).isNull();
        assertThat(disabled.options().getActiveTools()).isEmpty();
        assertThat(disabledRequest.getPrepareStep().prepare(
            StepContext.builder().stepIndex(0).build()).getActiveTools()).isEmpty();
    }

    @Test
    void streamViewsSharePreparationAndStreamInvocation() {
        var model = new RecordingModel("streamed");
        var preparations = new AtomicInteger();
        var agent = Agent.create(AgentOptions.forModel(model)
            .prepareCall(context -> {
                preparations.incrementAndGet();
                return Mono.just(context.prepared());
            })
            .build());
        var stream = agent.stream(AgentCall.prompt("Hello"));

        assertThat(stream.textStream().collectList().block()).containsExactly("streamed");
        assertThat(stream.result().block().getText()).isEqualTo("streamed");
        assertThat(stream.fullStream().collectList().block()).isNotEmpty();
        assertThat(preparations).hasValue(1);
        assertThat(model.streamCalls).hasValue(1);
    }

    @Test
    void customStopPolicyAndSemanticSettingsReachGenerateAndStreamUnchanged() {
        var model = new RecordingModel("same result");
        StopCondition customStop = context -> context.getStepIndex() < 2;
        var tool = ToolDefinition.builder().name("lookup").build();
        var recovery = (run.halo.aifoundation.tool.ToolCallRepairCallback) context ->
            Mono.just(ToolCallRepairResult.unrepaired());
        var output = OutputSpec.json();
        var agent = Agent.create(AgentOptions.forModel(model)
            .instructions("policy")
            .tools(List.of(tool))
            .toolChoice(ToolChoice.required())
            .output(output)
            .stopWhen(customStop)
            .toolCallRepair(recovery)
            .reasoning(ReasoningOptions.disabled())
            .maxOutputTokens(300)
            .temperature(0.2)
            .topP(0.9)
            .topK(20)
            .minP(0.05)
            .presencePenalty(0.1)
            .frequencyPenalty(0.2)
            .repetitionPenalty(1.1)
            .logprobs(true)
            .topLogprobs(3)
            .parallelToolCalls(false)
            .stopSequences(List.of("END"))
            .seed(7)
            .maxRetries(1)
            .build());

        var generated = agent.generate(AgentCall.prompt("Hello")).block();
        var streamed = agent.stream(AgentCall.prompt("Hello")).result().block();

        assertThat(generated).usingRecursiveComparison().isEqualTo(streamed);
        assertThat(model.requests).hasSize(2).allSatisfy(request -> {
            assertThat(request.getStopWhen()).isSameAs(customStop);
            assertThat(request.getToolCallRepair()).isSameAs(recovery);
            assertThat(request.getTools()).extracting(ToolDefinition::getName)
                .containsExactly("lookup");
            assertThat(request.getToolChoice().getType()).isEqualTo(ToolChoice.Type.REQUIRED);
            assertThat(request.getOutput().getType()).isEqualTo(output.getType());
            assertThat(request.getReasoning().getMode())
                .isEqualTo(ReasoningOptions.Mode.DISABLED);
            assertThat(request.getMaxOutputTokens()).isEqualTo(300);
            assertThat(request.getTemperature()).isEqualTo(0.2);
            assertThat(request.getStopSequences()).containsExactly("END");
            assertThat(request.getSeed()).isEqualTo(7);
            assertThat(request.getMaxRetries()).isEqualTo(1);
        });
    }

    @Test
    void concurrentCallsKeepOptionsAndCancellationIsolated() {
        record CallOptions(String value) {
        }
        var model = new RecordingModel("ok");
        var agent = Agent.create(AgentOptions.forModel(model, CallOptions.class)
            .prepareCall(context -> {
                context.getRequestBuilder().system(context.getOptions().value());
                return Mono.just(context.prepared());
            })
            .build());
        var cancelled = new CancellationSource();
        cancelled.cancel();

        StepVerifier.create(Flux.merge(
                agent.generate(AgentCall.prompt("one", new CallOptions("one"))),
                agent.generate(AgentCall.<CallOptions>builder()
                    .prompt("cancelled")
                    .options(new CallOptions("cancelled"))
                    .cancellationToken(cancelled.token())
                    .build()).onErrorResume(AiGenerationCancelledException.class, error ->
                        Mono.empty()),
                agent.generate(AgentCall.prompt("two", new CallOptions("two"))))
            .collectList())
            .assertNext(results -> assertThat(results).hasSize(2))
            .verifyComplete();

        assertThat(model.requests)
            .extracting(GenerateTextRequest::getSystem)
            .containsExactlyInAnyOrder("one", "two");
    }

    @Test
    void publicAgentAndRecoveryContractsDoNotExposeImplementationTypes() {
        var publicTypes = List.of(
            Agent.class,
            AgentOptions.class,
            AgentCall.class,
            AgentCallValidator.class,
            AgentCallPrepare.class,
            AgentCallPrepareContext.class,
            PreparedAgentCall.class,
            ToolCallRepairContext.class,
            ToolCallFailureKind.class
        );

        var signatures = publicTypes.stream()
            .flatMap(type -> Flux.concat(
                    Flux.fromArray(type.getDeclaredFields()).map(Field::getGenericType),
                    Flux.fromArray(type.getDeclaredMethods())
                        .flatMap(method -> Flux.concat(
                            Flux.just(method.getGenericReturnType()),
                            Flux.fromArray(method.getGenericParameterTypes()))),
                    Flux.fromArray(type.getDeclaredConstructors())
                        .flatMap(constructor -> Flux.fromArray(
                            constructor.getGenericParameterTypes())))
                .toStream())
            .map(Type::getTypeName)
            .toList();

        assertThat(signatures)
            .noneMatch(name -> name.startsWith("org.springframework")
                || name.contains("run.halo.aifoundation.service")
                || name.contains("run.halo.aifoundation.provider"));
    }

    private static final class NoopMiddleware implements
        run.halo.aifoundation.chat.middleware.LanguageModelMiddleware {
    }

    private static final class RecordingModel implements LanguageModel {
        private final String text;
        private final List<GenerateTextRequest> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger streamCalls = new AtomicInteger();

        private RecordingModel(String text) {
            this.text = text;
        }

        @Override
        public Mono<GenerateTextResult> generateText(String prompt) {
            return generateText(GenerateTextRequest.builder().prompt(prompt).build());
        }

        @Override
        public Mono<GenerateTextResult> generateText(GenerateTextRequest request) {
            return Mono.defer(() -> {
                requests.add(request);
                return Mono.just(result());
            });
        }

        @Override
        public StreamTextResult streamText(GenerateTextRequest request) {
            requests.add(request);
            streamCalls.incrementAndGet();
            var full = Flux.just(
                TextStreamPart.start("message-1"),
                TextStreamPart.textStart("text-1"),
                TextStreamPart.textDelta("text-1", text),
                TextStreamPart.textEnd("text-1")
            ).cache();
            return new StreamTextResult(full, Flux.just(text), Flux.empty(), Flux.empty(),
                Mono.empty(), Mono.just(result()));
        }

        @Override
        public LanguageModelCapabilities capabilities() {
            return LanguageModelCapabilities.defaults();
        }

        private GenerateTextResult result() {
            return GenerateTextResult.builder()
                .text(text)
                .steps(List.of())
                .warnings(List.of())
                .responseMessages(List.of(ModelMessage.assistant(text)))
                .build();
        }
    }
}
