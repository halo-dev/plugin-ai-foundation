package run.halo.aifoundation.service.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;

@Component
public class ModelCapabilityService {

    public ModelCapabilities effectiveCapabilities(AiModel model,
        @Nullable AiProviderType providerType) {
        if (model == null || model.getSpec() == null) {
            return ModelCapabilities.empty();
        }
        var spec = model.getSpec();
        var explicit = spec.getCapabilities();
        var sources = effectiveSources(explicit, spec.getCapabilitySources());
        var language = copy(explicit == null ? null : explicit.getLanguage());
        var imageGeneration = copy(explicit == null ? null : explicit.getImageGeneration());

        if (spec.getModelType() == ModelType.LANGUAGE) {
            language = applyLanguageDefaults(language, spec.getFeatures());
        }
        if (spec.getModelType() == ModelType.IMAGE_GENERATION
            && sources.getImageGeneration() != CapabilitySource.MANUAL) {
            imageGeneration = applyImageGenerationDefaults(imageGeneration, spec.getAdapterType(),
                providerType);
        }
        return ModelCapabilities.builder()
            .language(language)
            .imageGeneration(imageGeneration)
            .sources(sources)
            .build();
    }

    private LanguageCapability applyLanguageDefaults(LanguageCapability language,
        List<ModelFeature> features) {
        var result = language;
        var hasVision = features != null && features.contains(ModelFeature.VISION);
        var hasAudioInput = features != null && features.contains(ModelFeature.AUDIO_INPUT);
        if (hasVision) {
            result = result == null ? LanguageCapability.unknown() : result;
            if (result.getImageInput() == null) {
                result.setImageInput(true);
            }
        }
        if (hasAudioInput) {
            result = result == null ? LanguageCapability.unknown() : result;
            if (result.getFileInput() == null) {
                result.setFileInput(true);
            }
        }
        if (result != null && Boolean.TRUE.equals(result.getImageInput())) {
            if (result.getInputMediaTypes() == null || result.getInputMediaTypes().isEmpty()) {
                result.setInputMediaTypes(List.of("image/*"));
            }
            if (result.getInputSources() == null || result.getInputSources().isEmpty()) {
                result.setInputSources(List.of(InputSource.DATA));
            }
        }
        if (result != null && hasAudioInput && Boolean.TRUE.equals(result.getFileInput())) {
            appendDefault(result.getInputMediaTypes(), "audio/*", result::setInputMediaTypes);
            if (result.getInputSources() == null || result.getInputSources().isEmpty()) {
                result.setInputSources(List.of(InputSource.DATA));
            }
        }
        return result;
    }

    private <T> void appendDefault(List<T> values, T value, Consumer<List<T>> setter) {
        if (values == null || values.isEmpty()) {
            setter.accept(List.of(value));
            return;
        }
        if (values.contains(value)) {
            return;
        }
        var next = new ArrayList<>(values);
        next.add(value);
        setter.accept(next);
    }

    private ImageGenerationCapability applyImageGenerationDefaults(
        ImageGenerationCapability imageGeneration, AdapterType adapterType,
        @Nullable AiProviderType providerType) {
        var result = imageGeneration == null
            ? ImageGenerationCapability.unknown()
            : imageGeneration;
        if (adapterType == null && providerType != null) {
            adapterType = providerType.recommendAdapterType(ModelType.IMAGE_GENERATION)
                .orElse(null);
        }
        if (adapterType != null && adapterType.getModelType() == ModelType.IMAGE_GENERATION) {
            if (result.getTextToImage() == null) {
                result.setTextToImage(true);
            }
            if (result.getMaxImagesPerCall() == null) {
                result.setMaxImagesPerCall(1);
            }
        }
        return result;
    }

    private ModelCapabilitySources effectiveSources(ModelCapabilities capabilities,
        ModelCapabilitySources specSources) {
        var source = specSources != null ? specSources
            : capabilities == null ? null : capabilities.getSources();
        if (source == null) {
            return ModelCapabilitySources.unknown();
        }
        return ModelCapabilitySources.builder()
            .language(source.getLanguage() == null ? CapabilitySource.UNKNOWN
                : source.getLanguage())
            .imageGeneration(source.getImageGeneration() == null ? CapabilitySource.UNKNOWN
                : source.getImageGeneration())
            .build();
    }

    private LanguageCapability copy(LanguageCapability source) {
        if (source == null) {
            return null;
        }
        return LanguageCapability.builder()
            .imageInput(source.getImageInput())
            .fileInput(source.getFileInput())
            .reasoningHistory(source.getReasoningHistory())
            .inputMediaTypes(copyList(source.getInputMediaTypes()))
            .inputSources(copyList(source.getInputSources()))
            .build();
    }

    private ImageGenerationCapability copy(ImageGenerationCapability source) {
        if (source == null) {
            return null;
        }
        return ImageGenerationCapability.builder()
            .textToImage(source.getTextToImage())
            .imageToImage(source.getImageToImage())
            .maskInput(source.getMaskInput())
            .maxImagesPerCall(source.getMaxImagesPerCall())
            .sizes(copyList(source.getSizes()))
            .aspectRatios(copyList(source.getAspectRatios()))
            .outputMediaTypes(copyList(source.getOutputMediaTypes()))
            .build();
    }

    private <T> List<T> copyList(List<T> source) {
        return source == null ? null : new ArrayList<>(source);
    }
}
