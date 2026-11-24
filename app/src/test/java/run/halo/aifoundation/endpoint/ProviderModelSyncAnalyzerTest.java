package run.halo.aifoundation.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class ProviderModelSyncAnalyzerTest {

    private final ProviderModelSyncAnalyzer analyzer = new ProviderModelSyncAnalyzer();

    @Test
    void marksNewExistingAndDisabledModels() {
        var saved = saved("openai-prod-gpt-4-abcd1234", "gpt-4", false);
        var disabledRemote = discovered("gpt-4", ModelType.LANGUAGE);

        var analysis =
                analyzer.analyze(List.of(discovered("gpt-5", ModelType.LANGUAGE), disabledRemote), List.of(saved));

        var newItem = analysis.models().get(0);
        assertThat(newItem.syncStatus()).isEqualTo(ModelSyncStatus.NEW);
        assertThat(newItem.syncable()).isTrue();
        assertThat(newItem.localModelName()).isNull();

        var existingItem = analysis.models().get(1);
        assertThat(existingItem.syncStatus()).isEqualTo(ModelSyncStatus.DISABLED);
        assertThat(existingItem.localModelName()).isEqualTo("openai-prod-gpt-4-abcd1234");
        assertThat(existingItem.localEnabled()).isFalse();
        assertThat(existingItem.localDisplayName()).isEqualTo("gpt-4");
    }

    @Test
    void reportsSavedModelsMissingFromRemoteDiscovery() {
        var missing = saved("openai-prod-old-abcd1234", "old-model", true);

        var analysis = analyzer.analyze(List.of(discovered("gpt-4", ModelType.LANGUAGE)), List.of(missing));

        assertThat(analysis.missingModels()).hasSize(1);
        assertThat(analysis.missingModels().get(0).modelId()).isEqualTo("old-model");
        assertThat(analysis.missingModels().get(0).modelName()).isEqualTo("openai-prod-old-abcd1234");
        assertThat(analysis.missingModels().get(0).enabled()).isTrue();
    }

    @Test
    void reportsDisplayNameDifferenceWithoutApplyingIt() {
        var saved = saved("openai-prod-gpt-4-abcd1234", "gpt-4", true);
        saved.getSpec().setDisplayName("My GPT");
        var remote = discovered("gpt-4", ModelType.LANGUAGE);
        remote = withDisplayName(remote, "GPT-4");

        var analysis = analyzer.analyze(List.of(remote), List.of(saved));

        var difference = difference(analysis, "displayName");
        assertThat(difference.localValue()).isEqualTo("My GPT");
        assertThat(difference.remoteValue()).isEqualTo("GPT-4");
        assertThat(difference.applied()).isFalse();
    }

    @Test
    void marksDiscoveryOwnedDifferencesAsApplied() {
        var saved = saved("openai-prod-gpt-4-abcd1234", "gpt-4", true);
        saved.getSpec().setModelType(ModelType.LANGUAGE);
        saved.getSpec().setAdapterType(AdapterType.OPENAI_CHAT);
        saved.getSpec().setFeatures(List.of(ModelFeature.STREAMING));

        var remote = discovered("gpt-4", ModelType.EMBEDDING);
        remote = withAdapterAndFeatures(remote, AdapterType.OPENAI_EMBEDDING, List.of());

        var analysis = analyzer.analyze(List.of(remote), List.of(saved));

        assertThat(difference(analysis, "modelType").applied()).isTrue();
        assertThat(difference(analysis, "adapterType").applied()).isTrue();
        assertThat(difference(analysis, "features").applied()).isTrue();
    }

    @Test
    void keepsManualCapabilityDomainOutOfSync() {
        var saved = saved("openai-prod-gpt-4-abcd1234", "gpt-4", true);
        saved.getSpec().setFeatures(List.of());
        saved.getSpec()
                .setCapabilities(ModelCapabilities.builder()
                        .language(LanguageCapability.builder().imageInput(false).build())
                        .sources(ModelCapabilitySources.builder()
                                .language(CapabilitySource.MANUAL)
                                .build())
                        .build());
        saved.getSpec()
                .setCapabilitySources(ModelCapabilitySources.builder()
                        .language(CapabilitySource.MANUAL)
                        .build());

        var remote = discovered("gpt-4", ModelType.LANGUAGE);
        remote = withLanguageCapability(
                remote, LanguageCapability.builder().imageInput(true).build());

        var analysis = analyzer.analyze(List.of(remote), List.of(saved));

        var difference = difference(analysis, "capabilities.language.imageInput");
        assertThat(difference.localValue()).isEqualTo("false");
        assertThat(difference.remoteValue()).isEqualTo("true");
        assertThat(difference.applied()).isFalse();
        assertThat(difference(analysis, "features").applied()).isFalse();
    }

    @Test
    void manuallyCreatedModelIsNotSyncable() {
        var saved = saved("manual-gpt-4", "gpt-4", true);
        saved.getSpec().setDisplayName("Manual GPT");
        saved.getSpec().setDiscoverySource(DiscoverySource.MANUAL);

        var analysis = analyzer.analyze(List.of(discovered("gpt-4", ModelType.LANGUAGE)), List.of(saved));

        assertThat(analysis.models().get(0).syncable()).isFalse();
        assertThat(analysis.models().get(0).differences()).isNotEmpty();
        assertThat(analysis.models().get(0).differences())
                .allSatisfy(difference -> assertThat(difference.applied()).isFalse());
    }

    private static ModelSyncDifference difference(ProviderModelSyncAnalyzer.DiscoveryAnalysis analysis, String field) {
        return analysis.models().get(0).differences().stream()
                .filter(difference -> difference.field().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing difference: " + field));
    }

    private static DiscoveredModelItem discovered(String modelId, ModelType modelType) {
        return new DiscoveredModelItem(
                modelId,
                modelId,
                "",
                modelType,
                List.of(ModelFeature.STREAMING),
                DiscoverySource.RULE,
                DiscoveryConfidence.LOW,
                modelType == ModelType.LANGUAGE ? AdapterType.OPENAI_CHAT : AdapterType.OPENAI_EMBEDDING,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                List.of());
    }

    private static DiscoveredModelItem withDisplayName(DiscoveredModelItem item, String displayName) {
        return new DiscoveredModelItem(
                item.modelId(),
                displayName,
                item.name(),
                item.modelType(),
                item.features(),
                item.source(),
                item.confidence(),
                item.adapterType(),
                item.capabilities(),
                item.capabilitySources(),
                item.catalogMatch(),
                item.catalogParameters(),
                item.syncStatus(),
                item.localModelName(),
                item.localEnabled(),
                item.localDisplayName(),
                item.syncable(),
                item.differences());
    }

    private static DiscoveredModelItem withAdapterAndFeatures(
            DiscoveredModelItem item, AdapterType adapterType, List<ModelFeature> features) {
        return new DiscoveredModelItem(
                item.modelId(),
                item.displayName(),
                item.name(),
                item.modelType(),
                features,
                item.source(),
                item.confidence(),
                adapterType,
                item.capabilities(),
                item.capabilitySources(),
                item.catalogMatch(),
                item.catalogParameters(),
                item.syncStatus(),
                item.localModelName(),
                item.localEnabled(),
                item.localDisplayName(),
                item.syncable(),
                item.differences());
    }

    private static DiscoveredModelItem withLanguageCapability(DiscoveredModelItem item, LanguageCapability language) {
        return new DiscoveredModelItem(
                item.modelId(),
                item.displayName(),
                item.name(),
                item.modelType(),
                item.features(),
                item.source(),
                item.confidence(),
                item.adapterType(),
                ModelCapabilities.builder().language(language).build(),
                item.capabilitySources(),
                item.catalogMatch(),
                item.catalogParameters(),
                item.syncStatus(),
                item.localModelName(),
                item.localEnabled(),
                item.localDisplayName(),
                item.syncable(),
                item.differences());
    }

    private static AiModel saved(String name, String modelId, boolean enabled) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("openai-prod");
        spec.setModelId(modelId);
        spec.setDisplayName(modelId);
        spec.setEnabled(enabled);
        spec.setModelType(ModelType.LANGUAGE);
        spec.setAdapterType(AdapterType.OPENAI_CHAT);
        spec.setFeatures(List.of(ModelFeature.STREAMING));
        spec.setDiscoverySource(DiscoverySource.RULE);
        spec.setDiscoveryConfidence(DiscoveryConfidence.LOW);
        model.setSpec(spec);
        return model;
    }
}
