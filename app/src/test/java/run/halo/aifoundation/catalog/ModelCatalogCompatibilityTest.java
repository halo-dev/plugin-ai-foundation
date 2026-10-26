package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.app.infra.utils.JsonUtils;

class ModelCatalogCompatibilityTest {

    @TempDir
    Path directory;

    @Test
    void keepsPublishedSourceValuesAndExhaustiveConsumersUnchanged() {
        assertThat(CapabilitySource.values()).extracting(CapabilitySource::getValue)
            .containsExactly("remote", "built-in", "manual", "unknown");
        for (var source : CapabilitySource.values()) {
            // A published caller's exhaustive switch must still compile unchanged.
            var published = switch (source) {
                case REMOTE -> PublishedSource.REMOTE;
                case BUILT_IN -> PublishedSource.BUILT_IN;
                case MANUAL -> PublishedSource.MANUAL;
                case UNKNOWN -> PublishedSource.UNKNOWN;
            };
            assertThat(JsonUtils.jsonToObject(JsonUtils.objectToJson(source), PublishedSource.class))
                .isEqualTo(published);
        }
    }

    @Test
    void catalogSnapshotCanBeReadAndResavedWithoutProvenanceByPublishedReader() {
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            Mono.just(ModelsDevCatalogServiceTest.CATALOG), ModelsDevCatalogServiceTest.CLOCK);
        assertThat(service.refresh().block().available()).isTrue();
        var discovered = DiscoveredModel.builder().modelId("exact-model")
            .modelType(ModelType.LANGUAGE).adapterType(AdapterType.OPENAI_RESPONSES)
            .features(Set.of(ModelFeature.STREAMING)).source(DiscoverySource.RULE).build();
        var result = new ModelCatalogEnricher(service).enrich(
            ModelCatalogEnricherTest.provider(null), new OpenAiProvider(), List.of(discovered))
            .getFirst();
        var model = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("openai-prod");
        spec.setModelId(result.modelId());
        spec.setModelType(result.modelType());
        spec.setAdapterType(result.adapterType());
        spec.setFeatures(List.copyOf(result.features()));
        spec.setCapabilities(result.capabilities());
        spec.setCapabilitySources(result.capabilitySources());
        spec.setCatalogMatch(result.catalogMatch());
        model.setSpec(spec);

        var json = JsonUtils.objectToJson(model);
        var legacy = JsonUtils.jsonToObject(json, PublishedModel.class);
        assertThat(legacy.spec().capabilitySources().language()).isEqualTo(PublishedSource.UNKNOWN);
        assertThat(legacy.spec().capabilities().sources().language()).isEqualTo(PublishedSource.UNKNOWN);
        assertThat(legacy.spec().capabilities().language()).isEqualTo(result.capabilities().getLanguage());
        assertThat(legacy.spec().modelId()).isEqualTo("exact-model");
        assertThat(json).contains("\"catalogMatch\"").doesNotContain("\"catalog\"");

        var restored = JsonUtils.jsonToObject(JsonUtils.objectToJson(legacy), AiModel.class);
        assertThat(restored.getSpec().getCatalogMatch()).isNull();
        assertThat(new ModelCapabilityService().effectiveCapabilities(restored, null).getLanguage())
            .isEqualTo(result.capabilities().getLanguage());

        // Negative control: this reader would have rejected the original PR's enum addition.
        assertThatThrownBy(() -> JsonUtils.jsonToObject(
            json.replace("\"unknown\"", "\"catalog\""), PublishedModel.class))
            .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsExistingModelsWithoutCatalogMetadataOrRewritingTheirSource() {
        var model = JsonUtils.jsonToObject("""
            {"spec":{"providerName":"openai-prod","modelId":"existing-model",
              "modelType":"language","adapterType":"openai-chat","features":["vision"],
              "capabilitySources":{"language":"manual","imageGeneration":"unknown"},
              "capabilities":{"language":{"imageInput":false,"inputMediaTypes":[],
                "inputSources":[]},"sources":{"language":"manual"}}}}
            """, AiModel.class);
        assertThat(model.getSpec().getCatalogMatch()).isNull();
        var capabilities = new ModelCapabilityService().effectiveCapabilities(model, null);
        assertThat(capabilities.getSources().getLanguage()).isEqualTo(CapabilitySource.MANUAL);
        assertThat(capabilities.getLanguage().getImageInput()).isFalse();
        assertThat(capabilities.getLanguage().getInputMediaTypes()).isEmpty();
    }

    // Frozen wire projection of v1.3.0 (5e0ed899), whose source enum is also used by v1.0.0.
    // Intentionally omit catalogMatch; use Halo's mapper to exercise unknown-field handling.
    record PublishedModel(PublishedSpec spec) {
    }

    record PublishedSpec(String providerName, String modelId, ModelType modelType,
                         AdapterType adapterType, List<ModelFeature> features,
                         PublishedCapabilities capabilities, PublishedSources capabilitySources) {
    }

    record PublishedCapabilities(LanguageCapability language, PublishedSources sources) {
    }

    record PublishedSources(PublishedSource language, PublishedSource imageGeneration) {
    }

    enum PublishedSource {
        REMOTE("remote"), BUILT_IN("built-in"), MANUAL("manual"), UNKNOWN("unknown");

        private final String value;

        PublishedSource(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }

        @JsonCreator
        public static PublishedSource fromValue(String value) {
            return Arrays.stream(values()).filter(source -> source.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported capability source: " + value));
        }
    }
}
