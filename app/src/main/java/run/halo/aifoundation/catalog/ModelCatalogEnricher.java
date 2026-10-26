package run.halo.aifoundation.catalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterProtocol;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

/** Supplements discovery in memory. Has no access to saved models or runtime model resolution. */
@Component
@RequiredArgsConstructor
public class ModelCatalogEnricher {

    private final ModelsDevCatalogService catalog;

    public List<DiscoveredModel> enrich(AiProvider provider, AiProviderType type,
        List<DiscoveredModel> models) {
        var snapshot = catalog.snapshot();
        var providerId = ModelsDevProviderIdentity.resolve(provider, type);
        if (snapshot == null || providerId == null) {
            return models;
        }
        return models.stream().map(model -> {
            var entry = snapshot.catalog().find(providerId, model.modelId());
            if (entry == null || model.modelType() != ModelType.LANGUAGE
                || model.source() == DiscoverySource.MANUAL
                || source(model) == CapabilitySource.MANUAL
                || (entry.apiOverride() != null && !ModelsDevProviderIdentity
                    .normalize(entry.apiOverride()).equals(ModelsDevProviderIdentity.endpoint(provider, type)))) {
                return model;
            }
            var adapter = model.adapterType() != null ? model.adapterType()
                : type.recommendAdapterType(model).orElse(null);
            if (adapter == null || adapter.getModelType() != ModelType.LANGUAGE
                || !type.getSupportedAdapterTypes().contains(adapter)) {
                return model;
            }
            return supplement(model, entry, type, adapter, providerId, snapshot);
        }).toList();
    }

    private DiscoveredModel supplement(DiscoveredModel model, ModelsDevCatalog.Entry entry,
        AiProviderType type, AdapterType adapter, String providerId,
        ModelsDevCatalogService.Snapshot snapshot) {
        var features = new LinkedHashSet<>(model.features());
        var fields = new ArrayList<String>();
        var supported = Set.copyOf(type.getSupportedFeatures(adapter));
        feature(model, features, fields, supported, ModelFeature.TOOL_CALL, entry.tools());
        feature(model, features, fields, supported, ModelFeature.STRUCTURED_OUTPUT,
            entry.structuredOutput());
        feature(model, features, fields, supported, ModelFeature.REASONING, entry.reasoning());

        var language = copy(model.capabilities() == null ? null : model.capabilities().getLanguage());
        var providerImage = providerFeature(model, ModelFeature.VISION);
        var providerAudio = providerFeature(model, ModelFeature.AUDIO_INPUT);
        if (entry.inputs() != null) {
            if (source(model) == CapabilitySource.BUILT_IN) {
                language.setImageInput(null);
                language.setFileInput(null);
                language.setInputMediaTypes(null);
                language.setInputSources(null);
            }
            var image = (providerImage != null ? providerImage : entry.inputs().contains("image"))
                && supported.contains(ModelFeature.VISION);
            var audio = (providerAudio != null ? providerAudio : entry.inputs().contains("audio"))
                && supported.contains(ModelFeature.AUDIO_INPUT)
                && adapter.getProtocol() == AdapterProtocol.CHAT_COMPLETIONS;
            // The generic Responses adapter represents PDF as input_file. Chat Completions
            // cannot represent arbitrary files; video and attachment alone enable nothing.
            var pdf = entry.inputs().contains("pdf")
                && adapter.getProtocol() == AdapterProtocol.RESPONSES;
            feature(model, features, fields, supported, ModelFeature.VISION, image);
            feature(model, features, fields, supported, ModelFeature.AUDIO_INPUT, audio);
            if (language.getImageInput() == null) {
                language.setImageInput(image);
                if (providerImage == null) {
                    fields.add("language.imageInput");
                }
            }
            if (language.getFileInput() == null) {
                language.setFileInput(audio || pdf);
                fields.add("language.fileInput");
            }
            // Explicit empty lists, false flags and provider coverage remain authoritative.
            if (language.getInputMediaTypes() == null) {
                var mediaTypes = new ArrayList<String>();
                if (image && Boolean.TRUE.equals(language.getImageInput())) {
                    mediaTypes.addAll(List.of("image/png", "image/jpeg"));
                }
                if (Boolean.TRUE.equals(language.getFileInput())) {
                    if (audio) {
                        mediaTypes.addAll(List.of("audio/wav", "audio/mpeg"));
                    }
                    if (pdf) {
                        mediaTypes.add("application/pdf");
                    }
                }
                language.setInputMediaTypes(List.copyOf(mediaTypes));
                fields.add("language.inputMediaTypes");
            }
            if (language.getInputSources() == null) {
                language.setInputSources(language.getInputMediaTypes().isEmpty()
                    ? List.of() : List.of(InputSource.DATA));
                fields.add("language.inputSources");
            }
            // A model's ability to reason is not evidence of history replay support.
            if (language.getReasoningHistory() == null) {
                language.setReasoningHistory(false);
            }
        }
        if (fields.isEmpty()) {
            return model;
        }
        var sources = ModelCapabilitySources.builder()
            .language(providerImage != null || providerAudio != null ? CapabilitySource.REMOTE
                : fields.stream().anyMatch(field -> field.startsWith("language."))
                    && source(model) != CapabilitySource.REMOTE ? CapabilitySource.CATALOG : source(model))
            .imageGeneration(model.capabilitySources().getImageGeneration())
            .build();
        var capabilities = ModelCapabilities.builder()
            .language(language.equals(LanguageCapability.unknown()) ? null : language)
            .imageGeneration(model.capabilities() == null ? null
                : model.capabilities().getImageGeneration())
            .sources(sources)
            .build();
        return model.toBuilder()
            .features(Set.copyOf(features))
            .capabilities(capabilities)
            .capabilitySources(sources)
            .confidence(model.confidence() == DiscoveryConfidence.LOW
                ? DiscoveryConfidence.MEDIUM : model.confidence())
            .catalogMatch(new ModelCatalogMatch(providerId, model.modelId(), snapshot.fetchedAt(),
                List.copyOf(fields)))
            .build();
    }

