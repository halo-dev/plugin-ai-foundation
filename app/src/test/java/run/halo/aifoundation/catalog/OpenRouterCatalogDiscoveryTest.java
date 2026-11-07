package run.halo.aifoundation.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.catalog.ModelCatalogTestFixtures.provider;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.capability.CapabilitySource;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.extension.AiProvider;
import run.halo.aifoundation.provider.openrouter.OpenRouterProvider;
import run.halo.aifoundation.provider.support.ModelType;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.app.extension.Metadata;

class OpenRouterCatalogDiscoveryTest {

    static Stream<Arguments> outputFormats() {
        return Stream.of(
                Arguments.of("", null, List.of("image/png"), true),
                Arguments.of(",\"output_format\":null", null, List.of("image/png"), true),
                Arguments.of(",\"output_format\":{\"type\":\"enum\"}", null, List.of("image/png"), true),
                Arguments.of(",\"output_format\":{\"values\":[]}", List.of(), List.of(), false),
                Arguments.of(
                        ",\"output_format\":{\"values\":[\"jpeg\"]}",
                        List.of("image/jpeg"),
                        List.of("image/jpeg"),
                        false),
                Arguments.of(",\"output_format\":{\"values\":[\"avif\"]}", List.of(), List.of(), false));
    }

    @ParameterizedTest
    @MethodSource("outputFormats")
    void actualDiscoverySupplementsOnlyUndeclaredImageFormats(
            String outputFormat, List<String> discoveredFormats, List<String> effectiveFormats, boolean supplemented) {
        var response = """
            {"data":[{"id":"black-forest-labs/flux-3-image","name":"Flux",
              "architecture":{"input_modalities":["text"],"output_modalities":["image"]},
              "supported_parameters":{"n":{"type":"range","min":1,"max":10}%s}}]}
            """.formatted(outputFormat);
        var type = new OpenRouterProvider() {
            @Override
            protected WebClient.Builder discoveryWebClientBuilder(AiProvider provider) {
                return WebClient.builder().exchangeFunction(request -> {
                    assertThat(request.url().getHost()).isEqualTo("openrouter.ai");
                    var body = request.url().getPath().equals("/api/v1/images/models") ? response : "{\"data\":[]}";
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(body)
                            .build());
                });
            }
        };
        var provider = provider(null);
        provider.getSpec().setProviderType("openrouter");
        var metadata = new Metadata();
        metadata.setName("openrouter");
        provider.setMetadata(metadata);
        var discovered = type.discoverModels(provider, "test-key").block();
        assertThat(discovered).hasSize(1);
        var original = discovered.getFirst();
        assertThat(original.modelType()).isEqualTo(ModelType.IMAGE_GENERATION);
        assertThat(original.capabilities().getImageGeneration().getOutputMediaTypes())
                .isEqualTo(discoveredFormats);

        var service = mock(ModelsDevCatalogService.class);
        when(service.snapshot()).thenReturn(ModelsDevBundledCatalog.load());
        var result = new ModelCatalogEnricher(service)
                .enrich(provider, type, discovered)
                .getFirst();
        var image = result.capabilities().getImageGeneration();
        assertThat(image.getOutputMediaTypes()).isEqualTo(effectiveFormats);
        assertThat(image.getTextToImage()).isTrue();
        assertThat(image.getImageToImage()).isFalse();
        assertThat(image.getMaskInput()).isFalse();
        assertThat(image.getMaxImagesPerCall()).isEqualTo(10);
        assertThat(result.capabilitySources().getImageGeneration()).isEqualTo(CapabilitySource.REMOTE);
        assertThat(result.catalogMatch().fields().contains("imageGeneration.outputMediaTypes"))
                .isEqualTo(supplemented);
        var requirement = ModelCapabilityRequirement.builder()
                .imageGeneration(ImageGenerationCapability.builder()
                        .outputMediaTypes(List.of("image/png"))
                        .build())
                .build();
        assertThat(new ModelCapabilityMatcher()
                        .match(result.capabilities(), requirement)
                        .matched())
                .isEqualTo(effectiveFormats.contains("image/png"));
        assertThat(original.capabilities().getImageGeneration().getOutputMediaTypes())
                .isEqualTo(discoveredFormats);
    }
}
