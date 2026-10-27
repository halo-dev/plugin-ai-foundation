package run.halo.aifoundation.service.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.catalog.ModelCatalogMatch;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.AiProviderType;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.LanguageModelProviderOptions;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.app.extension.Metadata;

class ModelCapabilityServiceTest {

    private final ModelCapabilityService service = new ModelCapabilityService();
    private final ModelCapabilityMatcher matcher = new ModelCapabilityMatcher();

    @Test
    void catalogMediaCoverageIsNotExpandedByLegacyFeatureDefaults() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setFeatures(List.of(ModelFeature.AUDIO_INPUT, ModelFeature.VISION));
        model.getSpec().setCapabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder().imageInput(true).fileInput(true)
                .reasoningHistory(false)
                .inputMediaTypes(List.of("image/png", "audio/wav"))
                .inputSources(List.of(InputSource.DATA)).build()).build());
        model.getSpec().setCapabilitySources(ModelCapabilitySources.builder()
            .language(CapabilitySource.UNKNOWN).build());
        model.getSpec().setCatalogMatch(new ModelCatalogMatch("openai", "model", null,
            List.of("language.inputMediaTypes")));

        var capabilities = service.effectiveCapabilities(model, null);
        assertThat(capabilities.getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "audio/wav");
        assertThat(capabilities.getLanguage().getInputSources()).containsExactly(InputSource.DATA);
        assertThat(capabilities.getLanguage().getReasoningHistory()).isFalse();

        // Changing an unrelated feature marks the language domain manual, but leaves the
        // catalog's precise media coverage intact.
        model.getSpec().setCapabilitySources(ModelCapabilitySources.builder()
            .language(CapabilitySource.MANUAL).build());
        assertThat(service.effectiveCapabilities(model, null).getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "audio/wav");

        // Editing a media field removes its attribution, not the precise coverage contract.
        model.getSpec().setCatalogMatch(new ModelCatalogMatch("openai", "model", null, List.of()));
        assertThat(service.effectiveCapabilities(model, null).getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "audio/wav");
        assertThat(matcher.match(service.effectiveCapabilities(model, null),
            ModelCapabilityRequirement.builder().language(LanguageCapability.builder()
                .fileInput(true).inputMediaTypes(List.of("audio/ogg"))
                .inputSources(List.of(InputSource.DATA)).build()).build()).matched()).isFalse();

        // A pre-catalog reader drops the optional provenance. Its existing audio default is
        // deliberately unchanged; decoding compatibility does not promise downgrade parity.
        model.getSpec().setCatalogMatch(null);
        assertThat(service.effectiveCapabilities(model, null).getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "audio/wav", "audio/*");
    }

    @Test
    void catalogImportsDefaultAbsentCoverageButPreserveExplicitEmptyLists() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setFeatures(List.of(ModelFeature.VISION, ModelFeature.AUDIO_INPUT));
        model.getSpec().setCatalogMatch(new ModelCatalogMatch("openai", "model", null,
            List.of("language.inputMediaTypes")));
        var defaults = service.effectiveCapabilities(model, null).getLanguage();
        assertThat(defaults.getInputMediaTypes()).containsExactly("image/*", "audio/*");
        assertThat(defaults.getInputSources()).containsExactly(InputSource.DATA);

        model.getSpec().setCapabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder().inputMediaTypes(List.of())
                .inputSources(List.of()).build()).build());
        var explicit = service.effectiveCapabilities(model, null).getLanguage();
        assertThat(explicit.getInputMediaTypes()).isEmpty();
        assertThat(explicit.getInputSources()).isEmpty();
    }

    @Test
    void effectiveCapabilities_defaultsVisionFeatureToImageInput() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setFeatures(List.of(ModelFeature.STREAMING, ModelFeature.VISION));

        var capabilities = service.effectiveCapabilities(model, null);

        assertThat(capabilities.getLanguage().getImageInput()).isTrue();
        assertThat(capabilities.getLanguage().getInputMediaTypes()).containsExactly("image/*");
        assertThat(capabilities.getLanguage().getInputSources()).containsExactly(InputSource.DATA);
        assertThat(matcher.match(capabilities,
            ModelCapabilityRequirement.languageImageInput("image/png", InputSource.DATA))
            .matched()).isTrue();
    }

    @Test
    void effectiveCapabilities_keepManualDomainUntouched() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setFeatures(List.of(ModelFeature.VISION));
        model.getSpec().setCapabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .imageInput(false)
                .build())
            .build());
        model.getSpec().setCapabilitySources(ModelCapabilitySources.builder()
            .language(CapabilitySource.MANUAL)
            .build());

        var capabilities = service.effectiveCapabilities(model, null);

        assertThat(capabilities.getLanguage().getImageInput()).isFalse();
        assertThat(capabilities.getLanguage().getInputMediaTypes()).isNull();
    }

    @Test
    void effectiveCapabilities_defaultsImageInputDetailsWhenImageInputIsExplicit() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setCapabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .imageInput(true)
                .build())
            .build());
        model.getSpec().setCapabilitySources(ModelCapabilitySources.builder()
            .language(CapabilitySource.MANUAL)
            .build());

        var capabilities = service.effectiveCapabilities(model, null);

        assertThat(capabilities.getLanguage().getImageInput()).isTrue();
        assertThat(capabilities.getLanguage().getInputMediaTypes()).containsExactly("image/*");
        assertThat(capabilities.getLanguage().getInputSources()).containsExactly(InputSource.DATA);
        assertThat(matcher.match(capabilities,
            ModelCapabilityRequirement.languageImageInput("image/png", InputSource.DATA))
            .matched()).isTrue();
    }

    @Test
    void effectiveCapabilities_defaultsAudioFeatureToFileInput() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setFeatures(List.of(ModelFeature.AUDIO_INPUT));

        var capabilities = service.effectiveCapabilities(model, null);

        assertThat(capabilities.getLanguage().getFileInput()).isTrue();
        assertThat(capabilities.getLanguage().getInputMediaTypes()).containsExactly("audio/*");
        assertThat(capabilities.getLanguage().getInputSources()).containsExactly(InputSource.DATA);
        assertThat(matcher.match(capabilities,
            ModelCapabilityRequirement.builder()
                .language(LanguageCapability.builder()
                    .fileInput(true)
                    .inputMediaTypes(List.of("audio/wav"))
                    .inputSources(List.of(InputSource.DATA))
                    .build())
                .build())
            .matched()).isTrue();
    }

    @Test
    void effectiveCapabilities_fillImageGenerationDefaultsFromAdapter() {
        var model = model(ModelType.IMAGE_GENERATION);
        model.getSpec().setAdapterType(AdapterType.OPENAI_IMAGE);

        var capabilities = service.effectiveCapabilities(model, null);

        assertThat(capabilities.getImageGeneration().getTextToImage()).isTrue();
        assertThat(capabilities.getImageGeneration().getMaxImagesPerCall()).isEqualTo(1);
    }

    @Test
    void effectiveCapabilities_canUseProviderRecommendedImageAdapter() {
        var model = model(ModelType.IMAGE_GENERATION);
        var providerType = mock(AiProviderType.class);
        when(providerType.recommendAdapterType(ModelType.IMAGE_GENERATION))
            .thenReturn(java.util.Optional.of(AdapterType.OPENAI_IMAGE));

        var capabilities = service.effectiveCapabilities(model, providerType);

        assertThat(capabilities.getImageGeneration().getTextToImage()).isTrue();
    }

    @Test
    void effectiveCapabilities_inheritsProviderReasoningHistoryDefault() {
        var model = model(ModelType.LANGUAGE);
        var providerType = mock(AiProviderType.class);
        when(providerType.languageModelProviderOptions()).thenReturn(
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .build());

        var capabilities = service.effectiveCapabilities(model, providerType);

        assertThat(capabilities.getLanguage().getReasoningHistory()).isTrue();
    }

    @Test
    void effectiveCapabilities_modelReasoningHistoryOverridesProviderDefault() {
        var model = model(ModelType.LANGUAGE);
        model.getSpec().setCapabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .reasoningHistory(false)
                .build())
            .build());
        var providerType = mock(AiProviderType.class);
        when(providerType.languageModelProviderOptions()).thenReturn(
            LanguageModelProviderOptions.builder()
                .reasoningHistorySupported(true)
                .build());

        var capabilities = service.effectiveCapabilities(model, providerType);

        assertThat(capabilities.getLanguage().getReasoningHistory()).isFalse();
    }

    @Test
    void effectiveCapabilities_inheritsUnsupportedProviderReasoningHistoryDefault() {
        var model = model(ModelType.LANGUAGE);
        var providerType = mock(AiProviderType.class);
        when(providerType.languageModelProviderOptions())
            .thenReturn(LanguageModelProviderOptions.defaults());

        var capabilities = service.effectiveCapabilities(model, providerType);

        assertThat(capabilities.getLanguage().getReasoningHistory()).isFalse();
    }

    @Test
    void matcher_treatsUnknownAsUnsupportedAndUsesMediaCoverage() {
        var unknown = ModelCapabilities.empty();
        var imageWildcard = ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .imageInput(true)
                .inputMediaTypes(List.of("image/*"))
                .inputSources(List.of(InputSource.DATA))
                .build())
            .build();
        var requiredPng = ModelCapabilityRequirement.languageImageInput("image/png",
            InputSource.DATA);
        var requiredWildcard = ModelCapabilityRequirement.languageImageInput("image/*",
            InputSource.DATA);
        var pngOnly = ModelCapabilities.builder()
            .language(LanguageCapability.builder()
                .imageInput(true)
                .inputMediaTypes(List.of("image/png"))
                .inputSources(List.of(InputSource.DATA))
                .build())
            .build();

        assertThat(matcher.match(unknown, requiredPng).matched()).isFalse();
        assertThat(matcher.match(imageWildcard, requiredPng).matched()).isTrue();
        assertThat(matcher.match(pngOnly, requiredWildcard).matched()).isFalse();
    }

    @Test
    void matcher_requiresAllImageGenerationConditions() {
        var capabilities = ModelCapabilities.builder()
            .imageGeneration(ImageGenerationCapability.builder()
                .textToImage(true)
                .maxImagesPerCall(2)
                .outputMediaTypes(List.of("image/png"))
                .build())
            .build();
        var requirement = ModelCapabilityRequirement.builder()
            .imageGeneration(ImageGenerationCapability.builder()
                .textToImage(true)
                .maxImagesPerCall(3)
                .outputMediaTypes(List.of("image/png"))
                .build())
            .build();

        var result = matcher.match(capabilities, requirement);

        assertThat(result.matched()).isFalse();
        assertThat(result.issues())
            .extracting(CapabilityMatchIssue::path)
            .containsExactly("imageGeneration.maxImagesPerCall");
    }

    private AiModel model(ModelType type) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName("model");
        model.setMetadata(metadata);
        var spec = new AiModel.AiModelSpec();
        spec.setProviderName("provider");
        spec.setModelId("provider-model");
        spec.setDisplayName("Provider Model");
        spec.setModelType(type);
        spec.setFeatures(List.of());
        model.setSpec(spec);
        return model;
    }
}