    private void feature(DiscoveredModel model, Set<ModelFeature> features, List<String> fields,
        Set<ModelFeature> supported, ModelFeature feature, Boolean value) {
        if (model.featureEvidence().containsKey(feature)) {
            if (Boolean.TRUE.equals(model.featureEvidence().get(feature))) {
                features.add(feature);
            } else {
                features.remove(feature);
            }
            return;
        }
        if (value == null) {
            return;
        }
        var language = model.capabilities() == null ? null : model.capabilities().getLanguage();
        if (feature == ModelFeature.VISION && language != null && language.getImageInput() != null
            && source(model) != CapabilitySource.BUILT_IN) {
            if (!language.getImageInput()) {
                features.remove(feature);
            }
            return;
        }
        if (feature == ModelFeature.AUDIO_INPUT && language != null
            && Boolean.FALSE.equals(language.getFileInput())) {
            features.remove(feature);
            return;
        }
        // Positive remote metadata is also evidence. Missing features are not explicit negatives.
        if (model.source() == DiscoverySource.REMOTE && model.features().contains(feature)) {
            return;
        }
        if (value && supported.contains(feature)) {
            features.add(feature);
        } else {
            features.remove(feature);
        }
        fields.add("features." + feature.getValue());
    }

    private CapabilitySource source(DiscoveredModel model) {
        var source = model.capabilitySources().getLanguage();
        if ((source == null || source == CapabilitySource.UNKNOWN) && model.capabilities() != null
            && model.capabilities().getSources() != null) {
            source = model.capabilities().getSources().getLanguage();
        }
        return source == null ? CapabilitySource.UNKNOWN : source;
    }

    private Boolean providerFeature(DiscoveredModel model, ModelFeature feature) {
        var declared = model.featureEvidence().get(feature);
        if (declared != null) {
            return declared;
        }
        return model.source() == DiscoverySource.REMOTE && model.features().contains(feature)
            ? Boolean.TRUE : null;
    }

    private LanguageCapability copy(LanguageCapability value) {
        return value == null ? LanguageCapability.unknown() : LanguageCapability.builder()
            .imageInput(value.getImageInput()).fileInput(value.getFileInput())
            .reasoningHistory(value.getReasoningHistory())
            .inputMediaTypes(value.getInputMediaTypes()).inputSources(value.getInputSources()).build();
    }
}
