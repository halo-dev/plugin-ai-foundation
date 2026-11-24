package run.halo.aifoundation.endpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.CapabilityDomain;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

/** Correlates one discovery response with the saved models of the same provider. */
@Component
public class ProviderModelSyncAnalyzer {

    /** Adds saved-model sync state to discovered items and reports saved models that the provider no longer returns. */
    public DiscoveryAnalysis analyze(List<DiscoveredModelItem> discoveredModels, List<AiModel> savedModels) {
        Map<String, AiModel> savedByModelId = new LinkedHashMap<>();
        for (var saved : savedModels) {
            var spec = saved.getSpec();
            if (spec == null || spec.getModelId() == null || saved.getMetadata() == null) {
                continue;
            }
            savedByModelId.putIfAbsent(spec.getModelId(), saved);
        }
        Set<String> discoveredModelIds = discoveredModels.stream()
                .map(DiscoveredModelItem::modelId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        var items = discoveredModels.stream()
                .map(item -> withSyncState(item, savedByModelId.get(item.modelId())))
                .toList();
        var missing = savedByModelId.entrySet().stream()
                .filter(entry -> !discoveredModelIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(ProviderModelSyncAnalyzer::missingItem)
                .toList();
        return new DiscoveryAnalysis(items, missing);
    }

    private DiscoveredModelItem withSyncState(DiscoveredModelItem remote, @Nullable AiModel saved) {
        if (saved == null || saved.getSpec() == null) {
            return new DiscoveredModelItem(
                    remote.modelId(),
                    remote.displayName(),
                    remote.name(),
                    remote.modelType(),
                    remote.features(),
                    remote.source(),
                    remote.confidence(),
                    remote.adapterType(),
                    remote.capabilities(),
                    remote.capabilitySources(),
                    remote.catalogMatch(),
                    remote.catalogParameters(),
                    ModelSyncStatus.NEW,
                    null,
                    null,
                    null,
                    true,
                    List.of());
        }
        var spec = saved.getSpec();
        var enabled = spec.isEnabled();
        var syncable = isSyncable(spec);
        return new DiscoveredModelItem(
                remote.modelId(),
                remote.displayName(),
                saved.getMetadata() == null
                        ? remote.name()
                        : saved.getMetadata().getName(),
                remote.modelType(),
                remote.features(),
                remote.source(),
                remote.confidence(),
                remote.adapterType(),
                remote.capabilities(),
                remote.capabilitySources(),
                remote.catalogMatch(),
                remote.catalogParameters(),
                enabled ? ModelSyncStatus.EXISTING : ModelSyncStatus.DISABLED,
                saved.getMetadata() == null ? null : saved.getMetadata().getName(),
                enabled,
                spec.getDisplayName(),
                syncable,
                differences(remote, spec, syncable));
    }

    private static boolean isSyncable(AiModel.AiModelSpec spec) {
        var source = spec.getDiscoverySource();
        return source != null && source != DiscoverySource.MANUAL;
    }

    static List<ModelSyncDifference> differences(
            DiscoveredModelItem remote, AiModel.AiModelSpec saved, boolean syncable) {
        var differences = new ArrayList<ModelSyncDifference>();
        // Display name is administrator-owned: report the drift but never apply it.
        add(differences, "displayName", saved.getDisplayName(), remote.displayName(), false);
        add(differences, "modelType", value(saved.getModelType()), value(remote.modelType()), syncable);
        add(differences, "adapterType", value(saved.getAdapterType()), value(remote.adapterType()), syncable);

        var languageManual = isManual(saved, CapabilityDomain.LANGUAGE);
        var imageGenerationManual = isManual(saved, CapabilityDomain.IMAGE_GENERATION);
        var featuresApplied = syncable && !languageManual && !imageGenerationManual;
        add(differences, "features", features(saved.getFeatures()), features(remote.features()), featuresApplied);

        var remoteCapabilities = remote.capabilities();
        if (remoteCapabilities != null) {
            var languageApplied = syncable && !languageManual;
            addLanguageDifferences(differences, saved, remoteCapabilities.getLanguage(), languageApplied);
            var imageApplied = syncable && !imageGenerationManual;
            addImageDifferences(differences, saved, remoteCapabilities.getImageGeneration(), imageApplied);
        }
        return differences;
    }

    private static void addLanguageDifferences(
            List<ModelSyncDifference> differences,
            AiModel.AiModelSpec saved,
            @Nullable LanguageCapability remote,
            boolean applied) {
        var local =
                saved.getCapabilities() == null ? null : saved.getCapabilities().getLanguage();
        add(
                differences,
                "capabilities.language.imageInput",
                booleanText(local == null ? null : local.getImageInput()),
                booleanText(remote == null ? null : remote.getImageInput()),
                applied);
        add(
                differences,
                "capabilities.language.fileInput",
                booleanText(local == null ? null : local.getFileInput()),
                booleanText(remote == null ? null : remote.getFileInput()),
                applied);
        add(
                differences,
                "capabilities.language.reasoningHistory",
                booleanText(local == null ? null : local.getReasoningHistory()),
                booleanText(remote == null ? null : remote.getReasoningHistory()),
                applied);
        add(
                differences,
                "capabilities.language.inputMediaTypes",
                list(local == null ? null : local.getInputMediaTypes()),
                list(remote == null ? null : remote.getInputMediaTypes()),
                applied);
        add(
                differences,
                "capabilities.language.inputSources",
                list(
                        local == null || local.getInputSources() == null
                                ? null
                                : local.getInputSources().stream()
                                        .map(InputSource::getValue)
                                        .toList()),
                list(
                        remote == null || remote.getInputSources() == null
                                ? null
                                : remote.getInputSources().stream()
                                        .map(InputSource::getValue)
                                        .toList()),
                applied);
    }

    private static void addImageDifferences(
            List<ModelSyncDifference> differences,
            AiModel.AiModelSpec saved,
            @Nullable ImageGenerationCapability remote,
            boolean applied) {
        var local =
                saved.getCapabilities() == null ? null : saved.getCapabilities().getImageGeneration();
        add(
                differences,
                "capabilities.imageGeneration.textToImage",
                booleanText(local == null ? null : local.getTextToImage()),
                booleanText(remote == null ? null : remote.getTextToImage()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.imageToImage",
                booleanText(local == null ? null : local.getImageToImage()),
                booleanText(remote == null ? null : remote.getImageToImage()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.maskInput",
                booleanText(local == null ? null : local.getMaskInput()),
                booleanText(remote == null ? null : remote.getMaskInput()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.maxImagesPerCall",
                numberText(local == null ? null : local.getMaxImagesPerCall()),
                numberText(remote == null ? null : remote.getMaxImagesPerCall()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.sizes",
                list(local == null ? null : local.getSizes()),
                list(remote == null ? null : remote.getSizes()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.aspectRatios",
                list(local == null ? null : local.getAspectRatios()),
                list(remote == null ? null : remote.getAspectRatios()),
                applied);
        add(
                differences,
                "capabilities.imageGeneration.outputMediaTypes",
                list(local == null ? null : local.getOutputMediaTypes()),
                list(remote == null ? null : remote.getOutputMediaTypes()),
                applied);
    }

    private static boolean isManual(AiModel.AiModelSpec spec, CapabilityDomain domain) {
        ModelCapabilitySources sources = spec.getCapabilitySources();
        if (sources == null && spec.getCapabilities() != null) {
            sources = spec.getCapabilities().getSources();
        }
        return sources != null && sources.source(domain) == CapabilitySource.MANUAL;
    }

    private static void add(
            List<ModelSyncDifference> differences,
            String field,
            @Nullable String localValue,
            @Nullable String remoteValue,
            boolean applied) {
        var local = blankToNull(localValue);
        var remote = blankToNull(remoteValue);
        if (Objects.equals(local, remote)) {
            return;
        }
        differences.add(new ModelSyncDifference(field, local, remote, applied));
    }

    private static MissingRemoteModelItem missingItem(AiModel saved) {
        var spec = saved.getSpec();
        return new MissingRemoteModelItem(
                saved.getMetadata() == null ? null : saved.getMetadata().getName(),
                spec.getModelId(),
                spec.getDisplayName(),
                spec.getModelType(),
                spec.isEnabled());
    }

    @Nullable
    private static String value(@Nullable Enum<?> value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ModelType modelType) {
            return modelType.getValue();
        }
        if (value instanceof AdapterType adapterType) {
            return adapterType.getValue();
        }
        return value.name();
    }

    @Nullable
    private static String features(@Nullable List<ModelFeature> features) {
        if (features == null || features.isEmpty()) {
            return null;
        }
        return features.stream()
                .map(ModelFeature::getValue)
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    @Nullable
    private static String list(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(", ", values);
    }

    @Nullable
    private static String booleanText(@Nullable Boolean value) {
        return value == null ? null : value.toString();
    }

    @Nullable
    private static String numberText(@Nullable Integer value) {
        return value == null ? null : value.toString();
    }

    @Nullable
    private static String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Result of correlating discovery output with saved models. */
    public record DiscoveryAnalysis(List<DiscoveredModelItem> models, List<MissingRemoteModelItem> missingModels) {}
}
