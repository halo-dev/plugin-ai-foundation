package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.CATALOG;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.CLOCK;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.NON_CHAT_CATALOG;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.NOW;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.capability.ModelCapabilitySources;
import run.halo.aifoundation.extension.AiModel;
import run.halo.aifoundation.provider.deepseek.DeepSeekProvider;
import run.halo.aifoundation.provider.openai.OpenAiProvider;
import run.halo.aifoundation.provider.openailike.OpenAiLikeProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.support.AdapterType;
import run.halo.aifoundation.provider.support.DiscoveredModel;
import run.halo.aifoundation.provider.support.DiscoveryConfidence;
import run.halo.aifoundation.provider.support.DiscoverySource;
import run.halo.aifoundation.provider.support.ModelFeature;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.capability.ModelCapabilityService;
import run.halo.app.infra.utils.JsonUtils;

class ModelCatalogEnricherTest {

    @TempDir
    Path directory;

    @Test
    void supplementsIdentifierOnlyResultWithoutAddingUnavailableCatalogModels() {
        var enricher = enricher(CATALOG);
        var input = discovered();
        var results = enricher.enrich(provider(null), new OpenAiProvider(), List.of(input));
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.features()).contains(ModelFeature.TOOL_CALL, ModelFeature.VISION)
            .doesNotContain(ModelFeature.REASONING, ModelFeature.STRUCTURED_OUTPUT,
                ModelFeature.AUDIO_INPUT);
        assertThat(result.capabilities().getLanguage().getInputMediaTypes())
            .containsExactly("image/png", "image/jpeg", "application/pdf");
        assertThat(result.capabilities().getLanguage().getInputSources())
            .containsExactly(InputSource.DATA);
        assertThat(result.capabilities().getLanguage().getReasoningHistory()).isNull();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.UNKNOWN);
        assertThat(result.catalogMatch().providerId()).isEqualTo("openai");
        assertThat(result.catalogMatch().fetchedAt()).isEqualTo(NOW);
        assertThat(result.source()).isEqualTo(DiscoverySource.RULE);
        assertThat(result.confidence()).isEqualTo(DiscoveryConfidence.MEDIUM);
        assertThat(input.capabilities()).isNull();
    }

    @Test
    void neverMatchesCustomGatewaysAliasesPrivateDeploymentsOrDifferentEndpoints() {
        var enricher = enricher(CATALOG);
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
        var chatModel = model.toBuilder().adapterType(AdapterType.OPENAI_CHAT).build();
        assertThat(enricher.enrich(overridden, new OpenAiProvider(), List.of(chatModel)))
            .containsExactly(chatModel);
    }

    @Test
    void preservesExplicitProviderFalseTrueAndEmptyCoverageWhileFillingMissingFields() {
        var enricher = enricher(CATALOG);
        var model = discovered().toBuilder().source(DiscoverySource.REMOTE)
            .features(Set.of(ModelFeature.STREAMING, ModelFeature.STRUCTURED_OUTPUT))
            .featureEvidence(Map.of(ModelFeature.TOOL_CALL, false))
            .capabilities(ModelCapabilities.builder().language(LanguageCapability.builder()
                .imageInput(false).inputMediaTypes(List.of()).inputSources(List.of()).build())
                    .build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.REMOTE)
                .build())
            .build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model))
            .getFirst();
        assertThat(result.features()).contains(ModelFeature.STRUCTURED_OUTPUT)
            .doesNotContain(ModelFeature.TOOL_CALL, ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getInputMediaTypes()).isEmpty();
        assertThat(result.capabilities().getLanguage().getInputSources()).isEmpty();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields())
            .doesNotContain("features.tool-call", "language.imageInput");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-", "_", ""})
    void preservesProviderFeatureAliasesOverCatalogDisagreement(String separator) {
        var type = new OpenAiProvider() {
            DiscoveredModel declaredModel() {
                return modelProfile(Map.of("type", "language", "features", List.of(
                    "tool" + separator + "call", "structured" + separator + "output")),
                    "exact-model");
            }
        };
        var model = type.declaredModel();
        var result = enricher(CATALOG).enrich(provider(null), type, List.of(model)).getFirst();

        assertThat(result.catalogMatch()).isNotNull();
        assertThat(result.features())
            .contains(ModelFeature.TOOL_CALL, ModelFeature.STRUCTURED_OUTPUT);
        assertThat(result.featureEvidence())
            .containsEntry(ModelFeature.TOOL_CALL, true)
            .containsEntry(ModelFeature.STRUCTURED_OUTPUT, true);
    }

    @Test
    void manualSnapshotsAndNonLanguageModelsAreUntouched() {
        var enricher = enricher(CATALOG);
        var manual = discovered().toBuilder().capabilities(ModelCapabilities.language(
            LanguageCapability.builder().imageInput(false).build())).build();
        var image = discovered().toBuilder().modelType(ModelType.IMAGE_GENERATION).build();
        var models = List.of(manual, image);
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), models))
            .containsExactlyElementsOf(models);
    }

    @Test
    void catalogOverridesAdapterDefaultsButCannotEnableUnsupportedFeaturesOrFiles() {
        var enricher = enricher(CATALOG
            .replace("\"reasoning\":{\"supported\":false}", "\"reasoning\":{\"supported\":true}"));
        var type = new OpenAiProvider() {
            @Override
            public List<ModelFeature> getSupportedFeatures(AdapterType adapter) {
                return List.of(ModelFeature.STREAMING);
            }
        };
        var model = discovered().toBuilder().adapterType(AdapterType.OPENAI_CHAT)
            .features(Set.of(ModelFeature.STREAMING))
            .capabilities(ModelCapabilities.builder()
                .language(LanguageCapability.builder().imageInput(true).build()).build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.BUILT_IN)
                .build())
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
        var enricher = enricher(CATALOG);
        var model = discovered().toBuilder()
            .featureEvidence(Map.of(ModelFeature.VISION, false)).build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model))
            .getFirst();
        assertThat(result.features()).doesNotContain(ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isFalse();
        assertThat(result.capabilities().getLanguage().getInputMediaTypes())
            .doesNotContain("image/png", "image/jpeg");
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields()).doesNotContain("language.imageInput");
    }

    @Test
    void positiveProviderImageEvidenceWinsOverCatalogTextOnlyInput() {
        var enricher = enricher(CATALOG
            .replace("[\"text\",\"image/png\",\"image/jpeg\",\"application/pdf\"]", "[\"text\"]"));
        var model = discovered().toBuilder().source(DiscoverySource.REMOTE)
            .features(Set.of(ModelFeature.VISION)).build();
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model))
            .getFirst();
        assertThat(result.features()).contains(ModelFeature.VISION);
        assertThat(result.capabilities().getLanguage().getImageInput()).isTrue();
        assertThat(result.capabilitySources().getLanguage()).isEqualTo(CapabilitySource.REMOTE);
    }

    @Test
    void catalogWithoutReplayEvidencePreservesProviderDefaultAndExplicitFalse() {
        var enricher = enricher("""
            {"deepseek":{"id":"deepseek","models":{"deepseek-chat":{
              "id":"deepseek-chat","type":"chat","capabilities":{"reasoning":{"supported":true}},
              "api":{"chat-completions":{"base_url":"https://api.deepseek.com"}},
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
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.REMOTE)
                .build())
            .build();
        var preserved = enricher.enrich(provider, type, List.of(explicit)).getFirst();
        assertThat(preserved.capabilities().getLanguage().getReasoningHistory()).isFalse();
    }

    @Test
    void attachesParametersEvenWhenAllCapabilitiesAlreadyHaveProviderEvidence() {
        var model = discovered().toBuilder().source(DiscoverySource.REMOTE)
            .featureEvidence(Map.of(ModelFeature.TOOL_CALL, true, ModelFeature.REASONING, false,
                ModelFeature.STRUCTURED_OUTPUT, false, ModelFeature.VISION, false,
                ModelFeature.AUDIO_INPUT, false))
            .capabilities(ModelCapabilities.builder().language(LanguageCapability.builder()
                .imageInput(false).fileInput(false).inputMediaTypes(List.of())
                .inputSources(List.of()).build()).build())
            .capabilitySources(ModelCapabilitySources.builder().language(CapabilitySource.REMOTE)
                .build())
            .build();
        var result = enricher(CATALOG)
            .enrich(provider(null), new OpenAiProvider(), List.of(model)).getFirst();
        assertThat(result.catalogParameters()).isNotNull();
        assertThat(result.catalogMatch().fields()).containsExactly("parameters");
        assertThat(result.capabilities()).isEqualTo(model.capabilities());
    }

    @Test
    void concreteMimeTypesArePreservedAndIntersectedWithAdapterRepresentations() {
        var body = CATALOG.replace(
            "[\"text\",\"image/png\",\"image/jpeg\",\"application/pdf\"]",
            "[\"text\",\"image/webp\",\"audio/mp3\",\"audio/flac\",\"application/pdf\",\"video/mp4\"]");
        var type = new OpenAiProvider() {
            @Override
            public List<ModelFeature> getSupportedFeatures(AdapterType adapter) {
                return List.of(ModelFeature.VISION, ModelFeature.AUDIO_INPUT);
            }
        };
        var enricher = enricher(body);
        var chat = enricher.enrich(provider(null), type,
            List.of(discovered().toBuilder().adapterType(AdapterType.OPENAI_CHAT).build()))
                .getFirst();
        assertThat(chat.capabilities().getLanguage().getInputMediaTypes())
            .containsExactly("image/webp", "audio/mpeg");
        var responses = enricher.enrich(provider(null), type, List.of(discovered())).getFirst();
        assertThat(responses.capabilities().getLanguage().getInputMediaTypes())
            .containsExactly("image/webp", "application/pdf");
    }

    @Test
    void matchingAnotherProtocolDoesNotAuthorizeCatalogParametersForSelectedAdapter() {
        var body = CATALOG.replace(
            "\"responses\":{\"base_url\":\"https://api.openai.com/v1\"",
            "\"responses\":{\"base_url\":\"https://private.example/v1\"");
        var enricher = enricher(body);
        var model = discovered();
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), List.of(model)))
            .containsExactly(model);
        var chat = model.toBuilder().adapterType(AdapterType.OPENAI_CHAT).build();
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), List.of(chat))
            .getFirst().catalogParameters()).isNotNull();
    }

    @Test
    void exactCatalogTypesCorrectIdentifierOnlyLanguageGuessesWithoutAddingModels() {
        var embedding = discovered().toBuilder().modelId("embedding-model").build();
        var image = discovered().toBuilder().modelId("image-model").build();
        var results = enricher(NON_CHAT_CATALOG)
            .enrich(provider(null), new OpenAiProvider(), List.of(embedding, image));
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().modelType()).isEqualTo(ModelType.EMBEDDING);
        assertThat(results.getFirst().adapterType()).isEqualTo(AdapterType.OPENAI_EMBEDDING);
        assertThat(results.getFirst().features()).isEmpty();
        assertThat(results.getFirst().catalogMatch().fields())
            .containsExactly("modelType", "adapterType", "parameters");
        assertThat(results.getFirst().catalogParameters().getConstraints().getContextTokens())
            .isEqualTo(8191);
        assertThat(results.getFirst().catalogParameters().getConstraints().getOutputTokens())
            .isNull();
        assertThat(results.getFirst().catalogParameters().getMappings().getEmbedding()).isNull();
        assertThat(results.getLast().modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(results.getLast().adapterType()).isEqualTo(AdapterType.OPENAI_IMAGE);
        assertThat(results.getLast().capabilities().getImageGeneration().getOutputMediaTypes())
            .containsExactly("image/png", "image/jpeg", "image/webp");
        assertThat(results.getLast().capabilities().getImageGeneration().getImageToImage())
            .isNull();
        assertThat(results.getLast().capabilities().getImageGeneration().getMaskInput()).isNull();
        assertThat(results.getLast().capabilities().getImageGeneration().getMaxImagesPerCall())
            .isNull();
    }

    @Test
    void catalogCannotReclassifyExplicitTypesCapabilitiesOrFeatureEvidence() {
        var guessed = discovered().toBuilder().modelId("embedding-model").build();
        var explicitRemote = guessed.toBuilder().source(DiscoverySource.REMOTE).build();
        var selected = guessed.toBuilder().confidence(DiscoveryConfidence.HIGH).build();
        var capabilities = guessed.toBuilder().capabilities(ModelCapabilities.builder()
            .language(LanguageCapability.builder().imageInput(false).build()).build()).build();
        var evidence = guessed.toBuilder().featureEvidence(Map.of(ModelFeature.VISION, false))
            .build();
        var models = List.of(explicitRemote, selected, capabilities, evidence);
        assertThat(enricher(NON_CHAT_CATALOG).enrich(provider(null), new OpenAiProvider(), models))
            .containsExactlyElementsOf(models);
    }

    @Test
    void imageOutputsOnlyNarrowKnownBuiltInFormatsWithoutInferringEditingOrImageCount() {
        var image = ImageGenerationCapability.builder().textToImage(true).imageToImage(false)
            .maskInput(false).maxImagesPerCall(2).sizes(List.of("1024x1024"))
            .outputMediaTypes(List.of("image/png", "image/jpeg", "image/svg+xml")).build();
        var model = DiscoveredModel.builder().modelId("image-model")
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.OPENAI_IMAGE)
            .capabilities(ModelCapabilities.builder().imageGeneration(image).build())
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.BUILT_IN).build()).build();
        var result = enricher(NON_CHAT_CATALOG).enrich(provider(null), new OpenAiProvider(),
            List.of(model))
            .getFirst();
        var capabilities = result.capabilities().getImageGeneration();
        assertThat(capabilities.getOutputMediaTypes()).containsExactly("image/png", "image/jpeg");
        assertThat(capabilities.getImageToImage()).isFalse();
        assertThat(capabilities.getMaskInput()).isFalse();
        assertThat(capabilities.getMaxImagesPerCall()).isEqualTo(2);
        assertThat(capabilities.getSizes()).containsExactly("1024x1024");
        assertThat(result.catalogMatch().fields())
            .containsExactly("imageGeneration.outputMediaTypes", "parameters");
        assertThat(image.getOutputMediaTypes())
            .containsExactly("image/png", "image/jpeg", "image/svg+xml");
    }

    @Test
    void explicitImageOutputCoverageAndManualCapabilitiesRemainAuthoritative() {
        var capabilities = ModelCapabilities.builder()
            .imageGeneration(ImageGenerationCapability.builder()
                .outputMediaTypes(List.of()).imageToImage(false).build())
            .build();
        var model = DiscoveredModel.builder().modelId("image-model")
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.OPENAI_IMAGE).source(DiscoverySource.REMOTE)
            .capabilities(capabilities)
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE).build()).build();
        var enricher = enricher(NON_CHAT_CATALOG);
        var result = enricher.enrich(provider(null), new OpenAiProvider(), List.of(model))
            .getFirst();
        assertThat(result.capabilities()).isSameAs(capabilities);
        assertThat(result.catalogMatch().fields()).containsExactly("parameters");
        var manual = model.toBuilder().capabilitySources(ModelCapabilitySources.builder()
            .imageGeneration(CapabilitySource.MANUAL).build()).build();
        assertThat(enricher.enrich(provider(null), new OpenAiProvider(), List.of(manual)))
            .containsExactly(manual);
    }

    @Test
    void missingRemoteImageFormatsUseOnlyReviewedAdapterOutputsAndKeepOtherProviderFields() {
        var capabilities = ModelCapabilities.builder()
            .imageGeneration(ImageGenerationCapability.builder()
                .imageToImage(false).maskInput(false).maxImagesPerCall(3).build())
            .build();
        var model = DiscoveredModel.builder().modelId("image-model")
            .modelType(ModelType.IMAGE_GENERATION)
            .adapterType(AdapterType.OPENAI_IMAGE).source(DiscoverySource.REMOTE)
            .capabilities(capabilities)
            .capabilitySources(ModelCapabilitySources.builder()
                .imageGeneration(CapabilitySource.REMOTE).build()).build();
        var catalog = NON_CHAT_CATALOG.replace("\"image/webp\"]", "\"image/webp\",\"image/avif\"]");
        var result = enricher(catalog).enrich(provider(null), new OpenAiProvider(), List.of(model))
            .getFirst();
        assertThat(result.capabilities().getImageGeneration().getOutputMediaTypes())
            .containsExactly("image/png", "image/jpeg", "image/webp");
        assertThat(result.capabilities().getImageGeneration().getImageToImage()).isFalse();
        assertThat(result.capabilities().getImageGeneration().getMaskInput()).isFalse();
        assertThat(result.capabilities().getImageGeneration().getMaxImagesPerCall()).isEqualTo(3);
        assertThat(result.capabilitySources().getImageGeneration())
            .isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields())
            .containsExactly("imageGeneration.outputMediaTypes", "parameters");
        assertThat(capabilities.getImageGeneration().getOutputMediaTypes()).isNull();
    }

    @Test
    void clearingSavedCatalogImageCapabilitiesRestoresDefaultsButPreservesExplicitRestrictions() {
        var service = mock(ModelsDevCatalogService.class);
        when(service.snapshot()).thenReturn(ModelsDevBundledCatalog.load());
        var type = new OpenAiProvider();
        var imported = new ModelCatalogEnricher(service).enrich(provider(null), type,
            List.of(discovered().toBuilder().modelId("gpt-image-1-mini").build())).getFirst();
        assertThat(imported.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(imported.capabilities().getImageGeneration().getOutputMediaTypes())
            .contains("image/png");
        assertThat(imported.capabilities().getImageGeneration().getTextToImage()).isNull();

        var saved = new AiModel();
        var spec = new AiModel.AiModelSpec();
        spec.setModelId(imported.modelId());
        spec.setModelType(imported.modelType());
        spec.setAdapterType(imported.adapterType());
        spec.setCapabilities(imported.capabilities());
        spec.setCapabilitySources(imported.capabilitySources());
        spec.setCatalogMatch(imported.catalogMatch());
        spec.setCatalogParameters(imported.catalogParameters());
        saved.setSpec(spec);
        var capabilities = new ModelCapabilityService();
        var matcher = new ModelCapabilityMatcher();
        var textToImage = ModelCapabilityRequirement.builder().imageGeneration(
            ImageGenerationCapability.builder().textToImage(true).build()).build();
        assertThat(matcher.match(capabilities.effectiveCapabilities(saved, type), textToImage)
            .matched()).isTrue();

        // Clearing the final field must persist an absent domain and source, not an empty
        // manual override. The remaining catalog profile must not revive cleared formats.
        spec.setCapabilities(null);
        spec.setCapabilitySources(null);
        var restored = JsonUtils.jsonToObject(JsonUtils.objectToJson(saved), AiModel.class);
        var effective = capabilities.effectiveCapabilities(restored, type);
        assertThat(effective.getImageGeneration().getTextToImage()).isTrue();
        assertThat(effective.getImageGeneration().getMaxImagesPerCall()).isEqualTo(1);
        assertThat(effective.getImageGeneration().getOutputMediaTypes()).isNull();
        assertThat(matcher.match(effective, textToImage).matched()).isTrue();

        // Explicit false and an explicit empty format range are still real overrides.
        spec.setCapabilities(ModelCapabilities.builder().imageGeneration(
            ImageGenerationCapability.builder().textToImage(false)
                .outputMediaTypes(List.of()).build()).build());
        spec.setCapabilitySources(ModelCapabilitySources.builder()
            .imageGeneration(CapabilitySource.MANUAL).build());
        restored = JsonUtils.jsonToObject(JsonUtils.objectToJson(saved), AiModel.class);
        effective = capabilities.effectiveCapabilities(restored, type);
        assertThat(effective.getImageGeneration().getTextToImage()).isFalse();
        assertThat(effective.getImageGeneration().getOutputMediaTypes()).isEmpty();
        assertThat(matcher.match(effective, textToImage).matched()).isFalse();
        var png = ModelCapabilityRequirement.builder().imageGeneration(
            ImageGenerationCapability.builder().outputMediaTypes(List.of("image/png")).build())
                .build();
        assertThat(matcher.match(effective, png).matched()).isFalse();
        assertThat(imported.capabilities().getImageGeneration().getOutputMediaTypes())
            .contains("image/png");
    }

    @Test
    void rerankDiscoveryReadsInputLimitsWithoutCreatingLanguageOrTopNOverrides() {
        var provider = provider(null);
        provider.getSpec().setProviderType("openrouter");
        var model = DiscoveredModel.builder().modelId("rerank-model").modelType(ModelType.RERANK)
            .adapterType(AdapterType.RERANK).source(DiscoverySource.REMOTE).build();
        var result = enricher(NON_CHAT_CATALOG)
            .enrich(provider, new OpenRouterProvider(), List.of(model)).getFirst();
        assertThat(result.catalogParameters()).isNotNull();
        assertThat(result.catalogParameters().getConstraints().getInputTokens()).isEqualTo(4000);
        assertThat(result.catalogParameters().getConstraints().getOutputTokens()).isNull();
        assertThat(result.catalogParameters().getMappings().getLanguage()).isNull();
        assertThat(result.catalogParameters().getMappings().getRerank()).isNull();
        assertThat(result.capabilities()).isNull();
    }

    private ModelCatalogEnricher enricher(String body) {
        var service = new ModelsDevCatalogService(directory.resolve("catalog.json"),
            Mono.just(body),
            CLOCK);
        assertThat(service.refresh().block().available()).isTrue();
        return new ModelCatalogEnricher(service);
    }

    private DiscoveredModel discovered() {
        return DiscoveredModel.builder().modelId("exact-model").modelType(ModelType.LANGUAGE)
            .adapterType(AdapterType.OPENAI_RESPONSES)
            .features(Set.of(ModelFeature.STREAMING, ModelFeature.REASONING,
                ModelFeature.STRUCTURED_OUTPUT))
            .source(DiscoverySource.RULE).build();
    }

}