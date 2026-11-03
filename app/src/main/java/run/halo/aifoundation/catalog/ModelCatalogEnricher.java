package run.halo.aifoundation.catalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
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
        return models.stream()
            .map(model -> enrichModel(provider, type, model, providerId, snapshot))
            .toList();
    }

    private DiscoveredModel enrichModel(AiProvider provider, AiProviderType type,
        DiscoveredModel discovered, String providerId, ModelsDevCatalogService.Snapshot snapshot) {
        var entry = snapshot.catalog().find(providerId, discovered.modelId());
        var model = classify(discovered, entry, provider, type);
        if (entry == null || !entry.matchesModelType(model.modelType())
            || model.source() == DiscoverySource.MANUAL
            || capabilitySource(model) == CapabilitySource.MANUAL) {
            return model;
        }
        var adapter = model.adapterType() != null ? model.adapterType()
            : type.recommendAdapterType(model).orElse(null);
        if (adapter == null || adapter.getModelType() != model.modelType()
            || !type.getSupportedAdapterTypes().contains(adapter)
            || !ModelsDevProviderIdentity.matches(provider, type, entry, adapter)) {
            return model;
        }
        var supplemented = supplement(model, entry, type, adapter, providerId, snapshot);
        var parameters = ModelCatalogParameterMapper.map(provider, type, model, entry, adapter);
        if (parameters == null) {
            return supplemented;
        }
        var fields = new ArrayList<>(supplemented.catalogMatch() == null
            ? List.<String>of() : supplemented.catalogMatch().fields());
        if (model != discovered) {
            fields.add("modelType");
            fields.add("adapterType");
        }
        fields.add("parameters");
        return supplemented.toBuilder().catalogParameters(parameters)
            .catalogMatch(new ModelCatalogMatch(providerId, model.modelId(), snapshot.fetchedAt(),
                List.copyOf(fields)))
            .confidence(supplemented.confidence() == DiscoveryConfidence.LOW
                ? DiscoveryConfidence.MEDIUM : supplemented.confidence())
            .build();
    }

    private DiscoveredModel classify(DiscoveredModel model, ModelsDevCatalog.Entry entry,
        AiProvider provider, AiProviderType type) {
        if (entry == null || entry.matchesModelType(model.modelType())
            || !hasInferredModelType(model)) {
            return model;
        }
        var modelType = Arrays.stream(ModelType.values()).filter(entry::matchesModelType)
            .findFirst().orElse(null);
        if (modelType == null || !type.getSupportedModelTypes().contains(modelType)) {
            return model;
        }
        var adapter = type.recommendAdapterType(modelType).orElse(null);
        if (adapter == null || adapter.getModelType() != modelType
            || !type.getSupportedAdapterTypes().contains(adapter)
            || !ModelsDevProviderIdentity.matches(provider, type, entry, adapter)) {
            return model;
        }
        // Identifier-only discovery guesses the provider's default type. An exact catalog
        // identity can correct that guess, without adding models absent from discovery.
        return model.toBuilder().modelType(modelType).adapterType(adapter).features(Set.of())
            .capabilitySources(ModelCapabilitySources.unknown()).catalogMatch(null)
            .catalogParameters(null).build();
    }

    private boolean hasInferredModelType(DiscoveredModel model) {
        return model.modelType() == ModelType.LANGUAGE
            && model.source() == DiscoverySource.RULE
            && model.confidence() == DiscoveryConfidence.LOW
            && model.featureEvidence().isEmpty()
            && model.capabilities() == null
            && !isExplicitSource(model.capabilitySources().getLanguage())
            && !isExplicitSource(model.capabilitySources().getImageGeneration());
    }

    private boolean isExplicitSource(CapabilitySource source) {
        return source == CapabilitySource.MANUAL || source == CapabilitySource.REMOTE;
    }

    private DiscoveredModel supplement(DiscoveredModel model, ModelsDevCatalog.Entry entry,
        AiProviderType type, AdapterType adapter, String providerId,
        ModelsDevCatalogService.Snapshot snapshot) {
        if (model.modelType() != ModelType.LANGUAGE) {
            return supplementImageOutputs(model, entry, adapter, providerId, snapshot);
        }
        var features = new LinkedHashSet<>(model.features());
        var fields = new ArrayList<String>();
        var supported = Set.copyOf(type.getSupportedFeatures(adapter));
        applyFeature(model, features, fields, supported, ModelFeature.TOOL_CALL, entry.tools());
        applyFeature(model, features, fields, supported, ModelFeature.STRUCTURED_OUTPUT,
            entry.structuredOutput());
        applyFeature(model, features, fields, supported, ModelFeature.REASONING, entry.reasoning());

        var language = copyLanguageCapability(model.capabilities() == null
            ? null : model.capabilities().getLanguage());
        var providerImage = providerFeature(model, ModelFeature.VISION);
        var providerAudio = providerFeature(model, ModelFeature.AUDIO_INPUT);
        if (entry.inputs() != null) {
            if (capabilitySource(model) == CapabilitySource.BUILT_IN) {
                language.setImageInput(null);
                language.setFileInput(null);
                language.setInputMediaTypes(null);
                language.setInputSources(null);
            }
            var imageTypes = imageMediaTypes(entry.inputs());
            var audioTypes = audioMediaTypes(entry.inputs());
            var image = (providerImage != null ? providerImage : !imageTypes.isEmpty())
                && supported.contains(ModelFeature.VISION);
            var audio = (providerAudio != null ? providerAudio : !audioTypes.isEmpty())
                && supported.contains(ModelFeature.AUDIO_INPUT)
                && adapter.getProtocol() == AdapterProtocol.CHAT_COMPLETIONS;
            // The generic Responses adapter represents PDF as input_file. Chat Completions
            // cannot represent arbitrary files; video and attachment alone enable nothing.
            var pdf = (entry.inputs().contains("pdf") || entry.inputs().contains("application/pdf"))
                && adapter.getProtocol() == AdapterProtocol.RESPONSES;
            applyFeature(model, features, fields, supported, ModelFeature.VISION, image);
            applyFeature(model, features, fields, supported, ModelFeature.AUDIO_INPUT, audio);
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
                    mediaTypes.addAll(imageTypes.isEmpty() ? List.of("image/png", "image/jpeg")
                        : imageTypes);
                }
                if (Boolean.TRUE.equals(language.getFileInput())) {
                    if (audio) {
                        mediaTypes.addAll(audioTypes.isEmpty() ? List.of("audio/wav", "audio/mpeg")
                            : audioTypes);
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
        }
        if (fields.isEmpty()) {
            return features.equals(model.features()) ? model
                : model.toBuilder().features(Set.copyOf(features)).build();
        }
        // Keep the published enum stable: older readers reject unknown source values.
        // Per-field catalog attribution belongs exclusively to catalogMatch.
        var sources = ModelCapabilitySources.builder()
            .language(languageSource(model, fields, providerImage, providerAudio))
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

    private CapabilitySource languageSource(DiscoveredModel model, List<String> fields,
        Boolean providerImage, Boolean providerAudio) {
        if (providerImage != null || providerAudio != null) {
            return CapabilitySource.REMOTE;
        }
        var currentSource = capabilitySource(model);
        var hasCatalogMedia = fields.stream().anyMatch(field -> field.startsWith("language."));
        if (hasCatalogMedia && currentSource != CapabilitySource.REMOTE) {
            return CapabilitySource.UNKNOWN;
        }
        return currentSource;
    }

    private DiscoveredModel supplementImageOutputs(DiscoveredModel model,
        ModelsDevCatalog.Entry entry, AdapterType adapter, String providerId,
        ModelsDevCatalogService.Snapshot snapshot) {
        if (model.modelType() != ModelType.IMAGE_GENERATION || entry.outputs() == null) {
            return model;
        }
        var explicit = model.capabilities() == null
            ? null : model.capabilities().getImageGeneration();
        var image = explicit == null ? ImageGenerationCapability.unknown() : explicit;
        if (image.getOutputMediaTypes() != null
            && capabilitySource(model) != CapabilitySource.BUILT_IN) {
            return model;
        }
        // These formats have explicit response-media handling in the corresponding image
        // clients. Other adapters remain unknown unless discovery supplies built-in coverage.
        var supported = image.getOutputMediaTypes() != null ? image.getOutputMediaTypes()
            : switch (adapter) {
                case OPENAI_IMAGE -> List.of("image/png", "image/jpeg", "image/webp");
                case OPENROUTER_IMAGE ->
                    List.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");
                default -> null;
            };
        if (supported == null) {
            return model;
        }
        var declared = entry.outputs().stream()
            .filter(value -> value.startsWith("image/") && !value.contains("*"))
            .distinct().toList();
        if (declared.isEmpty()) {
            return model;
        }
        // Catalog output modalities narrow known adapter support; they cannot enable
        // editing, masks, output counts, sizes or an otherwise unknown output format.
        var outputs = declared.stream()
            .filter(value -> supported.contains(value) || supported.contains("image/*"))
            .toList();
        var copy = ImageGenerationCapability.builder()
            .textToImage(image.getTextToImage()).imageToImage(image.getImageToImage())
            .maskInput(image.getMaskInput()).maxImagesPerCall(image.getMaxImagesPerCall())
            .sizes(image.getSizes()).aspectRatios(image.getAspectRatios())
            .outputMediaTypes(outputs).build();
        var sources = ModelCapabilitySources.builder()
            .language(model.capabilitySources().getLanguage())
            .imageGeneration(capabilitySource(model) == CapabilitySource.REMOTE
                ? CapabilitySource.REMOTE : CapabilitySource.UNKNOWN).build();
        return model.toBuilder()
            .capabilities(ModelCapabilities.builder().language(model.capabilities() == null
                    ? null : model.capabilities().getLanguage())
                .imageGeneration(copy).sources(sources).build())
            .capabilitySources(sources)
            .catalogMatch(new ModelCatalogMatch(providerId, model.modelId(), snapshot.fetchedAt(),
                List.of("imageGeneration.outputMediaTypes")))
            .build();
    }

    private void applyFeature(DiscoveredModel model, Set<ModelFeature> features,
        List<String> fields,
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
            && capabilitySource(model) != CapabilitySource.BUILT_IN) {
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

    private List<String> imageMediaTypes(List<String> inputs) {
        var types = new LinkedHashSet<String>();
        for (var input : inputs) {
            if ("image".equals(input)) {
                types.addAll(List.of("image/png", "image/jpeg"));
            } else if (input.startsWith("image/") && !input.contains("*")) {
                types.add(input);
            }
        }
        return List.copyOf(types);
    }

    private List<String> audioMediaTypes(List<String> inputs) {
        var types = new LinkedHashSet<String>();
        for (var input : inputs) {
            // The Chat Completions adapter currently serializes only wav and mp3 input_audio.
            switch (input) {
                case "audio" -> types.addAll(List.of("audio/wav", "audio/mpeg"));
                case "audio/wav", "audio/x-wav" -> types.add("audio/wav");
                case "audio/mpeg", "audio/mp3" -> types.add("audio/mpeg");
                default -> { }
            }
        }
        return List.copyOf(types);
    }

    private CapabilitySource capabilitySource(DiscoveredModel model) {
        var image = model.modelType() == ModelType.IMAGE_GENERATION;
        var source = image ? model.capabilitySources().getImageGeneration()
            : model.capabilitySources().getLanguage();
        if ((source == null || source == CapabilitySource.UNKNOWN) && model.capabilities() != null
            && model.capabilities().getSources() != null) {
            source = image ? model.capabilities().getSources().getImageGeneration()
                : model.capabilities().getSources().getLanguage();
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

    private LanguageCapability copyLanguageCapability(LanguageCapability value) {
        return value == null ? LanguageCapability.unknown() : LanguageCapability.builder()
            .imageInput(value.getImageInput()).fileInput(value.getFileInput())
            .reasoningHistory(value.getReasoningHistory())
            .inputMediaTypes(value.getInputMediaTypes())
            .inputSources(value.getInputSources()).build();
    }
}