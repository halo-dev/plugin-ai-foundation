package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.service.model.ModelRuntimeContext;
import run.halo.aifoundation.tool.ToolChoice;
import run.halo.aifoundation.tool.ToolDefinition;

class LanguageModelParameterConstraintsTest {
    @Test
    void outputLimitAppliesBeforeNormalAndStreamingProviderRequestsIncludingPreparedSteps() {
        for (var prepared : List.of(false, true)) {
            var constraints = new ModelParameterConstraints();
            constraints.setOutputTokens(100L);
            var chat = mock(ChatModel.class);
            var model = model(chat, context(constraints, RuntimeParameterMappings.empty(),
                Map.of()));
            var request = GenerateTextRequest.builder().prompt("Hello")
                .maxOutputTokens(prepared ? 50 : 101)
                .prepareStep(prepared
                    ? step -> PreparedStep.builder().maxOutputTokens(101).build() : null)
                .build();
            StepVerifier.create(model.generateText(request))
                .expectErrorMessage("maxOutputTokens must not exceed model limit 100")
                .verify();
            var errors = model.streamText(request).fullStream()
                .filter(part -> PartType.ERROR.equals(part.getType())).collectList().block();
            assertThat(errors).singleElement().satisfies(error -> assertThat(error.getErrorText())
                .isEqualTo("maxOutputTokens must not exceed model limit 100"));
            verifyNoInteractions(chat);
        }
    }

