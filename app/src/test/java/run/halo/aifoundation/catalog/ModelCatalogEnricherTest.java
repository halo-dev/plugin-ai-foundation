package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

class ModelCatalogEnricherTest {

    @TempDir
    Path directory;

    @Test
    void supplementsIdentifierOnlyResultWithoutAddingUnavailableCatalogModels() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG);
        var input = discovered();
        var results = enricher.enrich(provider(null), new OpenAiProvider(), List.of(input));
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.features()).contains(ModelFeature.TOOL_CALL, ModelFeature.VISION)
            .doesNotContain(ModelFeature.REASONING, ModelFeature.STRUCTURED_OUTPUT, ModelFeature.AUDIO_INPUT);
        assertThat(result.capabilities().getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "image/jpeg", "application/pdf");
        assertThat(result.capabilities().getLanguage().getInputSources()).containsExactly(InputSource.DATA);
        assertThat(result.capabilities().getLanguage().getReasoningHistory()).isNull();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.UNKNOWN);
        assertThat(result.catalogMatch().providerId()).isEqualTo("openai");
        assertThat(result.catalogMatch().fetchedAt()).isEqualTo(ModelsDevCatalogServiceTest.NOW);
        assertThat(result.source()).isEqualTo(DiscoverySource.RULE);
        assertThat(result.confidence()).isEqualTo(DiscoveryConfidence.MEDIUM);
        assertThat(input.capabilities()).isNull();
    }

    @Test
    void neverMatchesCustomGatewaysAliasesPrivateDeploymentsOrDifferentEndpoints() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG);
        var model = discovered();
        for (var url : List.of("https://proxy.example/v1", "https://api.openai.com/v1/tenant",
            "https://api.openai.com.evil.example/v1", "http://api.openai.com/v1",
            "https://api.openai.com/v1?deployment=private")) {
            assertThat(enricher.enrich(provider(url), new OpenAiProvider(), List.of(model)))
                .containsExactly(model);
        }
        var similar = model.toBuilder().modelId("exact-model-latest").build();
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), List.of(similar)))
            .containsExactly(similar);
        assertThat(enricher.enrich(provider("https://api.openai.com/v1"), new OpenAiLikeProvider(),
            List.of(model))).containsExactly(model);
        var overridden = provider(null);
        overridden.getSpec().setChatEndpointPath("/tenant/chat/completions");
        assertThat(enricher.enrich(overridden, new OpenAiProvider(), List.of(model))).containsExactly(model);
    }

    @Test
    void preservesExplicitProviderFalseTrueAndEmptyCoverageWhileFillingMissingFields() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG);
        var model = discovered().toBuilder().source(DiscoverySource.REMOTE)
            .features(Set.of(ModelFeature.STREAMING, ModelFeature.STRUCTURED_OUTPUT))
            .featureEvidence(Map.of(ModelFeature.TOOL_CALL, false))
            .capabilities(ModelCapabilities.builder().language(LanguageCapability.builder()
                .imageInput(false).inputMediaTypes(List.of()).inputSources(List.of()).build()).build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.REMOTE).build())
            .build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model)).getFirst();
        assertThat(result.features()).contains(ModelFeature.STRUCTURED_OUTPUT)
            .doesNotContain(ModelFeature.TOOL_CALL, ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getInputMediaTypes()).isEmpty();
        assertThat(result.capabilities().getLanguage().getInputSources()).isEmpty();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields()).doesNotContain("features.tool-call", "language.imageInput");
    }

    @Test
    void manualSnapshotsAndNonLanguageModelsAreUntouched() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG);
        var manual = discovered().toBuilder().capabilities(ModelCapabilities.language(
            LanguageCapability.builder().imageInput(false).build())).build();
        var image = discovered().toBuilder().modelType(ModelType.IMAGE_GENERATION).build();
        var models = List.of(manual, image);
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), models)).containsExactlyElementsOf(models);
    }

    @Test
    void catalogOverridesAdapterDefaultsButCannotEnableUnsupportedFeaturesOrFiles() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG.replace("\"reasoning\":false", "\"reasoning\":true"));
        var type = new OpenAiProvider() {
            @Override
            public List<ModelFeature> getSupportedFeatures(AdapterType adapter) {
                return List.of(ModelFeature.STREAMING);
            }
        };
        var model = discovered().toBuilder().adapterType(AdapterType.OPENAI_CHAT)
            .features(Set.of(ModelFeature.STREAMING))
            .capabilities(ModelCapabilities.builder().language(LanguageCapability.builder().imageInput(true).build()).build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.BUILT_IN).build())
            .build();
        var result = enricher.enrich(provider(null), type, List.of(model)).getFirst();
        assertThat(result.features()).containsExactly(ModelFeature.STREAMING);
        assertThat(result.capabilities().getLanguage().getImageInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getFileInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getInputMediaTypes()).isEmpty();
        assertThat(result.capabilities().getImageGeneration()).isNull();
    }

    @Test
    void unavailableCatalogDoesNotAffectDiscovery() {
        var enricher = new ModelCatalogEnricher(mock(ModelsDevCatalogService.class));
        var models = List.of(discovered());
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), models)).isSameAs(models);
    }

    @Test
    void explicitFeatureNegativesCannotBeReenabledThroughFineGrainedCapabilities() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG);
        var model = discovered().toBuilder()
            .featureEvidence(Map.of(ModelFeature.VISION, false)).build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model)).getFirst();
        assertThat(result.features()).doesNotContain(ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getInputMediaTypes())
            .doesNotContain("image/png", "image/jpeg");
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields()).doesNotContain("language.imageInput");
    }

    @Test
    void positiveProviderImageEvidenceWinsOverCatalogTextOnlyInput() {
        var enricher = enricher(ModelsDevCatalogServiceTest.CATALOG
            .replace("[\"text\",\"image\",\"pdf\"]", "[\"text\"]"));
        var model = discovered().toBuilder().source(DiscoverySource.REMOTE)
            .features(Set.of(ModelFeature.VISION)).build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model)).getFirst();
        assertThat(result.features()).contains(ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isTrue();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
    }

    @Test
    void catalogWithoutReplayEvidencePreservesProviderDefaultAndExplicitFalse() {
        var enricher = enricher("""
            {"deepseek":{"id":"deepseek","models":{"deepseek-chat":{
              "id":"deepseek-chat","reasoning":true,
              "modalities":{"input":["text"],"output":["text"]}}}}}
            """);
        var provider = provider(null);
        provider.getSpec().setProviderType("deepseek");
        var type = new DeepSeekProvider();
        var input = discovered().toBuilder().modelId("deepseek-chat")
            .adapterType(AdapterType.DEEPSEEK_CHAT).build();
        var result = enricher.enrich(provider, type, List.of(input)).getFirst();
        assertThat(result.capabilities().getLanguage().getReasoningHistory()).isNull();
        var saved = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setModelType(ModelType.LANGUAGE);
        spec.setCapabilities(result.capabilities());
        spec.setCatalogMatch(result.catalogMatch());
        saved.setSpec(spec);
        assertThat(new ModelCapabilityService().effectiveCapabilities(saved, type)
            .getLanguage().getReasoningHistory()).isTrue();

        var explicit = input.toBuilder().capabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder().reasoningHistory(false).build()).build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.REMOTE).build())
            .build();
        var preserved = enricher.enrich(provider, type, List.of(explicit)).getFirst();
        assertThat(preserved.capabilities().getLanguage().getReasoningHistory()).isFalse();
    }

    private ModelCatalogEnricher enricher(String body) {
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"), Mono.just(body),
            ModelsDevCatalogServiceTest.CLOCK);
        assertThat(service.refresh().block().available()).isTrue();
        return new ModelCatalogEnricher(service);
    }

    private DiscoveredModel discovered() {
        return DiscoveredModel.builder().modelId("exact-model").modelType(ModelType.LANGUAGE)
            .adapterType(AdapterType.OPENAI_RESPONSES)
            .features(Set.of(ModelFeature.STREAMING, ModelFeature.REASONING, ModelFeature.STRUCTURED_OUTPUT))
            .source(DiscoverySource.RULE).build();
    }

    static AiProvider provider(String url) {
        var provider = new AiProvider();
        var spec = new AiProvider.AiProviderSpec();
        spec.setProviderType("openai");
        spec.setBaseUrl(url);
        provider.setSpec(spec);
        return provider;
    }
}
