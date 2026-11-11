package run.halo.aifoundation.service.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.PreparedStep;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.extension.ModelParameterConstraints;
import run.halo.aifoundation.extension.ModelParameterMappings;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.dashscope.DashScopeChatModel;
import run.halo.aifoundation.provider.dashscope.DashScopeMessagesModel;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekMessagesModel;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.kimi.KimiChatModel;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappingResolver;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.ollama.OllamaChatModel;
import run.halo.aifoundation.provider.ollama.OllamaChatOptions;
import run.halo.aifoundation.provider.ollama.OllamaProvider;
import run.halo.aifoundation.provider.openai.OpenAiChatModel;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openai.OpenAiResponsesModel;
import run.halo.aifoundation.provider.openrouter.OpenRouterChatModel;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.provider.support.NativeModelOptionsValidator;
import run.halo.aifoundation.provider.support.ProviderRequestValidation;
import run.halo.aifoundation.provider.transport.ProviderHttpException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.service.model.ModelResolution;
import run.halo.aifoundation.service.model.ModelRuntimeContext;

class LanguageModelWireParameterConstraintsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void nativeSamplingCannotBypassUnsupportedModelParametersAcrossProtocols() {
        for (var adapter : samplingAdapters()) {
            for (var field : List.of("temperature", "top_p", "top_k")) {
                for (var prepared : List.of(false, true)) {
                    var values = Map.<String, Object>of(field, samplingValues().get(field));
                    var nativeOptions = samplingOptions(adapter, values);
                    NativeModelOptionsValidator.validate(nativeOptions);
                    var probe =
                            probe(context(adapter, samplingConstraints(false), unsupportedSampling(), nativeOptions));
                    assertRejected(
                            probe,
                            samplingRequest(prepared),
                            (adapter == AdapterType.OLLAMA_CHAT ? "options." : "") + field
                                    + " is not supported by this model");
                }
            }
        }
    }

    @Test
    void filteredSdkSamplingDoesNotRejectRequestsWhenThereAreNoNativeDefaults() {
        for (var adapter : samplingAdapters()) {
            for (var prepared : List.of(false, true)) {
                var probe = probe(context(adapter, samplingConstraints(false), unsupportedSampling(), Map.of()));
                assertAccepted(probe, samplingRequest(prepared));
                assertThat(probe.bodies()).allSatisfy(body -> {
                    var sampling = samplingBody(adapter, body);
                    assertThat(sampling.containsKey("temperature")).isFalse();
                    assertThat(sampling.containsKey("top_p")).isFalse();
                    assertThat(sampling.containsKey("top_k")).isFalse();
                });
            }
        }
    }

    @Test
    void nativeSamplingRemainsAvailableForSupportedAndUnknownModelFacts() {
        for (var adapter : samplingAdapters()) {
            for (var constraints : List.of(samplingConstraints(true), new ModelParameterConstraints())) {
                var probe = probe(context(
                        adapter,
                        constraints,
                        RuntimeParameterMappings.empty(),
                        samplingOptions(adapter, samplingValues())));
                assertAccepted(probe, request(null, false, null));
                assertThat(probe.bodies()).allSatisfy(body -> {
                    var sampling = samplingBody(adapter, body);
                    samplingValues()
                            .forEach((field, value) ->
                                    assertThat(sampling.get(field)).isEqualTo(value));
                });
            }
        }
    }

    @Test
    void administratorMappingsOverrideCatalogFactsAndSdkValuesOverrideNativeDefaults() {
        for (var modelOverride : List.of(false, true)) {
            for (var prepared : List.of(false, true)) {
                var provider = new AiProvider();
                var providerSpec = new AiProvider.AiProviderSpec();
                providerSpec.setProviderType("openai");
                provider.setSpec(providerSpec);
                var model = new AiModel();
                var spec = new AiModel.AiModelSpec();
                spec.setModelType(ModelType.LANGUAGE);
                spec.setModelId("test-model");
                spec.setProviderName("provider");
                spec.setAdapterType(AdapterType.OPENAI_CHAT);
                model.setSpec(spec);
                var profile = new ModelParameterProfile();
                profile.setProviderType("openai");
                profile.setProviderBaseUrl("https://api.openai.com/v1");
                profile.setModelId("test-model");
                profile.setAdapterType(AdapterType.OPENAI_CHAT);
                profile.setConstraints(samplingConstraints(false));
                spec.setCatalogParameters(profile);

                var resolver = new EffectiveParameterMappingResolver();
                var resolution = new ModelResolution(model, provider, new OpenAiProvider(), null);
                assertThat(resolver.applicableCatalogProfile(resolution)).isSameAs(profile);
                var catalogConstraints = resolver.resolveConstraints(resolution);
                assertThat(catalogConstraints.getTemperature()).isFalse();
                assertThat(catalogConstraints.getTopP()).isFalse();
                assertThat(catalogConstraints.getTopK()).isFalse();

                var overrides = new ModelParameterMappings();
                var language = new ModelParameterMappings.LanguageMappings();
                language.setTemperature(selection("chat.temperature"));
                language.setTopP(selection("chat.top-p"));
                language.setTopK(selection("chat.top-k"));
                overrides.setLanguage(language);
                if (modelOverride) {
                    spec.setParameterMappings(overrides);
                } else {
                    providerSpec.setParameterMappings(overrides);
                }
                var resolved = resolver.resolveProfile(resolution);
                assertThat(resolved.getConstraints().getTemperature()).isNull();
                assertThat(resolved.getConstraints().getTopP()).isNull();
                assertThat(resolved.getConstraints().getTopK()).isNull();
                var mappings = new RuntimeParameterMappings(resolver.resolve(resolution), null, "model", "provider");
                var probe =
                        probe(context(AdapterType.OPENAI_CHAT, resolved.getConstraints(), mappings, samplingValues()));
                assertAccepted(probe, samplingRequest(prepared));
                assertThat(probe.bodies())
                        .allSatisfy(body -> assertThat(body)
                                .containsEntry("temperature", 0.7)
                                .containsEntry("top_p", 0.9)
                                .containsEntry("top_k", 40));
            }
        }
    }

    @Test
    void nativeStructuredFormatsCannotBypassPromptOnlyModelCapabilities() {
        for (var adapter :
                List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES, AdapterType.DASHSCOPE_MESSAGES)) {
            var constraints = new ModelParameterConstraints();
            constraints.setStructuredOutput(false);
            var nativeOptions = structuredOptions(adapter);
            NativeModelOptionsValidator.validate(nativeOptions);
            var field =
                    switch (adapter) {
                        case OPENAI_CHAT -> "response_format";
                        case OPENAI_RESPONSES -> "text.format";
                        default -> "output_config.format";
                    };
            for (var prepared : List.of(false, true)) {
                var probe = probe(context(adapter, constraints, RuntimeParameterMappings.empty(), nativeOptions));
                assertRejected(
                        probe,
                        request(null, prepared, null),
                        field + " requests native structured output, which is not supported by this model");
            }
            for (var allowed : List.of(new ModelParameterConstraints(), structuredSupport())) {
                var probe = probe(context(adapter, allowed, RuntimeParameterMappings.empty(), nativeOptions));
                assertAccepted(probe, request(null, false, null));
            }
        }
    }

    @Test
    void sdkStructuredOutputStillUsesPromptFallbackAcrossProtocols() {
        for (var adapter : samplingAdapters()) {
            var constraints = new ModelParameterConstraints();
            constraints.setStructuredOutput(false);
            for (var prepared : List.of(false, true)) {
                var probe = probe(context(adapter, constraints, RuntimeParameterMappings.empty(), Map.of()));
                var request = request(null, prepared, null);
                request.setOutput(OutputSpec.json());
                assertAccepted(probe, request);
                assertThat(probe.bodies()).allSatisfy(body -> {
                    assertThat(body).doesNotContainKeys("response_format", "format", "output_config");
                    if (body.get("text") instanceof Map<?, ?> text) {
                        assertThat(text.containsKey("format")).isFalse();
                    }
                });
            }
        }
    }

    @Test
    void plainTextFormatsRemainAvailableWhenNativeStructuredOutputIsUnsupported() {
        var constraints = new ModelParameterConstraints();
        constraints.setStructuredOutput(false);
        for (var adapter : List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES)) {
            var format = Map.of("type", "text");
            var nativeOptions = adapter == AdapterType.OPENAI_CHAT
                    ? Map.<String, Object>of("response_format", format)
                    : Map.<String, Object>of("text", Map.of("format", format));
            assertAccepted(
                    probe(context(adapter, constraints, RuntimeParameterMappings.empty(), nativeOptions)),
                    request(null, false, null));
        }
    }

    @Test
    void providerRemovedNativeOptionsDoNotCauseFalseConstraintFailures() {
        var constraints = new ModelParameterConstraints();
        constraints.setTopK(false);
        constraints.setServiceTiers(List.of());
        for (var prepared : List.of(false, true)) {
            var probe = probe(context(
                    AdapterType.DEEPSEEK_MESSAGES,
                    constraints,
                    RuntimeParameterMappings.empty(),
                    Map.of("top_k", 20, "service_tier", "priority")));
            assertAccepted(probe, request(null, prepared, null));
            assertThat(probe.bodies()).allSatisfy(body -> assertThat(body).doesNotContainKeys("top_k", "service_tier"));
        }
    }

    @Test
    void disabledStorageRemainsValidWhenTheModelDoesNotSupportStoringResponses() {
        var constraints = new ModelParameterConstraints();
        constraints.setStore(false);
        for (var adapter : List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES)) {
            for (var nativeOptions : List.of(Map.<String, Object>of(), Map.<String, Object>of("store", false))) {
                for (var prepared : List.of(false, true)) {
                    var probe = probe(context(adapter, constraints, RuntimeParameterMappings.empty(), nativeOptions));
                    assertAccepted(probe, request(null, prepared, null));
                    assertThat(probe.bodies()).allSatisfy(body -> {
                        if (adapter == AdapterType.OPENAI_RESPONSES || nativeOptions.containsKey("store")) {
                            assertThat(body).containsEntry("store", false);
                        }
                    });
                }
            }
        }
    }

    @Test
    void nativeCapabilitiesAreValidatedAgainstFinalWireValues() {
        for (var adapter : List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES)) {
            var constraints = new ModelParameterConstraints();
            constraints.setStore(false);
            constraints.setPromptCacheKey(false);
            constraints.setServiceTiers(List.of());
            for (var field : List.of("store", "prompt_cache_key", "service_tier")) {
                Object value =
                        switch (field) {
                            case "store" -> true;
                            case "prompt_cache_key" -> "test-cache";
                            default -> "priority";
                        };
                for (var prepared : List.of(false, true)) {
                    assertRejected(
                            probe(context(
                                    adapter, constraints, RuntimeParameterMappings.empty(), Map.of(field, value))),
                            request(null, prepared, null),
                            field.equals("service_tier")
                                    ? "service_tier priority is not supported by this model; " + "allowed values: []"
                                    : field + " is not supported by this model");
                }
            }
        }
    }

    @Test
    void nativeOutputLimitsRejectBeforeNormalStreamingAndPreparedHttpRequests() {
        for (var option : outputOptions()) {
            var nativeOptions = Map.<String, Object>of(option.field(), 101);
            NativeModelOptionsValidator.validate(nativeOptions);
            for (var prepared : List.of(false, true)) {
                var probe = probe(
                        context(option.adapter(), outputLimit(), RuntimeParameterMappings.empty(), nativeOptions));
                assertRejected(
                        probe, request(null, prepared, null), option.field() + " must not exceed model limit 100");
            }
        }
    }

    @Test
    void sdkAndPreparedOutputOverridesAreCheckedAfterWireEncoding() {
        for (var option : outputOptions()) {
            for (var prepared : List.of(false, true)) {
                var mappings = mapping(ModelParameter.MAX_OUTPUT_TOKENS, option.template(), null);
                var probe = probe(context(option.adapter(), outputLimit(), mappings, Map.of(option.field(), 5000)));
                assertAccepted(probe, request(50, prepared, null));
                assertThat(probe.bodies()).allSatisfy(body -> assertThat(body).containsEntry(option.field(), 50));
            }
        }
    }

    @Test
    void anotherNativeTokenFieldThatSurvivesEncodingStillHasItsLimitChecked() {
        var probe = probe(context(
                AdapterType.OPENAI_CHAT,
                outputLimit(),
                mapping(ModelParameter.MAX_OUTPUT_TOKENS, "openai.max-completion-tokens", null),
                Map.of("max_tokens", 5000, "max_completion_tokens", 5000)));
        assertRejected(probe, request(50, false, null), "max_tokens must not exceed model limit 100");
    }

    @Test
    void kimiAndOpenRouterValidateOnlyTheSurvivingCompletionLimit() {
        for (var adapter : List.of(AdapterType.KIMI_CHAT, AdapterType.OPENROUTER_CHAT)) {
            for (var prepared : List.of(false, true)) {
                var probe = probe(context(
                        adapter,
                        outputLimit(),
                        mapping(ModelParameter.MAX_OUTPUT_TOKENS, "openai.max-completion-tokens", null),
                        Map.of("max_tokens", 5000)));
                assertAccepted(probe, request(50, prepared, null));
                assertThat(probe.bodies())
                        .allSatisfy(body -> assertThat(body)
                                .containsEntry("max_completion_tokens", 50)
                                .doesNotContainKey("max_tokens"));
            }
        }
    }

    @Test
    void dashScopeValidatesItsOppositeTokenFieldPrecedence() {
        for (var prepared : List.of(false, true)) {
            var rejected = probe(context(
                    AdapterType.DASHSCOPE_CHAT,
                    outputLimit(),
                    mapping(ModelParameter.MAX_OUTPUT_TOKENS, "openai.max-completion-tokens", null),
                    Map.of("max_tokens", 5000)));
            assertRejected(
                    rejected, request(50, prepared, null), "max_completion_tokens must not exceed model limit 100");
            var accepted = probe(context(
                    AdapterType.DASHSCOPE_CHAT,
                    outputLimit(),
                    mapping(ModelParameter.MAX_OUTPUT_TOKENS, "openai.max-tokens", null),
                    Map.of("max_completion_tokens", 5000)));
            assertAccepted(accepted, request(50, prepared, null));
            assertThat(accepted.bodies())
                    .allSatisfy(body -> assertThat(body)
                            .containsEntry("max_completion_tokens", 50)
                            .doesNotContainKey("max_tokens"));
        }
    }

    @Test
    void messagesOutputLimitIsCheckedAfterItsTypedFieldConversion() {
        var rejected = probe(context(
                AdapterType.DEEPSEEK_MESSAGES,
                outputLimit(),
                RuntimeParameterMappings.empty(),
                Map.of("max_tokens", 5000)));
        assertRejected(rejected, request(null, false, null), "max_tokens must not exceed model limit 100");
        var accepted = probe(context(
                AdapterType.DEEPSEEK_MESSAGES,
                outputLimit(),
                RuntimeParameterMappings.empty(),
                Map.of("max_tokens", 5000)));
        assertAccepted(accepted, request(50, true, null));
        assertThat(accepted.bodies()).allSatisfy(body -> assertThat(body).containsEntry("max_tokens", 50));
    }

    @Test
    void ollamaNativeOutputLimitIsCheckedAfterMergingNestedOptions() {
        for (var prepared : List.of(false, true)) {
            var nativeOptions = Map.<String, Object>of("options", Map.of("num_predict", 5000, "num_ctx", 8192));
            var rejected = probe(
                    context(AdapterType.OLLAMA_CHAT, outputLimit(), RuntimeParameterMappings.empty(), nativeOptions));
            assertRejected(
                    rejected, request(null, prepared, null), "options.num_predict must not exceed model limit 100");
            var accepted = probe(context(
                    AdapterType.OLLAMA_CHAT,
                    outputLimit(),
                    mapping(ModelParameter.MAX_OUTPUT_TOKENS, "ollama.num-predict", null),
                    nativeOptions));
            assertAccepted(accepted, request(50, prepared, null));
            assertThat(accepted.bodies()).allSatisfy(body -> {
                var options = (Map<?, ?>) body.get("options");
                assertThat(options.get("num_predict")).isEqualTo(50);
                assertThat(options.get("num_ctx")).isEqualTo(8192);
            });
        }
    }

    @Test
    void ollamaUnboundedOutputCannotBypassAnExplicitModelLimit() {
        for (var unlimited : List.of(-1, -2)) {
            for (var prepared : List.of(false, true)) {
                var nativeOptions = Map.<String, Object>of("options", Map.of("num_predict", unlimited));
                var rejected = probe(context(
                        AdapterType.OLLAMA_CHAT, outputLimit(), RuntimeParameterMappings.empty(), nativeOptions));
                assertRejected(
                        rejected,
                        request(null, prepared, null),
                        "options.num_predict must specify a finite output limit " + "when the model limit is 100");
                var accepted = probe(context(
                        AdapterType.OLLAMA_CHAT,
                        outputLimit(),
                        mapping(ModelParameter.MAX_OUTPUT_TOKENS, "ollama.num-predict", null),
                        nativeOptions));
                assertAccepted(accepted, request(50, prepared, null));
                assertThat(accepted.bodies())
                        .allSatisfy(body -> assertThat(((Map<?, ?>) body.get("options")).get("num_predict"))
                                .isEqualTo(50));
            }
        }
    }

    @Test
    void ollamaNativeThinkingIsValidatedAndSdkOverridesRemainUsable() {
        var constraints = reasoningConstraints();
        for (var prepared : List.of(false, true)) {
            var rejected = probe(context(
                    AdapterType.OLLAMA_CHAT, constraints, RuntimeParameterMappings.empty(), Map.of("think", "low")));
            assertRejected(rejected, request(null, prepared, null), "think value low is not supported by this model");
            var accepted = probe(context(
                    AdapterType.OLLAMA_CHAT,
                    constraints,
                    mapping(ModelParameter.REASONING, "reasoning.ollama-think", null),
                    Map.of("think", "low")));
            assertAccepted(accepted, request(null, prepared, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)));
            assertThat(accepted.bodies()).allSatisfy(body -> assertThat(body).containsEntry("think", "high"));
        }
        constraints.getReasoning().setToggle(false);
        assertRejected(
                probe(context(
                        AdapterType.OLLAMA_CHAT, constraints, RuntimeParameterMappings.empty(), Map.of("think", true))),
                request(null, false, null),
                "think value true is not supported by this model");
    }

    @Test
    void compoundReasoningOverridesAreValidatedAfterReplacingNativeValues() {
        for (var adapter : List.of(AdapterType.OPENAI_CHAT, AdapterType.OPENAI_RESPONSES)) {
            var responses = adapter == AdapterType.OPENAI_RESPONSES;
            var intent = value(
                    responses ? "reasoning.effort" : "reasoning_effort",
                    ModelParameterMappings.ValueType.STRING,
                    "high");
            intent.setAdditionalValues(List.of(value(
                    responses ? "reasoning.max_tokens" : "thinking_budget",
                    ModelParameterMappings.ValueType.INTEGER,
                    "4096")));
            var intents = new ModelParameterMappings.ReasoningMapping();
            intents.setHigh(intent);
            var mappings = mapping(
                    ModelParameter.REASONING, responses ? "reasoning.responses-effort" : "reasoning.effort", intents);
            var nativeOptions = responses
                    ? Map.<String, Object>of("reasoning", Map.of("effort", "low", "max_tokens", 1))
                    : Map.<String, Object>of("reasoning_effort", "low", "thinking_budget", 1);
            for (var prepared : List.of(false, true)) {
                var probe = probe(context(adapter, reasoningConstraints(), mappings, nativeOptions));
                assertAccepted(probe, request(null, prepared, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)));
                assertThat(probe.bodies()).allSatisfy(body -> {
                    if (responses) {
                        assertThat(body).containsEntry("reasoning", Map.of("effort", "high", "max_tokens", 4096));
                    } else {
                        assertThat(body)
                                .containsEntry("reasoning_effort", "high")
                                .containsEntry("thinking_budget", 4096);
                    }
                });
            }
        }
    }

    @Test
    void nativeReasoningBudgetCannotOverflowModelMaximum() {
        assertReasoningBudgetRejected(new BigInteger("18446744073709555712"));
    }

    @Test
    void nativeReasoningBudgetCannotTruncateFractionAboveModelMaximum() {
        assertReasoningBudgetRejected(new BigDecimal("8192.5"));
    }

    @Test
    void nativeReasoningBudgetPreservesValidValuesAndUnknownBounds() {
        for (var option : reasoningBudgetOptions()) {
            for (var prepared : List.of(false, true)) {
                var bounded = probe(context(
                        option.adapter(),
                        reasoningBudgetConstraints(),
                        RuntimeParameterMappings.empty(),
                        option.nativeOptions(4096)));
                assertAccepted(bounded, request(null, prepared, null));
                assertThat(bounded.bodies())
                        .allSatisfy(body -> assertThat(
                                        new BigDecimal(option.wireValue(body).toString()))
                                .isEqualByComparingTo("4096"));
                for (var budget : List.of(new BigInteger("18446744073709555712"), new BigDecimal("8192.5"))) {
                    var unknown = probe(context(
                            option.adapter(),
                            new ModelParameterConstraints(),
                            RuntimeParameterMappings.empty(),
                            option.nativeOptions(budget)));
                    assertAccepted(unknown, request(null, prepared, null));
                    assertThat(unknown.bodies())
                            .allSatisfy(body -> assertThat(new BigDecimal(
                                            option.wireValue(body).toString()))
                                    .isEqualByComparingTo(new BigDecimal(budget.toString())));
                }
            }
        }
    }

    private void assertReasoningBudgetRejected(Number budget) {
        for (var option : reasoningBudgetOptions()) {
            var nativeOptions = option.nativeOptions(budget);
            NativeModelOptionsValidator.validate(nativeOptions);
            for (var prepared : List.of(false, true)) {
                assertRejected(
                        probe(context(
                                option.adapter(),
                                reasoningBudgetConstraints(),
                                RuntimeParameterMappings.empty(),
                                nativeOptions)),
                        request(null, prepared, null),
                        "reasoning budget must not exceed 8192");
            }
        }
    }

    @Test
    void outputLimitComparisonDoesNotOverflowAndNoLimitKeepsNativeBehavior() {
        var huge = Map.<String, Object>of("max_tokens", new BigInteger("18446744073709551616"));
        var validator = new LanguageModelParameterValidator(
                context(AdapterType.OPENAI_CHAT, outputLimit(), RuntimeParameterMappings.empty(), Map.of()));
        assertThatThrownBy(() -> validator.validateRequestBody(huge))
                .hasMessage("max_tokens must not exceed model limit 100");
        validator.validateRequestBody(Map.of("max_tokens", 100));
        var probe = probe(
                context(AdapterType.OPENAI_CHAT, null, RuntimeParameterMappings.empty(), Map.of("max_tokens", 5000)));
        assertAccepted(probe, request(null, false, null));
        assertThat(probe.bodies()).allSatisfy(body -> assertThat(body).containsEntry("max_tokens", 5000));
    }

    @Test
    void internalValidationPreservesToolContextAndIsAbsentWithoutConstraints() {
        var options = ChatCompletionsOptions.builder()
                .toolContext(Map.of("existing", "value"))
                .build();
        var validator = new LanguageModelParameterValidator(
                context(AdapterType.OPENAI_CHAT, outputLimit(), RuntimeParameterMappings.empty(), Map.of()));
        var decorated = (ChatCompletionsOptions) validator.withRequestValidation(options);
        assertThat(decorated.getToolContext()).containsEntry("existing", "value");
        assertThat(options.getToolContext()).containsOnlyKeys("existing");
        assertThatThrownBy(() -> ProviderRequestValidation.validate(decorated, Map.of("max_tokens", 101)))
                .hasMessage("max_tokens must not exceed model limit 100");
        var unconstrained = new LanguageModelParameterValidator(
                context(AdapterType.OPENAI_CHAT, null, RuntimeParameterMappings.empty(), Map.of()));
        assertThat(unconstrained.withRequestValidation(options)).isSameAs(options);
    }

    private void assertRejected(WireProbe probe, GenerateTextRequest request, String message) {
        StepVerifier.create(probe.model().generateText(request))
                .expectErrorMessage(message)
                .verify();
        var errors = probe.model()
                .streamText(request)
                .fullStream()
                .filter(part -> PartType.ERROR.equals(part.getType()))
                .collectList()
                .block();
        assertThat(errors)
                .singleElement()
                .satisfies(error -> assertThat(error.getErrorText()).isEqualTo(message));
        assertThat(probe.bodies()).isEmpty();
    }

    private void assertAccepted(WireProbe probe, GenerateTextRequest request) {
        probe.model()
                .generateText(request)
                .onErrorResume(error -> {
                    assertThat(error).isInstanceOf(ProviderHttpException.class);
                    return Mono.empty();
                })
                .block();
        probe.model().streamText(request).fullStream().collectList().block();
        // A captured request proves pre-send validation passed; the fake exchange then returns 400.
        assertThat(probe.bodies())
                .hasSize(2)
                .allSatisfy(
                        body -> assertThat(body.toString()).doesNotContain("ProviderRequestValidation", "existing"));
    }

    private GenerateTextRequest request(Integer tokens, boolean prepared, ReasoningOptions reasoning) {
        return GenerateTextRequest.builder()
                .prompt("Hello")
                .maxRetries(0)
                .maxOutputTokens(prepared ? null : tokens)
                .reasoning(reasoning)
                .prepareStep(
                        prepared
                                ? step -> PreparedStep.builder()
                                        .maxOutputTokens(tokens)
                                        .build()
                                : null)
                .build();
    }

    private WireProbe probe(ModelRuntimeContext context) {
        var bodies = new ArrayList<Map<String, Object>>();
        var webClient = WebClient.builder().exchangeFunction(request -> {
            var output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString))
                    .flatMap(body -> Mono.fromCallable(() -> {
                        bodies.add(JSON.readValue(body, new TypeReference<>() {}));
                        return ClientResponse.create(HttpStatus.BAD_REQUEST)
                                .header("Content-Type", "application/json")
                                .body("{\"error\":{\"message\":\"wire probe captured\"}}")
                                .build();
                    }));
        });
        var options = ChatCompletionsOptions.builder()
                .baseUrl("https://wire.invalid")
                .model("test-model")
                .maxRetries(0)
                .build();
        ChatModel chat =
                switch (context.adapterType()) {
                    case OPENAI_CHAT -> new OpenAiChatModel(options, webClient);
                    case OPENAI_RESPONSES -> new OpenAiResponsesModel(options, webClient);
                    case KIMI_CHAT -> new KimiChatModel(options, webClient);
                    case OPENROUTER_CHAT -> new OpenRouterChatModel(options, webClient);
                    case DASHSCOPE_CHAT -> new DashScopeChatModel(options, webClient);
                    case DASHSCOPE_MESSAGES -> new DashScopeMessagesModel(options, webClient);
                    case DEEPSEEK_MESSAGES -> new DeepSeekMessagesModel(options, webClient);
                    case OLLAMA_CHAT ->
                        new OllamaChatModel(
                                "https://wire.invalid",
                                null,
                                OllamaChatOptions.builder().model("test-model").build(),
                                webClient);
                    default -> throw new IllegalArgumentException("Unexpected test adapter");
                };
        var config = LanguageModelRuntimeConfiguration.from(context, ModelCapabilities.empty());
        var composition = LanguageModelRuntimeComposition.create(config, new LanguageModelRuntimeSupport(), null, null);
        return new WireProbe(new LanguageModelImpl(chat, composition, context), bodies);
    }

    private ModelRuntimeContext context(
            AdapterType adapter,
            ModelParameterConstraints constraints,
            RuntimeParameterMappings mappings,
            Map<String, Object> nativeOptions) {
        AiProviderType provider =
                switch (adapter) {
                    case KIMI_CHAT -> new KimiProvider();
                    case OPENROUTER_CHAT -> new OpenRouterProvider();
                    case DASHSCOPE_CHAT, DASHSCOPE_MESSAGES -> new DashScopeProvider();
                    case DEEPSEEK_MESSAGES -> new DeepSeekProvider();
                    case OLLAMA_CHAT -> new OllamaProvider();
                    default -> new OpenAiProvider();
                };
        return new ModelRuntimeContext(
                "model",
                "test-model",
                "provider",
                provider.getProviderType(),
                adapter,
                provider,
                mappings,
                nativeOptions,
                constraints);
    }

    private List<AdapterType> samplingAdapters() {
        return List.of(
                AdapterType.OPENAI_CHAT,
                AdapterType.OPENAI_RESPONSES,
                AdapterType.DASHSCOPE_MESSAGES,
                AdapterType.OLLAMA_CHAT);
    }

    private ModelParameterConstraints samplingConstraints(boolean supported) {
        var constraints = new ModelParameterConstraints();
        constraints.setTemperature(supported);
        constraints.setTopP(supported);
        constraints.setTopK(supported);
        return constraints;
    }

    private ModelParameterConstraints structuredSupport() {
        var constraints = new ModelParameterConstraints();
        constraints.setStructuredOutput(true);
        return constraints;
    }

    private Map<String, Object> samplingValues() {
        return Map.of("temperature", 0.5, "top_p", 0.8, "top_k", 20);
    }

    private Map<String, Object> samplingOptions(AdapterType adapter, Map<String, Object> values) {
        return adapter == AdapterType.OLLAMA_CHAT ? Map.of("options", values) : values;
    }

    private Map<?, ?> samplingBody(AdapterType adapter, Map<String, Object> body) {
        return adapter == AdapterType.OLLAMA_CHAT && body.get("options") instanceof Map<?, ?> options
                ? options
                : adapter == AdapterType.OLLAMA_CHAT ? Map.of() : body;
    }

    private RuntimeParameterMappings unsupportedSampling() {
        var values = new EnumMap<ModelParameter, EffectiveParameterMappings.EffectiveMapping>(ModelParameter.class);
        for (var parameter : List.of(ModelParameter.TEMPERATURE, ModelParameter.TOP_P, ModelParameter.TOP_K)) {
            values.put(
                    parameter,
                    new EffectiveParameterMappings.EffectiveMapping(
                            ModelParameterMappings.Mode.UNSUPPORTED,
                            null,
                            null,
                            EffectiveParameterMappings.Source.CATALOG));
        }
        return new RuntimeParameterMappings(new EffectiveParameterMappings(values), null, "model", "provider");
    }

    private GenerateTextRequest samplingRequest(boolean prepared) {
        return GenerateTextRequest.builder()
                .prompt("Hello")
                .maxRetries(0)
                .temperature(prepared ? null : 0.7)
                .topP(prepared ? null : 0.9)
                .topK(prepared ? null : 40)
                .prepareStep(
                        prepared
                                ? step -> PreparedStep.builder()
                                        .temperature(0.7)
                                        .topP(0.9)
                                        .topK(40)
                                        .build()
                                : null)
                .build();
    }

    private ModelParameterMappings.Selection selection(String template) {
        var selection = new ModelParameterMappings.Selection();
        selection.setMode(ModelParameterMappings.Mode.TEMPLATE);
        selection.setTemplate(template);
        return selection;
    }

    private Map<String, Object> structuredOptions(AdapterType adapter) {
        var schema = Map.of("type", "object", "properties", Map.of("answer", Map.of("type", "string")));
        return switch (adapter) {
            case OPENAI_CHAT ->
                Map.of(
                        "response_format",
                        Map.of("type", "json_schema", "json_schema", Map.of("name", "answer", "schema", schema)));
            case OPENAI_RESPONSES ->
                Map.of("text", Map.of("format", Map.of("type", "json_schema", "name", "answer", "schema", schema)));
            case DASHSCOPE_MESSAGES ->
                Map.of("output_config", Map.of("format", Map.of("type", "json_schema", "schema", schema)));
            default -> throw new IllegalArgumentException("Unexpected structured-output adapter");
        };
    }

    private RuntimeParameterMappings mapping(
            ModelParameter parameter, String template, ModelParameterMappings.ReasoningMapping reasoning) {
        return new RuntimeParameterMappings(
                new EffectiveParameterMappings(Map.of(
                        parameter,
                        new EffectiveParameterMappings.EffectiveMapping(
                                ModelParameterMappings.Mode.TEMPLATE,
                                template,
                                reasoning,
                                EffectiveParameterMappings.Source.CATALOG))),
                null,
                "model",
                "provider");
    }

    private ModelParameterMappings.ReasoningValueMapping value(
            String field, ModelParameterMappings.ValueType type, String value) {
        var mapping = new ModelParameterMappings.ReasoningValueMapping();
        mapping.setField(field);
        mapping.setValueType(type);
        mapping.setValue(value);
        return mapping;
    }

    private ModelParameterConstraints outputLimit() {
        var constraints = new ModelParameterConstraints();
        constraints.setOutputTokens(100L);
        return constraints;
    }

    private ModelParameterConstraints reasoningConstraints() {
        var constraints = new ModelParameterConstraints();
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setEfforts(List.of("high"));
        reasoning.setBudgetMinimum(512L);
        reasoning.setBudgetMaximum(4096L);
        constraints.setReasoning(reasoning);
        return constraints;
    }

    private List<OutputOptionCase> outputOptions() {
        return List.of(
                new OutputOptionCase("max_tokens", AdapterType.OPENAI_CHAT, "openai.max-tokens"),
                new OutputOptionCase("max_completion_tokens", AdapterType.OPENAI_CHAT, "openai.max-completion-tokens"),
                new OutputOptionCase(
                        "max_output_tokens", AdapterType.OPENAI_RESPONSES, "openai.max-completion-tokens"));
    }

    private ModelParameterConstraints reasoningBudgetConstraints() {
        var constraints = new ModelParameterConstraints();
        var reasoning = new ModelParameterConstraints.Reasoning();
        reasoning.setBudgetMinimum(1024L);
        reasoning.setBudgetMaximum(8192L);
        constraints.setReasoning(reasoning);
        return constraints;
    }

    private List<ReasoningBudgetOptionCase> reasoningBudgetOptions() {
        return List.of(
                new ReasoningBudgetOptionCase(AdapterType.OPENAI_CHAT, null, "thinking_budget"),
                new ReasoningBudgetOptionCase(AdapterType.OPENAI_RESPONSES, "reasoning", "max_tokens"),
                new ReasoningBudgetOptionCase(AdapterType.DASHSCOPE_MESSAGES, "thinking", "budget_tokens"));
    }

    private record ReasoningBudgetOptionCase(AdapterType adapter, String parent, String field) {
        Map<String, Object> nativeOptions(Number budget) {
            return parent == null
                    ? Map.of(field, budget)
                    : Map.of(
                            parent,
                            parent.equals("thinking")
                                    ? Map.of("type", "enabled", field, budget)
                                    : Map.of(field, budget));
        }

        Object wireValue(Map<String, Object> body) {
            return parent == null ? body.get(field) : ((Map<?, ?>) body.get(parent)).get(field);
        }
    }

    private record OutputOptionCase(String field, AdapterType adapter, String template) {}

    private record WireProbe(LanguageModelImpl model, List<Map<String, Object>> bodies) {}
}