    @Test
    void explicitToolChoicesAreValidatedIncludingEmptyAllowedListAndPreparedOverrides() {
        var constraints = new ModelParameterConstraints();
        constraints.setToolChoices(List.of("none"));
        var chat = mock(ChatModel.class);
        var model = model(chat, context(constraints, RuntimeParameterMappings.empty(), Map.of()));
        var request = GenerateTextRequest.builder().prompt("Hello").toolChoice(ToolChoice.none())
            .prepareStep(step -> PreparedStep.builder().toolChoice(ToolChoice.auto()).build())
                .build();
        StepVerifier.create(model.generateText(request))
            .expectErrorMessage(
                "toolChoice auto is not supported by this model; allowed values: [none]")
            .verify();
        constraints.setToolChoices(List.of());
        var validator = new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of()));
        assertThatThrownBy(() -> validator.validate(request))
            .hasMessageContaining("toolChoice none is not supported");
        verifyNoInteractions(chat);
    }

    @Test
    void emptyToolChoiceUsesAutoWithoutMutatingCallerOptions() {
        var constraints = new ModelParameterConstraints();
        constraints.setToolChoices(List.of("auto"));
        var validator = new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of()));
        for (var choice : List.of(new ToolChoice(), ToolChoice.builder().build())) {
            var request = GenerateTextRequest.builder().prompt("Hello").toolChoice(choice).build();

            assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
            assertThat(choice.getType()).isNull();
        }

        constraints.setToolChoices(List.of());
        var requestWithoutChoice = GenerateTextRequest.builder().prompt("Hello").build();
        assertThatCode(() -> validator.validate(requestWithoutChoice))
            .doesNotThrowAnyException();
    }

    @Test
    void emptyToolChoiceStillRejectsDisallowedAutoForNormalStreamingAndPreparedRequests() {
        for (var prepared : List.of(false, true)) {
            for (var choice : List.of(new ToolChoice(), ToolChoice.builder().build())) {
                var constraints = new ModelParameterConstraints();
                constraints.setToolChoices(List.of("none"));
                var chat = mock(ChatModel.class);
                var model = model(chat, context(constraints, RuntimeParameterMappings.empty(),
                    Map.of()));
                var request = GenerateTextRequest.builder().prompt("Hello")
                    .toolChoice(prepared ? ToolChoice.none() : choice)
                    .prepareStep(prepared
                        ? step -> PreparedStep.builder().toolChoice(choice).build() : null)
                    .build();
                var message = "toolChoice auto is not supported by this model; "
                    + "allowed values: [none]";

                StepVerifier.create(model.generateText(request))
                    .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class).hasMessage(message))
                    .verify();
                var errors = model.streamText(request).fullStream()
                    .filter(part -> PartType.ERROR.equals(part.getType())).collectList().block();
                assertThat(errors).singleElement()
                    .satisfies(error -> assertThat(error.getErrorText()).isEqualTo(message));
                verifyNoInteractions(chat);
            }
        }
    }

    @Test
    void reasoningBudgetIsValidatedBeforeApplyingAnExplicitIntent() {
        var constraints = new ModelParameterConstraints();
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setBudgetMinimum(512L);
        reasoning.setBudgetMaximum(4096L);
        constraints.setReasoning(reasoning);
        var runtime = new RuntimeParameterMappings(new EffectiveParameterMappings(Map.of(
            ModelParameter.REASONING, new EffectiveParameterMappings.EffectiveMapping(
                ModelParameterMappings.Mode.TEMPLATE, "reasoning.thinking-budget", null,
                EffectiveParameterMappings.Source.CATALOG))), null, "model", "provider");
        var validator = new LanguageModelParameterValidator(
            context(constraints, runtime, Map.of()));
        assertThatThrownBy(() -> validator.validate(GenerateTextRequest.builder().prompt("Hello")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.LOW)).build()))
            .hasMessage("reasoning budget must be at least 512");
        validator.validate(GenerateTextRequest.builder().prompt("Hello").build());
        validator.validate(GenerateTextRequest.builder().prompt("Hello")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)).build());
    }

    @Test
    void nativeOptionsRespectExplicitCapabilitiesAndAllowedServiceTiers() {
        var constraints = new ModelParameterConstraints();
        constraints.setStore(false);
        constraints.setServiceTiers(List.of());
        assertThatThrownBy(() -> new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of())).validateRequestBody(Map.of("store", true)))
            .hasMessage("store is not supported by this model");
        assertThatThrownBy(() -> new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of())).validateRequestBody(
                Map.of("service_tier", "priority")))
            .hasMessageContaining("service_tier priority is not supported");
    }

    @Test
    void catalogServiceTiersPreserveAutoDefaultAndUnknownNativeValues() {
        var constraints = new ModelParameterConstraints();
        constraints.setServiceTiers(List.of());
        for (var tier : List.of("auto", "default", "provider-specific-tier")) {
            new LanguageModelParameterValidator(context(constraints,
                RuntimeParameterMappings.empty(),
                Map.of())).validateRequestBody(Map.of("service_tier", tier));
        }
        for (var tier : List.of("flex", "priority", "fast", "ultrafast", "standby")) {
            assertThatThrownBy(() -> new LanguageModelParameterValidator(context(constraints,
                RuntimeParameterMappings.empty(), Map.of()))
                    .validateRequestBody(Map.of("service_tier", tier)))
                .hasMessageContaining("service_tier " + tier + " is not supported");
        }
        constraints.setServiceTiers(List.of("priority"));
        new LanguageModelParameterValidator(context(constraints, RuntimeParameterMappings.empty(),
            Map.of())).validateRequestBody(Map.of("service_tier", "priority"));
    }

    @Test
    void unsupportedNativeStructuredOutputUsesPromptGuidanceWithoutNativeFormat() {
        var constraints = new ModelParameterConstraints();
        constraints.setStructuredOutput(false);
        var chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(
            List.of(new Generation(new AssistantMessage("{}")))));
        var model = model(chat, context(constraints, RuntimeParameterMappings.empty(), Map.of()));
        var result = model.generateText(GenerateTextRequest.builder().prompt("Hello")
            .output(OutputSpec.json()).build()).block();
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).call(captor.capture());
        assertThat(((ChatCompletionsOptions) captor.getValue().getOptions()).getResponseFormat())
            .isNull();
        assertThat(result.getWarnings()).anySatisfy(warning -> assertThat(warning.getCode())
            .isEqualTo("structured-output-prompt-guidance"));
    }

    @Test
    void unsupportedNativeToolStrictPreservesLocalSchemaAndCallerDefinition() {
        var constraints = new ModelParameterConstraints();
        constraints.setToolStrict(false);
        var tool = ToolDefinition.builder().name("lookup").strict(true)
            .inputSchema(Map.of("type", "object")).build();
        var validator = new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of()));
        var nativeTool = validator.nativeTools(List.of(tool)).getFirst();
        assertThat(nativeTool.getStrict()).isNull();
        assertThat(nativeTool.getInputSchema()).isEqualTo(tool.getInputSchema());
        assertThat(tool.getStrict()).isTrue();
    }

    @Test
    void nativeReasoningControlsRespectDeclaredEffortsAndToggleSupport() {
        var constraints = new ModelParameterConstraints();
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setEfforts(List.of("high"));
        reasoning.setToggle(false);
        constraints.setReasoning(reasoning);
        for (var options : List.<Map<String, Object>>of(Map.of("reasoning_effort", "low"),
            Map.of("reasoning", Map.of("effort", "medium")),
            Map.of("thinking", Map.of("type", "enabled")))) {
            assertThatThrownBy(() -> new LanguageModelParameterValidator(context(constraints,
                RuntimeParameterMappings.empty(), options)).validateRequestBody(options))
                .hasMessageContaining("is not supported by this model");
        }
        new LanguageModelParameterValidator(context(constraints, RuntimeParameterMappings.empty(),
            Map.of("reasoning", Map.of("effort", "high")))).validateRequestBody(
                Map.of("reasoning", Map.of("effort", "high")));
    }

    @Test
    void streamUsageConstraintDisablesProtocolDefaultUsageRequest() {
        var constraints = new ModelParameterConstraints();
        constraints.setStreamUsage(false);
        var validator = new LanguageModelParameterValidator(context(constraints,
            RuntimeParameterMappings.empty(), Map.of()));
        var builder = ChatCompletionsOptions.builder();
        validator.applyNativeConstraints(builder);
        var options = builder.build().getStreamOptions();
        assertThat(options).isNotNull();
        assertThat(options.includeUsage()).isNull();
        assertThat(options.includeObfuscation()).isNull();
    }

    private ModelRuntimeContext context(ModelParameterConstraints constraints,
        RuntimeParameterMappings mappings, Map<String, Object> nativeOptions) {
        return context(constraints, mappings, nativeOptions, AdapterType.OPENAI_CHAT);
    }

    private ModelRuntimeContext context(ModelParameterConstraints constraints,
        RuntimeParameterMappings mappings, Map<String, Object> nativeOptions, AdapterType adapter) {
        return new ModelRuntimeContext("model", "gpt", "provider", "openai",
            adapter, new OpenAiProvider(), mappings, nativeOptions, constraints);
    }

    private LanguageModelImpl model(ChatModel chat, ModelRuntimeContext context) {
        var config = LanguageModelRuntimeConfiguration.from(context, ModelCapabilities.empty());
        var composition = LanguageModelRuntimeComposition.create(config,
            new LanguageModelRuntimeSupport(), null, null);
        return new LanguageModelImpl(chat, composition, context);
    }
}