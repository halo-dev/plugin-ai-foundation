package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.NON_CHAT_CATALOG;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.extension.ModelParameterMappings.Mode;
import run.halo.aifoundation.extension.ModelParameterProfile;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.dashscope.DashScopeProvider;
import run.halo.aifoundation.provider.doubao.DouBaoProvider;
import run.halo.aifoundation.provider.kimi.KimiProvider;
import run.halo.aifoundation.provider.mapping.EffectiveParameterMappings;
import run.halo.aifoundation.provider.mapping.ModelParameter;
import run.halo.aifoundation.provider.mapping.ParameterMappingTarget;
import run.halo.aifoundation.provider.mapping.ParameterMappingTemplateRegistry;
import run.halo.aifoundation.provider.mapping.ParameterMappingValidator;
import run.halo.aifoundation.provider.mapping.RuntimeParameterMappings;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.siliconflow.SiliconFlowProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

class ModelCatalogParameterMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOTH_PROTOCOLS = """
        {"responses":{"base_url":"https://api.openai.com/v1"},
          "chat-completions":{"base_url":"https://api.openai.com/v1"}}
        """;

    @Test
    void modelsOnTheSameProviderCanUseBudgetEffortOrFixedReasoning() throws Exception {
        var type = new DashScopeProvider();
        var budget = profile(type, AdapterType.DASHSCOPE_CHAT, entry("""
            {"reasoning":{"supported":true,"toggle":true,"budget":{"min":2048,"max":100000}}}
            """, "{}", BOTH_PROTOCOLS));
        var budgetMapping = budget.getMappings().getLanguage().getReasoning().getReasoningMapping();
        assertThat(budgetMapping.getEnabled().getField()).isEqualTo("enable_thinking");
        assertThat(budgetMapping.getDisabled().getValue()).isEqualTo("false");
        assertThat(budgetMapping.getLow().getField()).isEqualTo("thinking_budget");
        assertThat(budgetMapping.getLow().getValue()).isEqualTo("2048");
        assertThat(budgetMapping.getMedium().getValue()).isEqualTo("4096");
        assertThat(budgetMapping.getHigh().getValue()).isEqualTo("8192");
        assertThat(budgetMapping.getLow().getAdditionalValues()).singleElement().satisfies(value -> {
            assertThat(value.getField()).isEqualTo("enable_thinking");
            assertThat(value.getValue()).isEqualTo("true");
        });

        var effort = profile(type, AdapterType.DASHSCOPE_CHAT, entry("""
            {"reasoning":{"supported":true,"effort":["minimal","low","medium","high","xhigh"]}}
            """, "{}", BOTH_PROTOCOLS));
        var effortMapping = effort.getMappings().getLanguage().getReasoning().getReasoningMapping();
        assertThat(effortMapping.getLow().getField()).isEqualTo("reasoning_effort");
        assertThat(effortMapping.getLow().getValue()).isEqualTo("low");
        assertThat(effortMapping.getMedium().getValue()).isEqualTo("medium");
        assertThat(effortMapping.getHigh().getValue()).isEqualTo("xhigh");
        assertThat(effortMapping.getDisabled()).isNull();

        var fixed = profile(
                type, AdapterType.DASHSCOPE_CHAT, entry("{\"reasoning\":{\"supported\":true}}", "{}", BOTH_PROTOCOLS));
        assertThat(fixed.getConstraints().getReasoning().getSupported()).isTrue();
        assertThat(fixed.getMappings().getLanguage().getReasoning().getMode()).isEqualTo(Mode.UNSUPPORTED);
    }

    @Test
    void respectsBudgetBoundsWithoutTurningMaximumIntoTheDefault() throws Exception {
        var profile = profile(new DashScopeProvider(), AdapterType.DASHSCOPE_CHAT, entry("""
            {"reasoning":{"supported":true,"toggle":true,"budget":{"min":512,"max":6000}}}
            """, "{}", BOTH_PROTOCOLS));
        var reasoning = profile.getMappings().getLanguage().getReasoning().getReasoningMapping();
        assertThat(reasoning.getLow().getValue()).isEqualTo("1024");
        assertThat(reasoning.getMedium().getValue()).isEqualTo("4096");
        assertThat(reasoning.getHigh().getValue()).isEqualTo("6000");
        assertThat(reasoning.getEnabled().getValue()).isEqualTo("true");
        assertThat(profile.getConstraints().getReasoning().getBudgetMaximum()).isEqualTo(6000);
    }

    @Test
    void bundledSiliconFlowEffortsWorkWithoutProviderDefaultEffortIntents() {
        var bundle = ModelsDevBundledCatalog.load();
        assertThat(bundle).isNotNull();
        var entry = bundle.catalog().find("siliconflow-cn", "deepseek-ai/DeepSeek-V4-Flash");
        assertThat(entry).isNotNull();
        var result = profile(new SiliconFlowProvider(), AdapterType.SILICONFLOW_CHAT, entry);
        assertThat(result.getConstraints().getReasoning().getEfforts()).containsExactly("high", "max");
        assertThat(result.getFeatureEvidence()).isEmpty();
        var selection = result.getMappings().getLanguage().getReasoning();
        assertThat(selection.getMode()).isEqualTo(Mode.TEMPLATE);
        assertThat(selection.getTemplate()).isEqualTo("reasoning.enable-thinking");
        assertThat(selection.getReasoningMapping().getDisabled()).isNull();
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.LOW)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "high"));
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "max"));
    }

    @Test
    void bundledKimiToggleModelsUseThinkingInsteadOfProviderEffortDefaults() {
        var bundle = ModelsDevBundledCatalog.load();
        assertThat(bundle).isNotNull();
        for (var catalogId : List.of("moonshotai-cn", "moonshotai")) {
            var entry = bundle.catalog().find(catalogId, "kimi-k2.6");
            assertThat(entry).isNotNull();
            var result = profile(new KimiProvider(), AdapterType.KIMI_CHAT, entry);
            assertThat(result.getConstraints().getReasoning().getToggle()).isTrue();
            assertThat(result.getConstraints().getReasoning().getEfforts()).isNull();
            var selection = result.getMappings().getLanguage().getReasoning();
            assertThat(selection.getTemplate()).isEqualTo("reasoning.thinking-type");
            assertThat(selection.getReasoningMapping().getLow()).isNull();
            assertThat(selection.getReasoningMapping().getMedium()).isNull();
            assertThat(selection.getReasoningMapping().getHigh()).isNull();
            assertThat(applyReasoning(result, ReasoningOptions.enabled()))
                    .containsExactlyEntriesOf(Map.of("thinking", Map.of("type", "enabled")));
            assertThat(applyReasoning(result, ReasoningOptions.disabled()))
                    .containsExactlyEntriesOf(Map.of("thinking", Map.of("type", "disabled")));
        }
    }

    @Test
    void bundledKimiEffortModelsKeepThreePortableLevelsWithoutInventingAToggle() {
        var bundle = ModelsDevBundledCatalog.load();
        assertThat(bundle).isNotNull();
        var entry = bundle.catalog().find("moonshotai-cn", "kimi-k3.1-pro");
        assertThat(entry).isNotNull();
        var result = profile(new KimiProvider(), AdapterType.KIMI_CHAT, entry);
        var selection = result.getMappings().getLanguage().getReasoning();
        assertThat(selection.getTemplate()).isEqualTo("reasoning.kimi");
        assertThat(selection.getReasoningMapping().getDisabled()).isNull();
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.LOW)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "low"));
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.MEDIUM)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "high"));
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "max"));
    }

    @Test
    void kimiToggleMappingRequiresExplicitCatalogControlEvidence() throws Exception {
        for (var controls : List.of("", ",\"toggle\":false", ",\"toggle\":true,\"effort\":[\"future-strength\"]")) {
            var result = profile(
                    new KimiProvider(),
                    AdapterType.KIMI_CHAT,
                    entry("{\"reasoning\":{\"supported\":true" + controls + "}}", "{}", BOTH_PROTOCOLS));
            assertThat(result.getMappings().getLanguage().getReasoning().getMode())
                    .isEqualTo(Mode.UNSUPPORTED);
        }
    }

    @Test
    void doubaoEffortOnlyModelsDoNotInheritTheProviderThinkingToggle() throws Exception {
        var result = profile(new DouBaoProvider(), AdapterType.DOUBAO_CHAT, entry("""
            {"reasoning":{"supported":true,"effort":["none","minimal","low","medium","high","xhigh","max"]}}
            """, "{}", BOTH_PROTOCOLS));
        assertThat(result.getMappings().getLanguage().getReasoning().getTemplate())
                .isEqualTo("reasoning.thinking-type");
        assertThat(applyReasoning(result, ReasoningOptions.enabled()))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "medium"));
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "max"));
        assertThat(applyReasoning(result, ReasoningOptions.disabled()))
                .containsExactlyEntriesOf(Map.of("reasoning_effort", "none"));
    }

    @Test
    void doubaoEffortsKeepExplicitToggleRequirements() throws Exception {
        var result = profile(new DouBaoProvider(), AdapterType.DOUBAO_CHAT, entry("""
            {"reasoning":{"supported":true,"toggle":true,"effort":["minimal","low","medium","high"]}}
            """, "{}", BOTH_PROTOCOLS));
        assertThat(applyReasoning(result, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("reasoning_effort", "high", "thinking", Map.of("type", "enabled")));
        assertThat(applyReasoning(result, ReasoningOptions.disabled()))
                .containsExactlyEntriesOf(Map.of("thinking", Map.of("type", "disabled")));
    }

    @Test
    void effortFallbackDoesNotLeakChatFieldsIntoOtherProtocols() throws Exception {
        var capabilities = """
            {"reasoning":{"supported":true,"effort":["low","medium","high"]}}
            """;
        var responses =
                profile(new DouBaoProvider(), AdapterType.DOUBAO_RESPONSES, entry(capabilities, "{}", BOTH_PROTOCOLS));
        assertThat(applyReasoning(responses, ReasoningOptions.effort(ReasoningOptions.Effort.HIGH)))
                .containsExactlyEntriesOf(Map.of("reasoning", Map.of("effort", "high")));
        var messages = profile(
                new SiliconFlowProvider(),
                AdapterType.SILICONFLOW_MESSAGES,
                entry(capabilities, "{}", "{\"messages\":{\"base_url\":\"https://api.siliconflow.cn/v1\"}}"));
        assertThat(messages.getMappings().getLanguage().getReasoning().getMode())
                .isEqualTo(Mode.UNSUPPORTED);
    }

    @Test
    void effortFallbackKeepsEmptyAndUnknownCatalogEffortsUnsupported() throws Exception {
        for (var efforts : List.of("[]", "[\"future-strength\"]")) {
            var result = profile(
                    new SiliconFlowProvider(),
                    AdapterType.SILICONFLOW_CHAT,
                    entry("{\"reasoning\":{\"supported\":true,\"effort\":" + efforts + "}}", "{}", BOTH_PROTOCOLS));
            assertThat(result.getMappings().getLanguage().getReasoning().getMode())
                    .isEqualTo(Mode.UNSUPPORTED);
        }
    }

    @Test
    void samplingFalseDisablesMappingWhileMissingSupportKeepsInheritance() throws Exception {
        var result = profile(
                new OpenAiProvider(),
                AdapterType.OPENAI_RESPONSES,
                entry("{\"temperature\":false,\"top_k\":true}", "{}", BOTH_PROTOCOLS));
        assertThat(result.getConstraints().getTemperature()).isFalse();
        assertThat(result.getMappings().getLanguage().getTemperature().getMode())
                .isEqualTo(Mode.UNSUPPORTED);
        assertThat(result.getConstraints().getTopP()).isNull();
        assertThat(result.getMappings().getLanguage().getTopP()).isNull();
        assertThat(result.getConstraints().getTopK()).isTrue();
        assertThat(result.getMappings().getLanguage().getTopK()).isNull();
        assertThat(result.getConstraints().getToolCalling()).isNull();
    }

    @Test
    void retainsTokenLimitsAsConstraintsAndUsesOnlySelectedProtocolToolOptions() throws Exception {
        var entry = entry("""
            {"tools":{"supported":true},"structured_output":true,
              "service_tier":["priority"],"store":true,"stream_usage":true,"prompt_cache_key":true}
            """, "{\"context\":128000,\"input\":120000,\"output\":8192}", """
            {"responses":{"base_url":"https://api.openai.com/v1","capabilities":{
                "tools":{"strict":true,"choice":["auto","tool"]},"service_tier":[],"store":false}},
              "chat-completions":{"base_url":"https://api.openai.com/v1",
                "compatibility":{"max_tokens_field":"max_completion_tokens"},
                "capabilities":{"tools":{"strict":false,"choice":[]}}}}
            """);
        var responses = profile(new OpenAiProvider(), AdapterType.OPENAI_RESPONSES, entry);
        var constraints = responses.getConstraints();
        assertThat(constraints.getContextTokens()).isEqualTo(128000);
        assertThat(constraints.getInputTokens()).isEqualTo(120000);
        assertThat(constraints.getOutputTokens()).isEqualTo(8192);
        assertThat(responses.getMappings().getLanguage().getMaxOutputTokens()).isNull();
        assertThat(constraints.getToolStrict()).isTrue();
        assertThat(constraints.getToolChoices()).containsExactly("auto", "tool");
        assertThat(constraints.getServiceTiers()).isEmpty();
        assertThat(constraints.getStore()).isFalse();
        assertThat(constraints.getStreamUsage()).isTrue();
        assertThat(constraints.getPromptCacheKey()).isTrue();
        var chat = profile(new OpenAiProvider(), AdapterType.OPENAI_CHAT, entry);
        assertThat(chat.getConstraints().getToolStrict()).isFalse();
        assertThat(chat.getConstraints().getToolChoices()).isEmpty();
        assertThat(chat.getMappings().getLanguage().getMaxOutputTokens().getField())
                .isEqualTo("max_completion_tokens");
    }

    @Test
    void positiveRemoteFeaturesAndExplicitNegativesHavePriorityOverCatalog() throws Exception {
        var entry = entry("""
            {"tools":{"supported":false},"structured_output":false,"reasoning":{"supported":false}}
            """, "{}", BOTH_PROTOCOLS);
        var model = model(AdapterType.OPENAI_RESPONSES).toBuilder()
                .source(DiscoverySource.REMOTE)
                .features(Set.of(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING))
                .featureEvidence(Map.of(ModelFeature.REASONING, false))
                .build();
        var result = ModelCatalogParameterMapper.map(
                provider(null), new OpenAiProvider(), model, entry, AdapterType.OPENAI_RESPONSES);
        assertThat(result.getConstraints().getToolCalling()).isTrue();
        assertThat(result.getConstraints().getStructuredOutput()).isTrue();
        assertThat(result.getConstraints().getReasoning().getSupported()).isFalse();
        assertThat(result.getFeatureEvidence())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        ModelFeature.TOOL_CALL,
                        true,
                        ModelFeature.STRUCTURED_OUTPUT,
                        true,
                        ModelFeature.REASONING,
                        false));
    }

    @Test
    void catalogCapabilitiesAndRuleFeaturesNeverBecomeProviderEvidence() throws Exception {
        var entry = entry("""
            {"tools":{"supported":false},"structured_output":true,"reasoning":{"supported":true}}
            """, "{}", BOTH_PROTOCOLS);
        var model = model(AdapterType.OPENAI_RESPONSES).toBuilder()
                .features(Set.of(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.REASONING))
                .build();
        var result = ModelCatalogParameterMapper.map(
                provider(null), new OpenAiProvider(), model, entry, AdapterType.OPENAI_RESPONSES);
        assertThat(result.getConstraints().getToolCalling()).isFalse();
        assertThat(result.getConstraints().getStructuredOutput()).isTrue();
        assertThat(result.getConstraints().getReasoning().getSupported()).isTrue();
        assertThat(result.getFeatureEvidence()).isEmpty();
    }

    @Test
    void explicitFeatureEvidenceIsPreservedWithoutPromotingOtherFeatures() throws Exception {
        var entry = entry("{}", "{}", BOTH_PROTOCOLS);
        var model = model(AdapterType.OPENAI_RESPONSES).toBuilder()
                .featureEvidence(Map.of(
                        ModelFeature.TOOL_CALL, false, ModelFeature.STRUCTURED_OUTPUT, true, ModelFeature.VISION, true))
                .build();
        var result = ModelCatalogParameterMapper.map(
                provider(null), new OpenAiProvider(), model, entry, AdapterType.OPENAI_RESPONSES);
        assertThat(result.getFeatureEvidence())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(ModelFeature.TOOL_CALL, false, ModelFeature.STRUCTURED_OUTPUT, true));
    }

    @Test
    void providerEvidenceSurvivesSnapshotSerialization() throws Exception {
        var mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_RESPONSES, entry("{}", "{}", BOTH_PROTOCOLS));
        assertThat(mapper.readValue(mapper.writeValueAsString(result), ModelParameterProfile.class)
                        .getFeatureEvidence())
                .isEmpty();
        result.setFeatureEvidence(Map.of(ModelFeature.TOOL_CALL, false));
        assertThat(mapper.readValue(mapper.writeValueAsString(result), ModelParameterProfile.class)
                        .getFeatureEvidence())
                .containsEntry(ModelFeature.TOOL_CALL, false);
    }

    @Test
    void unknownEffortsArePreservedButDoNotInventRequestControlMappings() throws Exception {
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_RESPONSES, entry("""
            {"reasoning":{"supported":true,"effort":["future-strength"]}}
            """, "{}", BOTH_PROTOCOLS));
        assertThat(result.getConstraints().getReasoning().getEfforts()).containsExactly("future-strength");
        assertThat(result.getMappings().getLanguage().getReasoning().getMode()).isEqualTo(Mode.UNSUPPORTED);
    }

    @Test
    void absentToggleDoesNotInferOffUnlessEffortExplicitlyIncludesNone() throws Exception {
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_RESPONSES, entry("""
            {"reasoning":{"supported":true,"effort":["none","low","medium","high","max"]}}
            """, "{}", BOTH_PROTOCOLS));
        var mapping = result.getMappings().getLanguage().getReasoning().getReasoningMapping();
        assertThat(mapping.getDisabled().getValue()).isEqualTo("none");
        assertThat(mapping.getHigh().getValue()).isEqualTo("max");
        assertThat(mapping.getHigh().getField()).isEqualTo("reasoning.effort");
    }

    @Test
    void missingProtocolProducesNoProfile() throws Exception {
        var type = new OpenAiProvider();
        var entry = entry("{}", "{}", "{\"chat-completions\":{\"base_url\":\"https://api.openai.com/v1\"}}");
        assertThat(profile(type, AdapterType.OPENAI_RESPONSES, entry)).isNull();
    }

    @Test
    void budgetOnlyModelsDoNotInheritUnrelatedEffortToggleOnAnotherProtocol() throws Exception {
        var result =
                profile(new DashScopeProvider(), AdapterType.DASHSCOPE_RESPONSES, entry("""
            {"reasoning":{"supported":true,"toggle":true,"budget":{"max":81920}}}
            """, "{}", BOTH_PROTOCOLS));
        assertThat(result.getMappings().getLanguage().getReasoning().getMode()).isEqualTo(Mode.UNSUPPORTED);
    }

    @Test
    void missingV2MaxTokensFieldUsesItsDocumentedChatCompletionsDefault() throws Exception {
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_CHAT, entry("{}", "{}", BOTH_PROTOCOLS));
        assertThat(result.getMappings().getLanguage().getMaxOutputTokens().getField())
                .isEqualTo("max_completion_tokens");
        var legacyField = profile(new OpenAiProvider(), AdapterType.OPENAI_CHAT, entry("{}", "{}", """
                {"chat-completions":{"base_url":"https://api.openai.com/v1",
                  "compatibility":{"max_tokens_field":"max_tokens"}}}
                """));
        assertThat(legacyField.getMappings().getLanguage().getMaxOutputTokens().getField())
                .isEqualTo("max_tokens");
        var kimi = profile(new KimiProvider(), AdapterType.KIMI_CHAT, entry("{}", "{}", """
                {"chat-completions":{"base_url":"https://api.moonshot.cn/v1",
                  "compatibility":{"max_tokens_field":"max_tokens"}}}
                """));
        assertThat(kimi.getMappings().getLanguage().getMaxOutputTokens().getTemplate())
                .isEqualTo("openai.max-completion-tokens");
        assertThat(kimi.getMappings().getLanguage().getMaxOutputTokens().getField())
                .isEqualTo("max_tokens");
    }

    @Test
    void catalogFactsCannotEnableFeaturesOutsideTheAdapterImplementation() throws Exception {
        var type = new OpenAiProvider() {
            @Override
            public List<ModelFeature> getSupportedFeatures(AdapterType adapter) {
                return List.of(ModelFeature.STREAMING);
            }
        };
        var result = profile(type, AdapterType.OPENAI_CHAT, entry("""
            {"tools":{"supported":true},"structured_output":true,
              "reasoning":{"supported":true,"effort":["low","high"]}}
            """, "{}", BOTH_PROTOCOLS));
        assertThat(result.getConstraints().getToolCalling()).isFalse();
        assertThat(result.getConstraints().getStructuredOutput()).isFalse();
        assertThat(result.getMappings().getLanguage().getReasoning().getMode()).isEqualTo(Mode.UNSUPPORTED);
    }

    @Test
    void nonChatProfilesKeepInputLimitsWithoutCreatingRequestParameters() throws Exception {
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree(NON_CHAT_CATALOG));
        var embedding =
                profile(new OpenAiProvider(), AdapterType.OPENAI_EMBEDDING, catalog.find("openai", "embedding-model"));
        assertThat(embedding.getConstraints().getContextTokens()).isEqualTo(8191);
        assertThat(embedding.getConstraints().getInputTokens()).isEqualTo(8000);
        assertThat(embedding.getConstraints().getOutputTokens()).isNull();
        assertThat(embedding.getMappings().getEmbedding()).isNull();
        assertThat(embedding.getMappings().getLanguage()).isNull();
        assertThat(embedding.getFeatureEvidence()).isEmpty();

        var image = profile(new OpenAiProvider(), AdapterType.OPENAI_IMAGE, catalog.find("openai", "image-model"));
        assertThat(image.getConstraints().getContextTokens()).isEqualTo(8192);
        assertThat(image.getConstraints().getOutputTokens()).isNull();
        assertThat(image.getMappings().getImageGeneration()).isNull();
        assertThat(image.getConstraints().getReasoning()).isNull();

        var rerank = profile(new OpenRouterProvider(), AdapterType.RERANK, catalog.find("openrouter", "rerank-model"));
        assertThat(rerank.getConstraints().getContextTokens()).isEqualTo(4096);
        assertThat(rerank.getConstraints().getInputTokens()).isEqualTo(4000);
        assertThat(rerank.getConstraints().getOutputTokens()).isNull();
        assertThat(rerank.getMappings().getRerank()).isNull();
        assertThat(rerank.getConstraints().getToolCalling()).isNull();
    }

    @Test
    void nonChatUnknownLimitsRemainUnknownAndChatCapabilitiesCannotLeak() throws Exception {
        var catalog = ModelsDevCatalog.parse(MAPPER.readTree("""
            {"openai":{"id":"openai","models":{"embedding-model":{
              "id":"embedding-model","type":"embedding",
              "limit":{"context":0,"input":-1,"output":1536},
              "capabilities":{"temperature":false,"tools":{"supported":true},
                "reasoning":{"supported":true,"effort":["high"]}},
              "api":{"embeddings":{"base_url":"https://api.openai.com/v1"}}}}}}
            """));
        var entry = catalog.find("openai", "embedding-model");
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_EMBEDDING, entry);
        assertThat(result.getConstraints().getContextTokens()).isNull();
        assertThat(result.getConstraints().getInputTokens()).isNull();
        assertThat(result.getConstraints().getOutputTokens()).isNull();
        assertThat(result.getConstraints().getTemperature()).isNull();
        assertThat(result.getConstraints().getToolCalling()).isNull();
        assertThat(result.getConstraints().getReasoning()).isNull();
        assertThat(profile(new OpenAiProvider(), AdapterType.OPENAI_CHAT, entry))
                .isNull();
    }

    @Test
    void realBundledEmbeddingOutputDoesNotBecomeAnEmbeddingDimensionDefault() {
        var bundle = ModelsDevBundledCatalog.load();
        var entry = bundle.catalog().find("openai", "text-embedding-3-small");
        assertThat(entry).isNotNull();
        var result = profile(new OpenAiProvider(), AdapterType.OPENAI_EMBEDDING, entry);
        assertThat(result.getConstraints().getContextTokens()).isPositive();
        assertThat(result.getConstraints().getOutputTokens()).isNull();
        assertThat(result.getMappings().getEmbedding()).isNull();
    }

    private ModelParameterProfile profile(AiProviderType type, AdapterType adapter, ModelsDevCatalog.Entry entry) {
        var provider = provider(null);
        provider.getSpec().setProviderType(type.getProviderType());
        var model = model(adapter).toBuilder()
                .modelId(entry.model().path("id").asText())
                .build();
        return ModelCatalogParameterMapper.map(provider, type, model, entry, adapter);
    }

    private Map<String, Object> applyReasoning(ModelParameterProfile profile, ReasoningOptions intent) {
        var registry = new ParameterMappingTemplateRegistry();
        new ParameterMappingValidator(registry)
                .validateModel(profile.getMappings(), ModelType.LANGUAGE, profile.getAdapterType());
        var selection = profile.getMappings().getLanguage().getReasoning();
        var mapping = new EffectiveParameterMappings.EffectiveMapping(
                selection.getMode(),
                selection.getTemplate(),
                selection.getField(),
                selection.getReasoningMapping(),
                EffectiveParameterMappings.Source.CATALOG);
        var runtime = new RuntimeParameterMappings(
                new EffectiveParameterMappings(Map.of(ModelParameter.REASONING, mapping)),
                registry,
                profile.getModelId(),
                "provider");
        runtime.validateReasoning(intent, profile.getConstraints().getReasoning());
        var target = new ParameterMappingTarget();
        assertThat(runtime.applyReasoning(intent, target)).isTrue();
        return target.root();
    }

    private DiscoveredModel model(AdapterType adapter) {
        return DiscoveredModel.builder()
                .modelId("model")
                .modelType(adapter.getModelType())
                .adapterType(adapter)
                .source(DiscoverySource.RULE)
                .build();
    }

    private ModelsDevCatalog.Entry entry(String capabilities, String limits, String api) throws Exception {
        var root = MAPPER.createObjectNode();
        var provider = root.putObject("provider");
        provider.put("id", "provider");
        var model = provider.putObject("models").putObject("model");
        model.put("id", "model");
        model.put("type", "chat");
        model.set("capabilities", MAPPER.readTree(capabilities));
        model.set("limit", MAPPER.readTree(limits));
        model.set("api", MAPPER.readTree(api));
        return ModelsDevCatalog.parse(root).find("provider", "model");
    }
}
